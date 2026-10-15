package com.b2binventory.service;

import com.b2binventory.dto.AuthDtos.AuthResponse;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.security.CurrentUser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Whole-business backup and restore.
 *
 * <p>A backup is a JSON document holding every row the business owns, table by table, exactly as stored
 * (so the stock ledger, sales, purchases and payments stay consistent). People, sign-in data, notifications
 * and the audit log are not included.
 *
 * <p>Restore never touches existing data: it creates a <em>new</em> business owned by the caller, gives every
 * row a new id, rewrites foreign keys through old-to-new id maps and attributes all recorded actions to the
 * caller's new membership. The schema is read from the PostgreSQL catalog, so new tables and columns added
 * by later migrations are picked up without changes here.
 */
@Service
public class BackupService {

    public static final int FORMAT_VERSION = 1;

    /** Tables that belong to people or sessions, or that are rebuilt rather than copied. */
    private static final Set<String> EXCLUDED = Set.of(
            "businesses", "users", "memberships", "membership_permissions", "refresh_tokens", "invitations",
            "notifications", "notification_reads", "audit_events", "flyway_schema_history");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuthService authService;
    private final AuditService auditService;

    public BackupService(JdbcTemplate jdbc, ObjectMapper json, AuthService authService, AuditService auditService) {
        this.jdbc = jdbc;
        // Exact decimals: money and quantities must not pass through double.
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        this.authService = authService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ export

    @Transactional
    public ObjectNode export() {
        CurrentUser me = CurrentUser.get();
        long businessId = me.businessId();
        Schema schema = readSchema();

        ObjectNode root = json.createObjectNode();
        root.put("format", "b2b-inventory-backup");
        root.put("formatVersion", FORMAT_VERSION);
        root.put("schemaVersion", schemaVersion());
        root.put("exportedAt", Instant.now().toString());
        root.set("business", readRows("SELECT row_to_json(t) FROM businesses t WHERE id = ?", businessId).get(0));

        ObjectNode tables = root.putObject("tables");
        for (String table : schema.order) {
            tables.set(table, readRows(scopeQuery(schema, table), businessId));
        }
        auditService.record(businessId, me.membershipId(), "BACKUP_EXPORTED", "Business", businessId, Map.of());
        return root;
    }

    /** Rows owned by the business: directly through business_id, or through a parent table that has it. */
    private String scopeQuery(Schema schema, String table) {
        String order = schema.hasId.contains(table) ? " ORDER BY t.id" : "";
        if (schema.hasBusinessId.contains(table)) {
            return "SELECT row_to_json(t) FROM " + table + " t WHERE t.business_id = ?" + order;
        }
        for (ForeignKey fk : schema.foreignKeys.getOrDefault(table, List.of())) {
            if (schema.hasBusinessId.contains(fk.refTable)) {
                return "SELECT row_to_json(t) FROM " + table + " t JOIN " + fk.refTable + " p ON p.id = t." + fk.column
                        + " WHERE p.business_id = ?" + order;
            }
        }
        throw new IllegalStateException("Cannot tell which business owns rows of " + table);
    }

    private ArrayNode readRows(String sql, long businessId) {
        ArrayNode rows = json.createArrayNode();
        jdbc.query(sql, rs -> {
            try {
                rows.add(json.readTree(rs.getString(1)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, businessId);
        return rows;
    }

    // ------------------------------------------------------------------ restore

    /** Restores a backup into a new business owned by the caller and signs them into it. */
    @Transactional
    public AuthResponse restore(java.io.InputStream input) {
        JsonNode backup;
        try {
            backup = json.readTree(input);
        } catch (java.io.IOException e) {
            throw new BadRequestException("This file is not an inventory backup");
        }
        CurrentUser me = CurrentUser.get();
        validate(backup);
        Schema schema = readSchema();

        // New business, copied from the backup's profile.
        ObjectNode business = ((ObjectNode) backup.get("business")).deepCopy();
        long businessId = nextId("businesses");
        business.put("id", businessId);
        business.put("version", 0);
        business.remove(List.of("created_at", "updated_at"));
        String name = business.path("name").asText("Restored business");
        business.put("name", truncate(name + " (restored)", 200));
        insert("businesses", business, schema.columns.get("businesses"));

        long membershipId = jdbc.queryForObject(
                "INSERT INTO memberships (business_id, user_id, role) VALUES (?, ?, 'ADMIN') RETURNING id",
                Long.class, businessId, me.userId());

        Map<String, Map<Long, Long>> idMaps = new HashMap<>();
        JsonNode tables = backup.get("tables");
        int rowCount = 0;
        for (String table : schema.order) {
            JsonNode rows = tables.get(table);
            if (rows == null || !rows.isArray() || rows.isEmpty()) continue;
            Map<Long, Long> ids = idMaps.computeIfAbsent(table, t -> new HashMap<>());
            List<ForeignKey> fks = schema.foreignKeys.getOrDefault(table, List.of());
            Set<String> columns = schema.columns.get(table);

            // Self-referencing rows (sub-categories) are inserted once their parent exists.
            List<ObjectNode> pending = new ArrayList<>();
            rows.forEach(r -> pending.add(((ObjectNode) r).deepCopy()));
            while (!pending.isEmpty()) {
                int before = pending.size();
                for (Iterator<ObjectNode> it = pending.iterator(); it.hasNext(); ) {
                    ObjectNode row = it.next();
                    if (!selfReferencesReady(row, table, fks, ids)) continue;
                    for (ForeignKey fk : fks) {
                        remap(row, fk, businessId, membershipId, idMaps);
                    }
                    if (row.has("business_id")) row.put("business_id", businessId);
                    if (schema.hasId.contains(table)) {
                        long newId = nextId(table);
                        ids.put(row.get("id").asLong(), newId);
                        row.put("id", newId);
                    }
                    insert(table, row, columns);
                    rowCount++;
                    it.remove();
                }
                if (pending.size() == before) {
                    throw new BadRequestException("The backup is damaged: rows in " + table + " refer to missing rows");
                }
            }
        }

        auditService.record(businessId, membershipId, "BACKUP_RESTORED", "Business", businessId,
                Map.of("sourceBusiness", name, "rows", rowCount,
                        "exportedAt", backup.path("exportedAt").asText("")));
        return authService.signInAs(membershipId);
    }

    private void validate(JsonNode backup) {
        if (backup == null || !"b2b-inventory-backup".equals(backup.path("format").asText())) {
            throw new BadRequestException("This file is not an inventory backup");
        }
        int format = backup.path("formatVersion").asInt(-1);
        if (format != FORMAT_VERSION) {
            throw new BadRequestException("This backup format (version " + format + ") is not supported");
        }
        if (compareVersions(backup.path("schemaVersion").asText("0"), schemaVersion()) > 0) {
            throw new BadRequestException("This backup was made by a newer version of the server. Update the server first.");
        }
        if (!backup.path("business").isObject() || !backup.path("tables").isObject()) {
            throw new BadRequestException("The backup is incomplete");
        }
    }

    private boolean selfReferencesReady(ObjectNode row, String table, List<ForeignKey> fks, Map<Long, Long> ids) {
        for (ForeignKey fk : fks) {
            if (!fk.refTable.equals(table)) continue;
            JsonNode ref = row.get(fk.column);
            if (ref != null && !ref.isNull() && !ids.containsKey(ref.asLong())) return false;
        }
        return true;
    }

    private void remap(ObjectNode row, ForeignKey fk, long businessId, long membershipId,
                       Map<String, Map<Long, Long>> idMaps) {
        JsonNode ref = row.get(fk.column);
        if (ref == null || ref.isNull()) return;
        switch (fk.refTable) {
            case "businesses" -> row.put(fk.column, businessId);
            // Every recorded action is attributed to the person restoring; the original people are not copied.
            case "memberships" -> row.put(fk.column, membershipId);
            default -> {
                Long mapped = idMaps.getOrDefault(fk.refTable, Map.of()).get(ref.asLong());
                if (mapped == null) {
                    throw new BadRequestException("The backup is damaged: " + fk.table + "." + fk.column
                            + " refers to a missing " + fk.refTable + " row");
                }
                row.put(fk.column, mapped);
            }
        }
    }

    /** Inserts using PostgreSQL's own JSON-to-row conversion, so every column type (jsonb, uuid, numeric…) round-trips. */
    private void insert(String table, ObjectNode row, Set<String> tableColumns) {
        List<String> cols = new ArrayList<>();
        row.fieldNames().forEachRemaining(c -> {
            if (tableColumns.contains(c)) cols.add(c);
        });
        String list = String.join(", ", cols);
        try {
            jdbc.update("INSERT INTO " + table + " (" + list + ") SELECT " + list
                    + " FROM json_populate_record(null::" + table + ", ?::json)", json.writeValueAsString(row));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private long nextId(String table) {
        return jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence(?, 'id'))", Long.class, table);
    }

    // ------------------------------------------------------------------ schema

    private record ForeignKey(String table, String column, String refTable) {
    }

    private record Schema(List<String> order, Map<String, Set<String>> columns, Set<String> hasBusinessId,
                          Set<String> hasId, Map<String, List<ForeignKey>> foreignKeys) {
    }

    private Schema readSchema() {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.table_name, c.column_name FROM information_schema.columns c
                JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE c.table_schema = current_schema() AND t.table_type = 'BASE TABLE'
                ORDER BY c.table_name, c.ordinal_position""",
                rs -> {
                    columns.computeIfAbsent(rs.getString(1), t -> new LinkedHashSet<>()).add(rs.getString(2));
                });
        Map<String, List<ForeignKey>> fks = new HashMap<>();
        jdbc.query("""
                SELECT cl.relname, a.attname, rf.relname
                FROM pg_constraint con
                JOIN pg_class cl ON cl.oid = con.conrelid
                JOIN pg_class rf ON rf.oid = con.confrelid
                JOIN pg_namespace n ON n.oid = cl.relnamespace
                JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = con.conkey[1]
                WHERE con.contype = 'f' AND n.nspname = current_schema()""",
                rs -> {
                    ForeignKey fk = new ForeignKey(rs.getString(1), rs.getString(2), rs.getString(3));
                    fks.computeIfAbsent(fk.table, t -> new ArrayList<>()).add(fk);
                });

        Set<String> tables = new TreeSet<>(columns.keySet());
        tables.removeAll(EXCLUDED);
        Set<String> hasBusinessId = new HashSet<>();
        Set<String> hasId = new HashSet<>();
        columns.forEach((t, cols) -> {
            if (cols.contains("business_id")) hasBusinessId.add(t);
            if (cols.contains("id")) hasId.add(t);
        });

        // Parents before children; references to excluded tables are handled by remap().
        List<String> order = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (done.size() < tables.size()) {
            boolean progressed = false;
            for (String t : tables) {
                if (done.contains(t)) continue;
                boolean ready = fks.getOrDefault(t, List.of()).stream()
                        .map(ForeignKey::refTable)
                        .allMatch(r -> r.equals(t) || !tables.contains(r) || done.contains(r));
                if (ready) {
                    order.add(t);
                    done.add(t);
                    progressed = true;
                }
            }
            if (!progressed) {
                throw new IllegalStateException("Circular table references; cannot order backup tables");
            }
        }
        return new Schema(order, columns, hasBusinessId, hasId, fks);
    }

    private String schemaVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return Long.compare(p, q);
        }
        return 0;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
