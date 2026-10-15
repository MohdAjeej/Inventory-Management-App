package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.b2binventory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class StockTest extends IntegrationTest {

    @Test
    void openingStockAndOperationsAreRecordedInHistory() {
        Session admin = registerBusiness("Stock Co");
        Response p = post(admin, "/api/products", product("Bolt", 10));
        long productId = p.get("id").asLong();
        long variant = defaultVariant(p.body());
        assertThat(available(p)).isEqualTo(10.0);

        assertThat(post(admin, "/api/stock/operations", op("ADD", variant, 5)).status()).isEqualTo(200);
        assertThat(post(admin, "/api/stock/operations", op("REDUCE", variant, 3)).status()).isEqualTo(200);
        Response adjust = post(admin, "/api/stock/operations", op("ADJUST", variant, 11));
        assertThat(adjust.status()).isEqualTo(200);
        assertThat(adjust.get("movements").get(0).get("quantity").asDouble()).isEqualTo(-1.0);
        assertThat(adjust.get("movements").get(0).get("quantityBefore").asDouble()).isEqualTo(12.0);

        Response history = get(admin, "/api/stock/movements?productId=" + productId);
        assertThat(types(history.body())).containsExactly("ADJUST", "REDUCE", "ADD", "OPENING");
        assertThat(history.body().get("content").get(3).get("actorName").asText()).startsWith("Admin");

        assertThat(available(get(admin, "/api/products/" + productId))).isEqualTo(11.0);
    }

    @Test
    void negativeStockIsBlockedUnlessEnabled() {
        Session admin = registerBusiness("Neg Co");
        Response p = post(admin, "/api/products", product("Cable", 2));
        long variant = defaultVariant(p.body());

        Response tooMuch = post(admin, "/api/stock/operations", op("REDUCE", variant, 3));
        assertThat(tooMuch.status()).isEqualTo(422);
        assertThat(tooMuch.get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");

        Response adjustWithoutReason = post(admin, "/api/stock/operations",
                Map.of("type", "ADJUST", "variantId", variant, "quantity", 1));
        assertThat(adjustWithoutReason.status()).isEqualTo(400);

        put(admin, "/api/business/settings", Map.of("allowNegativeStock", true, "defaultLowStockThreshold", 5,
                "notifyLowStock", true));
        assertThat(post(admin, "/api/stock/operations", op("REDUCE", variant, 3)).status()).isEqualTo(200);
        assertThat(available(get(admin, "/api/products/" + p.get("id").asLong()))).isEqualTo(-1.0);
    }

    @Test
    void wholeNumberUnitsRejectFractions() {
        Session admin = registerBusiness("Units Co");
        long variant = defaultVariant(post(admin, "/api/products", product("Chair", null)).body());
        Response r = post(admin, "/api/stock/operations", op("ADD", variant, 1.5));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.get("fieldErrors").has("quantity")).isTrue();
    }

    @Test
    void transfersMoveStockBetweenWarehousesAtomically() {
        Session admin = registerBusiness("Multi WH");
        long main = warehouseId(this, admin, "Main Warehouse");
        long branch = post(admin, "/api/warehouses", Map.of("name", "City Branch", "code", "CITY", "branch", true))
                .get("id").asLong();
        Response p = post(admin, "/api/products", product("Lamp", 8));
        long variant = defaultVariant(p.body());

        Response t = post(admin, "/api/stock/transfers", Map.of("variantId", variant, "fromWarehouseId", main,
                "toWarehouseId", branch, "quantity", 5));
        assertThat(t.status()).isEqualTo(200);
        assertThat(t.get("movements")).hasSize(2);
        assertThat(t.get("movements").get(0).get("transferGroupId").asText())
                .isEqualTo(t.get("movements").get(1).get("transferGroupId").asText());

        Response detail = get(admin, "/api/products/" + p.get("id").asLong());
        assertThat(available(detail)).isEqualTo(8.0);
        assertThat(detail.get("balances")).hasSize(2);

        // Over-transfer fails and leaves both balances unchanged.
        Response over = post(admin, "/api/stock/transfers", Map.of("variantId", variant, "fromWarehouseId", main,
                "toWarehouseId", branch, "quantity", 4));
        assertThat(over.status()).isEqualTo(422);
        Response after = get(admin, "/api/variants/" + variant + "/balances");
        double sum = 0;
        for (var b : after.body()) sum += b.get("onHand").asDouble();
        assertThat(sum).isEqualTo(8.0);

        // A warehouse with stock cannot be archived.
        assertThat(post(admin, "/api/warehouses/" + branch + "/archive", null).status()).isEqualTo(409);
    }

    @Test
    void concurrentReductionsNeverOversell() throws Exception {
        Session admin = registerBusiness("Race Co");
        Response p = post(admin, "/api/products", product("Ticket", 10));
        long variant = defaultVariant(p.body());

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Integer>> results = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return post(admin, "/api/stock/operations", op("REDUCE", variant, 1)).status();
            }));
        }
        start.countDown();
        int ok = 0, rejected = 0;
        for (Future<Integer> f : results) {
            int status = f.get(60, TimeUnit.SECONDS);
            if (status == 200) ok++;
            else if (status == 422) rejected++;
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(10);
        assertThat(rejected).isEqualTo(10);
        assertThat(available(get(admin, "/api/products/" + p.get("id").asLong()))).isEqualTo(0.0);
        assertThat(get(admin, "/api/stock/movements?types=REDUCE&variantId=" + variant).get("totalElements").asInt())
                .isEqualTo(10);
    }

    @Test
    void lowStockAndOutOfStockRaiseNotificationsOnce() {
        Session admin = registerBusiness("Alert Co");
        Response p = post(admin, "/api/products", Map.of("name", "Milk", "minStock", 5,
                "openingStock", Map.of("quantity", 10)));
        long variant = defaultVariant(p.body());

        post(admin, "/api/stock/operations", op("REDUCE", variant, 6));
        post(admin, "/api/stock/operations", op("REDUCE", variant, 1));
        post(admin, "/api/stock/operations", op("REDUCE", variant, 3));

        Response notes = get(admin, "/api/notifications");
        List<String> kinds = new ArrayList<>();
        notes.get("content").forEach(n -> kinds.add(n.get("type").asText()));
        assertThat(kinds).containsExactly("OUT_OF_STOCK", "LOW_STOCK");
        assertThat(get(admin, "/api/notifications/unread-count").get("count").asInt()).isEqualTo(2);
        assertThat(post(admin, "/api/notifications/read-all", null).status()).isEqualTo(204);
        assertThat(get(admin, "/api/notifications/unread-count").get("count").asInt()).isZero();

        // Most-stocked lists products with stock, largest first; Milk (now 0) is left out.
        post(admin, "/api/products", Map.of("name", "Bread", "openingStock", Map.of("quantity", 20)));
        post(admin, "/api/products", Map.of("name", "Eggs", "openingStock", Map.of("quantity", 30)));

        Response dash = get(admin, "/api/dashboard");
        assertThat(dash.get("outOfStockCount").asInt()).isEqualTo(1);
        assertThat(dash.get("recentMovements").size()).isGreaterThanOrEqualTo(4);
        JsonNode top = dash.get("mostStocked");
        assertThat(top.size()).isEqualTo(2);
        assertThat(top.get(0).get("productName").asText()).isEqualTo("Eggs");
        for (int i = 1; i < top.size(); i++) {
            assertThat(top.get(i - 1).get("available").asDouble()).isGreaterThanOrEqualTo(top.get(i).get("available").asDouble());
        }
        top.forEach(row -> assertThat(row.get("available").asDouble()).isPositive());
    }

    @Test
    void movementSummaryRanksFastAndSlowMovers() {
        Session admin = registerBusiness("Movers Co");
        long fast = defaultVariant(post(admin, "/api/products", Map.of("name", "Cement",
                "openingStock", Map.of("quantity", 100))).body());
        long some = defaultVariant(post(admin, "/api/products", Map.of("name", "Sand",
                "openingStock", Map.of("quantity", 50))).body());
        post(admin, "/api/products", Map.of("name", "Tiles", "openingStock", Map.of("quantity", 40)));

        assertThat(post(admin, "/api/stock/operations", op("REDUCE", fast, 30)).status()).isEqualTo(200);
        assertThat(post(admin, "/api/stock/operations", op("REDUCE", fast, 20)).status()).isEqualTo(200);
        assertThat(post(admin, "/api/stock/operations", op("REDUCE", some, 5)).status()).isEqualTo(200);
        assertThat(post(admin, "/api/stock/operations", op("ADD", some, 10)).status()).isEqualTo(200);

        Response s = get(admin, "/api/reports/movement-summary");
        assertThat(s.status()).isEqualTo(200);
        assertThat(s.get("totalOut").asDouble()).isEqualTo(55.0);
        assertThat(s.get("totalIn").asDouble()).isEqualTo(200.0); // 190 opening + 10 added
        assertThat(s.get("movementCount").asInt()).isEqualTo(7);
        List<String> reduce = new ArrayList<>();
        s.get("byType").forEach(t -> { if (t.get("type").asText().equals("REDUCE")) reduce.add(t.get("count").asText()); });
        assertThat(reduce).containsExactly("3");
        assertThat(s.get("fastMoving").get(0).get("productName").asText()).isEqualTo("Cement");
        assertThat(s.get("fastMoving").get(0).get("quantityOut").asDouble()).isEqualTo(50.0);
        assertThat(s.get("fastMoving").get(1).get("productName").asText()).isEqualTo("Sand");
        assertThat(s.get("slowMoving").get(0).get("productName").asText()).isEqualTo("Tiles");

        // A period with no movements has nothing fast-moving.
        Response old = get(admin, "/api/reports/movement-summary?from=2020-01-01&to=2020-01-31");
        assertThat(old.get("movementCount").asInt()).isZero();
        assertThat(old.get("fastMoving").size()).isZero();
    }

    @Test
    void damagedAndReservedStockReduceAvailability() {
        Session admin = registerBusiness("Buckets Co");
        put(admin, "/api/business/settings", Map.of("trackDamagedReserved", true, "defaultLowStockThreshold", 0,
                "notifyLowStock", true));
        Response p = post(admin, "/api/products", product("Vase", 10));
        long variant = defaultVariant(p.body());

        assertThat(post(admin, "/api/stock/operations", op("DAMAGE", variant, 2)).status()).isEqualTo(200);
        assertThat(post(admin, "/api/stock/operations", op("RESERVE", variant, 3)).status()).isEqualTo(200);
        Response detail = get(admin, "/api/products/" + p.get("id").asLong());
        assertThat(detail.get("stock").get("onHand").asDouble()).isEqualTo(10.0);
        assertThat(available(detail)).isEqualTo(5.0);

        assertThat(post(admin, "/api/stock/operations", op("REDUCE", variant, 6)).status()).isEqualTo(422);
        Map<String, Object> writeOff = op("REDUCE", variant, 2);
        writeOff.put("fromDamaged", true);
        assertThat(post(admin, "/api/stock/operations", writeOff).status()).isEqualTo(200);
        assertThat(post(admin, "/api/stock/operations", op("RELEASE", variant, 3)).status()).isEqualTo(200);
        assertThat(available(get(admin, "/api/products/" + p.get("id").asLong()))).isEqualTo(8.0);
    }

    @Test
    void batchAndExpiryTracking() {
        Session admin = registerBusiness("Pharma Co");
        put(admin, "/api/business/settings", Map.of("trackBatches", true, "trackExpiry", true,
                "defaultLowStockThreshold", 0, "notifyLowStock", true));
        Response p = post(admin, "/api/products", Map.of("name", "Syrup", "trackBatches", true,
                "openingStock", Map.of("quantity", 5, "lotNumber", "L1", "expiryDate", "2027-01-31")));
        assertThat(p.status()).isEqualTo(201);
        long variant = defaultVariant(p.body());

        Map<String, Object> noLot = op("ADD", variant, 3);
        assertThat(post(admin, "/api/stock/operations", noLot).status()).isEqualTo(400);
        Map<String, Object> lot2 = op("ADD", variant, 3);
        lot2.put("lotNumber", "L2");
        lot2.put("expiryDate", "2027-06-30");
        assertThat(post(admin, "/api/stock/operations", lot2).status()).isEqualTo(200);

        Response batches = get(admin, "/api/variants/" + variant + "/batches");
        assertThat(batches.body()).hasSize(2);
        assertThat(batches.body().get(0).get("lotNumber").asText()).isEqualTo("L1");

        Map<String, Object> reduce = op("REDUCE", variant, 4);
        reduce.put("lotNumber", "L2");
        assertThat(post(admin, "/api/stock/operations", reduce).status()).isEqualTo(422);
    }

    @Test
    void serialNumbersAreTrackedIndividually() {
        Session admin = registerBusiness("Gadgets Co");
        put(admin, "/api/business/settings", Map.of("trackSerials", true, "defaultLowStockThreshold", 0,
                "notifyLowStock", true));
        Response p = post(admin, "/api/products", Map.of("name", "Phone", "trackSerials", true,
                "openingStock", Map.of("quantity", 2, "serials", List.of("SN1", "SN2"))));
        assertThat(p.status()).isEqualTo(201);
        long variant = defaultVariant(p.body());

        Map<String, Object> wrongCount = op("ADD", variant, 2);
        wrongCount.put("serials", List.of("SN3"));
        assertThat(post(admin, "/api/stock/operations", wrongCount).status()).isEqualTo(400);

        Map<String, Object> duplicate = op("ADD", variant, 1);
        duplicate.put("serials", List.of("SN1"));
        assertThat(post(admin, "/api/stock/operations", duplicate).status()).isEqualTo(409);

        Map<String, Object> sell = op("REDUCE", variant, 1);
        sell.put("serials", List.of("SN2"));
        assertThat(post(admin, "/api/stock/operations", sell).status()).isEqualTo(200);
        Response serials = get(admin, "/api/variants/" + variant + "/serials");
        assertThat(serials.body().get(1).get("status").asText()).isEqualTo("OUT");
    }

    @Test
    void stockCountReconcilesDifferences() {
        Session admin = registerBusiness("Count Co");
        long main = warehouseId(this, admin, "Main Warehouse");
        long v1 = defaultVariant(post(admin, "/api/products", product("Apple", 10)).body());
        defaultVariant(post(admin, "/api/products", product("Pear", 4)).body());

        Response count = post(admin, "/api/stock-counts", Map.of("warehouseId", main));
        assertThat(count.status()).isEqualTo(201);
        long countId = count.get("summary").get("id").asLong();
        List<Map<String, Object>> updates = new ArrayList<>();
        count.get("lines").forEach(l -> updates.add(Map.of("lineId", l.get("id").asLong(),
                "countedQty", l.get("variantId").asLong() == v1 ? 7 : 4)));
        assertThat(put(admin, "/api/stock-counts/" + countId + "/lines", Map.of("lines", updates)).status()).isEqualTo(200);

        Response reconciled = post(admin, "/api/stock-counts/" + countId + "/reconcile", null);
        assertThat(reconciled.status()).isEqualTo(200);
        assertThat(reconciled.get("adjustedLines").asInt()).isEqualTo(1);
        assertThat(get(admin, "/api/stock/movements?types=COUNT_ADJUST").get("totalElements").asInt()).isEqualTo(1);
        assertThat(post(admin, "/api/stock-counts/" + countId + "/reconcile", null).status()).isEqualTo(409);
    }

    @Test
    void staffPermissionsOnStock() {
        Session admin = registerBusiness("Staff Stock");
        Session staff = addEmployee(admin, "STAFF");
        long variant = defaultVariant(post(admin, "/api/products", product("Box", 5)).body());

        assertThat(post(staff, "/api/stock/operations", op("ADD", variant, 1)).status()).isEqualTo(200);
        assertThat(post(staff, "/api/stock/operations", op("REDUCE", variant, 1)).status()).isEqualTo(200);
        assertThat(post(staff, "/api/stock/operations", op("ADJUST", variant, 9)).status()).isEqualTo(403);
        assertThat(get(staff, "/api/reports/inventory").status()).isEqualTo(403);
    }
}
