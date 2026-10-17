package com.b2binventory.service;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.ProductDtos.BalanceResponse;
import com.b2binventory.dto.ProductDtos.OpeningStock;
import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.dto.StockDtos.*;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.InsufficientStockException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.*;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import com.b2binventory.service.notification.NotificationService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * All stock changes go through here. Each change locks the affected balance row, validates it,
 * updates the balance and appends an immutable movement plus an audit event, in one transaction.
 */
@Service
public class StockService {

    /** Internal description of one change to one balance. */
    record Change(MovementType type, ProductVariant variant, Warehouse warehouse, BigDecimal quantity,
                  String reason, String notes, Long batchId, String lotNumber, LocalDate expiryDate,
                  List<String> serials, boolean fromDamaged, UUID transferGroupId, Long stockCountId,
                  UUID clientRequestId, boolean system) {

        Change(MovementType type, ProductVariant variant, Warehouse warehouse, BigDecimal quantity,
               String reason, String notes, Long batchId, String lotNumber, LocalDate expiryDate,
               List<String> serials, boolean fromDamaged, UUID transferGroupId, Long stockCountId,
               UUID clientRequestId) {
            this(type, variant, warehouse, quantity, reason, notes, batchId, lotNumber, expiryDate, serials, fromDamaged,
                    transferGroupId, stockCountId, clientRequestId, false);
        }
    }

    private final StockBalanceRepository balances;
    private final StockMovementRepository movements;
    private final ProductVariantRepository variants;
    private final WarehouseRepository warehouses;
    private final BatchRepository batches;
    private final SerialNumberRepository serialNumbers;
    private final MembershipRepository memberships;
    private final BusinessService businessService;
    private final StockLevels stockLevels;
    private final AuditService audit;
    private final NotificationService notifications;

    public StockService(StockBalanceRepository balances, StockMovementRepository movements,
                        ProductVariantRepository variants, WarehouseRepository warehouses, BatchRepository batches,
                        SerialNumberRepository serialNumbers, MembershipRepository memberships,
                        BusinessService businessService, StockLevels stockLevels, AuditService audit,
                        NotificationService notifications) {
        this.balances = balances;
        this.movements = movements;
        this.variants = variants;
        this.warehouses = warehouses;
        this.batches = batches;
        this.serialNumbers = serialNumbers;
        this.memberships = memberships;
        this.businessService = businessService;
        this.stockLevels = stockLevels;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ public operations

    @Transactional
    public OperationResult operate(StockOperationRequest r) {
        Permissions.require(switch (r.type()) {
            case ADD -> Permission.STOCK_ADD;
            case REDUCE -> Permission.STOCK_REDUCE;
            case ADJUST, DAMAGE, RESERVE, RELEASE -> Permission.STOCK_ADJUST;
        });
        ProductVariant variant = variant(r.variantId());
        Optional<OperationResult> replay = replay(r.clientRequestId(), variant);
        if (replay.isPresent()) return replay.get();
        Warehouse warehouse = warehouseOrDefault(r.warehouseId());
        if (r.type() == OperationType.ADJUST && (r.reason() == null || r.reason().isBlank())) {
            throw new BadRequestException("Validation failed", Map.of("reason", "A reason is required for adjustments"));
        }
        MovementType type = MovementType.valueOf(r.type().name());
        StockMovement m = apply(new Change(type, variant, warehouse, r.quantity(), r.reason(), r.notes(),
                r.batchId(), r.lotNumber(), r.expiryDate(), r.serials(), r.fromDamaged(), null, null,
                r.clientRequestId()));
        if (m == null) {
            throw new BadRequestException("Stock is already " + r.quantity().stripTrailingZeros().toPlainString()
                    + "; nothing to adjust");
        }
        return result(List.of(m), variant);
    }

    @Transactional
    public OperationResult transfer(TransferRequest r) {
        Permissions.require(Permission.STOCK_TRANSFER);
        if (r.fromWarehouseId().equals(r.toWarehouseId())) {
            throw new BadRequestException("Choose two different warehouses");
        }
        ProductVariant variant = variant(r.variantId());
        Optional<OperationResult> replay = replay(r.clientRequestId(), variant);
        if (replay.isPresent()) return replay.get();
        Warehouse from = activeWarehouse(r.fromWarehouseId());
        Warehouse to = activeWarehouse(r.toWarehouseId());
        UUID group = UUID.randomUUID();
        String reason = r.reason() == null || r.reason().isBlank() ? "Transfer" : r.reason().trim();

        // Lock both balances in a fixed order to avoid deadlocks between opposite transfers.
        Long batchId = resolveBatchForIssue(variant, r.batchId(), r.lotNumber());
        boolean outFirst = from.getId() < to.getId();
        Change out = new Change(MovementType.TRANSFER_OUT, variant, from, r.quantity(), reason, r.notes(),
                batchId, null, null, r.serials(), false, group, null, r.clientRequestId());
        Change in = new Change(MovementType.TRANSFER_IN, variant, to, r.quantity(), reason, r.notes(),
                batchId, null, null, r.serials(), false, group, null, null);
        if (outFirst) {
            lockBalance(variant, from, batchId);
            lockBalance(variant, to, batchId);
        } else {
            lockBalance(variant, to, batchId);
            lockBalance(variant, from, batchId);
        }
        StockMovement outMovement = apply(out);
        StockMovement inMovement = apply(in);
        return result(List.of(outMovement, inMovement), variant);
    }

    /** Records opening stock for a newly created product (same transaction as the product). */
    @Transactional
    public StockMovement opening(ProductVariant variant, OpeningStock opening) {
        if (opening == null || opening.quantity() == null || opening.quantity().signum() == 0) return null;
        Permissions.require(Permission.STOCK_ADD);
        Warehouse warehouse = warehouseOrDefault(opening.warehouseId());
        return apply(new Change(MovementType.OPENING, variant, warehouse, opening.quantity(), "Opening stock", null,
                null, opening.lotNumber(), opening.expiryDate(), opening.serials(), false, null, null, null));
    }

    /**
     * Stock change caused by a sale, purchase or return document (SALE, SALE_CANCEL, RETURN_IN, PURCHASE,
     * PURCHASE_CANCEL). The calling service checks permissions and runs it inside its own transaction, so
     * the document and its stock movements are saved or rolled back together.
     */
    @Transactional
    public StockMovement documentMovement(MovementType type, ProductVariant variant, Warehouse warehouse,
                                          BigDecimal quantity, Long batchId, String lotNumber, LocalDate expiryDate,
                                          List<String> serials, String reason, String notes) {
        if (!DOCUMENT_TYPES.contains(type)) throw new IllegalArgumentException("Not a document movement: " + type);
        return apply(new Change(type, variant, warehouse, quantity, reason, notes, batchId, lotNumber, expiryDate,
                serials, false, null, null, null));
    }

    private static final Set<MovementType> DOCUMENT_TYPES = EnumSet.of(MovementType.SALE, MovementType.SALE_CANCEL,
            MovementType.RETURN_IN, MovementType.PURCHASE, MovementType.PURCHASE_CANCEL, MovementType.PURCHASE_RETURN);

    /**
     * Holds stock for a trade document (an invoice waiting for the buyer, or a return waiting for the seller),
     * so it cannot be sold twice. Works whether or not the business tracks reserved stock by hand.
     */
    @Transactional
    public StockMovement documentReserve(ProductVariant variant, Warehouse warehouse, BigDecimal quantity, String reason) {
        return apply(new Change(MovementType.RESERVE, variant, warehouse, quantity, reason, null, null, null, null, null,
                false, null, null, null, true));
    }

    /** Releases stock held by {@link #documentReserve}. */
    @Transactional
    public StockMovement documentRelease(ProductVariant variant, Warehouse warehouse, BigDecimal quantity, String reason) {
        return apply(new Change(MovementType.RELEASE, variant, warehouse, quantity, reason, null, null, null, null, null,
                false, null, null, null, true));
    }

    /** Sets a balance to a counted quantity; returns null when no change was needed. */
    @Transactional
    public StockMovement countAdjust(ProductVariant variant, Warehouse warehouse, Long batchId, BigDecimal counted,
                                     Long stockCountId) {
        return apply(new Change(MovementType.COUNT_ADJUST, variant, warehouse, counted, "Stock count", null,
                batchId, null, null, null, false, null, stockCountId, null));
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> history(Long productId, Long variantId, Long warehouseId,
                                                  List<MovementType> types, Long actorId, Instant from, Instant to,
                                                  int page, int size) {
        Specification<StockMovement> spec = movementSpec(productId, variantId, warehouseId, types, actorId, from, to);
        return PageResponse.of(movements.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))), StockService::toResponse);
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> historyAll(Long productId, Long variantId, Long warehouseId, List<MovementType> types,
                                             Long actorId, Instant from, Instant to, int limit) {
        Specification<StockMovement> spec = movementSpec(productId, variantId, warehouseId, types, actorId, from, to);
        return movements.findAll(spec, PageRequest.of(0, limit,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .getContent().stream().map(StockService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> balancesForVariant(Long variantId) {
        variant(variantId);
        return balances.findByVariant(variantId).stream().map(StockService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> batchesForVariant(Long variantId) {
        variant(variantId);
        Map<Long, BigDecimal> onHand = new HashMap<>();
        balances.findByVariant(variantId).forEach(b -> {
            if (b.getBatch() != null) onHand.merge(b.getBatch().getId(), b.getOnHand(), BigDecimal::add);
        });
        return batches.findByVariantIdOrderByExpiryDateAsc(variantId).stream()
                .map(b -> new BatchResponse(b.getId(), b.getLotNumber(), b.getExpiryDate(),
                        onHand.getOrDefault(b.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SerialResponse> serialsForVariant(Long variantId) {
        variant(variantId);
        return serialNumbers.findByVariantIdOrderBySerialAsc(variantId).stream()
                .map(s -> new SerialResponse(s.getId(), s.getSerial(), s.getStatus().name(),
                        s.getWarehouse() != null ? s.getWarehouse().getId() : null,
                        s.getWarehouse() != null ? s.getWarehouse().getName() : null, s.getUpdatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ core

    private StockMovement apply(Change c) {
        CurrentUser me = CurrentUser.get();
        Long businessId = me.businessId();
        BusinessSettings settings = businessService.settingsEntity(businessId);
        ProductVariant variant = c.variant();
        Product product = variant.getProduct();
        Unit unit = product.getBaseUnit();
        MovementType type = c.type();

        if (variant.isArchived() || product.isArchived()) {
            throw new ConflictException("This product is archived. Restore it before changing stock.");
        }
        if (c.warehouse().isArchived()) {
            throw new ConflictException("Warehouse " + c.warehouse().getName() + " is archived");
        }
        boolean isTarget = type == MovementType.ADJUST || type == MovementType.COUNT_ADJUST;
        validateQuantity(c.quantity(), unit, !isTarget);

        boolean damagedReserved = type == MovementType.DAMAGE || type == MovementType.RESERVE
                || type == MovementType.RELEASE || c.fromDamaged();
        if (damagedReserved && !settings.isTrackDamagedReserved() && !c.system()) {
            throw new BadRequestException("Damaged and reserved stock tracking is turned off in business settings");
        }

        boolean serialTracked = settings.isTrackSerials() && product.isTrackSerials();
        boolean batchTracked = settings.isTrackBatches() && product.isTrackBatches();
        Batch batch = resolveBatch(c, batchTracked, settings, businessId);
        Long batchId = batch != null ? batch.getId() : null;

        StockBalance balance = lockBalance(variant, c.warehouse(), batchId);
        BigDecimal q = c.quantity();
        BigDecimal before;
        BigDecimal after;
        BigDecimal signed;
        BigDecimal availableBefore = balance.available();

        switch (type) {
            case OPENING, ADD, TRANSFER_IN, SALE_CANCEL, RETURN_IN, PURCHASE -> {
                before = balance.getOnHand();
                after = before.add(q);
                signed = q;
                balance.setOnHand(after);
            }
            case REDUCE, TRANSFER_OUT, SALE, PURCHASE_CANCEL, PURCHASE_RETURN -> {
                before = balance.getOnHand();
                if (c.fromDamaged()) {
                    if (balance.getDamaged().compareTo(q) < 0) {
                        throw new InsufficientStockException(balance.getDamaged(), q);
                    }
                    balance.setDamaged(balance.getDamaged().subtract(q));
                } else if (balance.available().compareTo(q) < 0 && !settings.isAllowNegativeStock()) {
                    throw new InsufficientStockException(balance.available(), q);
                }
                after = before.subtract(q);
                signed = q.negate();
                balance.setOnHand(after);
            }
            case ADJUST, COUNT_ADJUST -> {
                before = balance.getOnHand();
                after = q;
                signed = after.subtract(before);
                if (signed.signum() == 0) return null;
                BigDecimal newAvailable = after.subtract(balance.getReserved()).subtract(balance.getDamaged());
                if (newAvailable.signum() < 0 && !settings.isAllowNegativeStock()) {
                    throw new BadRequestException("The new quantity is below the reserved and damaged stock");
                }
                balance.setOnHand(after);
            }
            case DAMAGE -> {
                if (balance.available().compareTo(q) < 0) throw new InsufficientStockException(balance.available(), q);
                before = balance.getDamaged();
                after = before.add(q);
                signed = q;
                balance.setDamaged(after);
            }
            case RESERVE -> {
                if (balance.available().compareTo(q) < 0) throw new InsufficientStockException(balance.available(), q);
                before = balance.getReserved();
                after = before.add(q);
                signed = q;
                balance.setReserved(after);
            }
            case RELEASE -> {
                if (balance.getReserved().compareTo(q) < 0) {
                    throw new BadRequestException("Only " + balance.getReserved().stripTrailingZeros().toPlainString()
                            + " is reserved");
                }
                before = balance.getReserved();
                after = before.subtract(q);
                signed = q.negate();
                balance.setReserved(after);
            }
            default -> throw new IllegalStateException("Unsupported movement " + type);
        }
        balance.setUpdatedAt(Instant.now());

        List<String> serials = serialTracked ? handleSerials(c, businessId) : null;

        StockMovement m = new StockMovement();
        m.setBusinessId(businessId);
        m.setVariant(variant);
        m.setProduct(product);
        m.setWarehouse(c.warehouse());
        m.setBatch(batch);
        m.setType(type);
        m.setQuantity(signed);
        m.setQuantityBefore(before);
        m.setQuantityAfter(after);
        m.setReason(AuthService.blankToNull(c.reason()));
        m.setNotes(AuthService.blankToNull(c.notes()));
        m.setSerials(serials);
        m.setActor(memberships.getReferenceById(me.membershipId()));
        m.setTransferGroupId(c.transferGroupId());
        m.setStockCountId(c.stockCountId());
        m.setClientRequestId(c.clientRequestId());
        movements.save(m);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", type.name());
        details.put("variantId", variant.getId());
        details.put("warehouseId", c.warehouse().getId());
        details.put("quantity", signed);
        details.put("before", before);
        details.put("after", after);
        audit.record(businessId, me.membershipId(), "STOCK_" + type.name(), "StockMovement", m.getId(), details);

        checkThreshold(variant, settings, balance.available().subtract(availableBefore));
        return m;
    }

    private StockBalance lockBalance(ProductVariant variant, Warehouse warehouse, Long batchId) {
        balances.insertIfMissing(variant.getBusinessId(), variant.getId(), warehouse.getId(), batchId);
        return balances.lock(variant.getId(), warehouse.getId(), batchId)
                .orElseThrow(() -> new IllegalStateException("Balance row missing after insert"));
    }

    private void validateQuantity(BigDecimal q, Unit unit, boolean mustBePositive) {
        if (q == null) throw new BadRequestException("Validation failed", Map.of("quantity", "Quantity is required"));
        if (mustBePositive ? q.signum() <= 0 : q.signum() < 0) {
            throw new BadRequestException("Validation failed", Map.of("quantity",
                    mustBePositive ? "Quantity must be greater than zero" : "Quantity cannot be negative"));
        }
        if (q.stripTrailingZeros().scale() > unit.getDecimals()) {
            throw new BadRequestException("Validation failed", Map.of("quantity", unit.getDecimals() == 0
                    ? unit.getName() + " must be whole numbers"
                    : unit.getName() + " allow at most " + unit.getDecimals() + " decimal places"));
        }
    }

    private Batch resolveBatch(Change c, boolean batchTracked, BusinessSettings settings, Long businessId) {
        if (!batchTracked) return null;
        boolean receiving = c.type() == MovementType.OPENING || c.type() == MovementType.ADD
                || c.type() == MovementType.PURCHASE;
        if (c.batchId() != null) {
            Batch b = batches.findByIdAndBusinessId(c.batchId(), businessId)
                    .filter(x -> x.getVariant().getId().equals(c.variant().getId()))
                    .orElseThrow(() -> new NotFoundException("Batch", c.batchId()));
            return b;
        }
        String lot = c.lotNumber() == null ? null : c.lotNumber().trim();
        if (lot == null || lot.isEmpty()) {
            if (c.type() == MovementType.COUNT_ADJUST) return null;
            throw new BadRequestException("Validation failed", Map.of("lotNumber", "Batch / lot number is required for this product"));
        }
        Optional<Batch> existing = batches.findByVariantAndLot(c.variant().getId(), lot);
        if (existing.isPresent()) {
            Batch b = existing.get();
            if (receiving && settings.isTrackExpiry() && c.expiryDate() != null && b.getExpiryDate() == null) {
                b.setExpiryDate(c.expiryDate());
            }
            return b;
        }
        if (!receiving) throw new NotFoundException("Batch " + lot + " not found for this product");
        if (settings.isTrackExpiry() && c.expiryDate() == null) {
            throw new BadRequestException("Validation failed", Map.of("expiryDate", "Expiry date is required for a new batch"));
        }
        Batch b = new Batch();
        b.setBusinessId(businessId);
        b.setVariant(c.variant());
        b.setLotNumber(lot);
        b.setExpiryDate(settings.isTrackExpiry() ? c.expiryDate() : null);
        return batches.save(b);
    }

    private Long resolveBatchForIssue(ProductVariant variant, Long batchId, String lotNumber) {
        BusinessSettings settings = businessService.settingsEntity(variant.getBusinessId());
        if (!(settings.isTrackBatches() && variant.getProduct().isTrackBatches())) return null;
        if (batchId != null) {
            return batches.findByIdAndBusinessId(batchId, variant.getBusinessId())
                    .filter(b -> b.getVariant().getId().equals(variant.getId()))
                    .orElseThrow(() -> new NotFoundException("Batch", batchId)).getId();
        }
        if (lotNumber == null || lotNumber.isBlank()) {
            throw new BadRequestException("Validation failed", Map.of("lotNumber", "Batch / lot number is required for this product"));
        }
        return batches.findByVariantAndLot(variant.getId(), lotNumber.trim())
                .orElseThrow(() -> new NotFoundException("Batch " + lotNumber + " not found")).getId();
    }

    /** Validates and updates serial numbers for serial-tracked products. */
    private List<String> handleSerials(Change c, Long businessId) {
        MovementType type = c.type();
        if (type == MovementType.COUNT_ADJUST) return null;
        if (type == MovementType.ADJUST || type == MovementType.DAMAGE || type == MovementType.RESERVE
                || type == MovementType.RELEASE) {
            if (c.serials() == null || c.serials().isEmpty()) return null;
        }
        List<String> serials = c.serials() == null ? List.of()
                : c.serials().stream().map(String::trim).distinct().toList();
        if (type == MovementType.ADJUST) {
            throw new BadRequestException("Serial-tracked products are changed with Add or Reduce, listing each serial number");
        }
        if (type != MovementType.DAMAGE && type != MovementType.RESERVE && type != MovementType.RELEASE
                && c.quantity().compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw new BadRequestException("Validation failed", Map.of("serials",
                    "Enter exactly " + c.quantity().stripTrailingZeros().toPlainString() + " serial numbers"));
        }
        Map<String, SerialNumber> found = new HashMap<>();
        serialNumbers.findByBusinessIdAndVariantIdAndSerialIn(businessId, c.variant().getId(), serials)
                .forEach(s -> found.put(s.getSerial(), s));
        Instant now = Instant.now();
        for (String serial : serials) {
            SerialNumber s = found.get(serial);
            switch (type) {
                case OPENING, ADD, SALE_CANCEL, RETURN_IN, PURCHASE -> {
                    if (s != null && s.getStatus() != SerialStatus.OUT) {
                        throw new ConflictException("Serial " + serial + " is already in stock");
                    }
                    if (s == null) {
                        s = new SerialNumber();
                        s.setBusinessId(businessId);
                        s.setVariant(c.variant());
                        s.setSerial(serial);
                    }
                    s.setStatus(SerialStatus.IN_STOCK);
                    s.setWarehouse(c.warehouse());
                }
                case REDUCE, TRANSFER_OUT, DAMAGE, RESERVE, SALE, PURCHASE_CANCEL -> {
                    if (s == null || s.getStatus() != SerialStatus.IN_STOCK
                            || s.getWarehouse() == null || !s.getWarehouse().getId().equals(c.warehouse().getId())) {
                        throw new ConflictException("Serial " + serial + " is not in stock at " + c.warehouse().getName());
                    }
                    if (type == MovementType.REDUCE || type == MovementType.SALE || type == MovementType.PURCHASE_CANCEL) {
                        s.setStatus(SerialStatus.OUT);
                    }
                    if (type == MovementType.DAMAGE) s.setStatus(SerialStatus.DAMAGED);
                    if (type == MovementType.RESERVE) s.setStatus(SerialStatus.RESERVED);
                }
                case TRANSFER_IN -> {
                    if (s == null) throw new ConflictException("Serial " + serial + " is unknown");
                    s.setWarehouse(c.warehouse());
                }
                case RELEASE -> {
                    if (s == null || s.getStatus() != SerialStatus.RESERVED) {
                        throw new ConflictException("Serial " + serial + " is not reserved");
                    }
                    s.setStatus(SerialStatus.IN_STOCK);
                }
                default -> {
                }
            }
            s.setUpdatedAt(now);
            serialNumbers.save(s);
        }
        return serials;
    }

    /** Raises a low-stock or out-of-stock alert when a change crosses the variant's threshold. */
    private void checkThreshold(ProductVariant variant, BusinessSettings settings, BigDecimal availableDelta) {
        if (!settings.isNotifyLowStock() || availableDelta.signum() >= 0) return;
        BigDecimal after = stockLevels.byVariant(List.of(variant.getId()), null)
                .getOrDefault(variant.getId(), com.b2binventory.dto.ProductDtos.StockTotals.ZERO).available();
        BigDecimal before = after.subtract(availableDelta);
        int threshold = StockLevels.threshold(variant, settings);
        StockStatus was = StockLevels.status(before, threshold);
        StockStatus now = StockLevels.status(after, threshold);
        if (now == was || now == StockStatus.IN_STOCK) return;

        Product product = variant.getProduct();
        String name = variant.isDefaultVariant() ? product.getName() : product.getName() + " (" + variant.getName() + ")";
        String qty = after.stripTrailingZeros().toPlainString() + " " + product.getBaseUnit().getCode();
        Map<String, Object> payload = Map.of("productId", product.getId(), "variantId", variant.getId());
        if (now == StockStatus.OUT_OF_STOCK) {
            notifications.notify(variant.getBusinessId(), null, NotificationService.OUT_OF_STOCK,
                    name + " is out of stock", "Available quantity is " + qty + ".", payload,
                    "OUT:" + variant.getId(), Duration.ofHours(6));
        } else {
            notifications.notify(variant.getBusinessId(), null, NotificationService.LOW_STOCK,
                    name + " is running low", "Only " + qty + " left (minimum " + threshold + ").", payload,
                    "LOW:" + variant.getId(), Duration.ofHours(6));
        }
    }

    // ------------------------------------------------------------------ helpers

    private ProductVariant variant(Long id) {
        return variants.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Product variant", id));
    }

    private Warehouse activeWarehouse(Long id) {
        Warehouse w = warehouses.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Warehouse", id));
        if (w.isArchived()) throw new ConflictException("Warehouse " + w.getName() + " is archived");
        return w;
    }

    Warehouse warehouseOrDefault(Long id) {
        if (id != null) return activeWarehouse(id);
        Long businessId = CurrentUser.get().businessId();
        return warehouses.findFirstByBusinessIdAndDefaultWarehouseTrue(businessId)
                .or(() -> warehouses.findByBusinessIdAndArchivedFalseOrderByNameAsc(businessId).stream().findFirst())
                .orElseThrow(() -> new BadRequestException("Create a warehouse first"));
    }

    /** Returns the result of an already-applied request with the same idempotency key, if any. */
    private Optional<OperationResult> replay(UUID clientRequestId, ProductVariant variant) {
        if (clientRequestId == null) return Optional.empty();
        Long businessId = CurrentUser.get().businessId();
        return movements.findByBusinessIdAndClientRequestId(businessId, clientRequestId).map(first -> {
            List<StockMovement> ms = first.getTransferGroupId() == null ? List.of(first)
                    : movements.findByBusinessIdAndTransferGroupIdOrderByIdAsc(businessId, first.getTransferGroupId());
            return result(ms, first.getVariant());
        });
    }

    private OperationResult result(List<StockMovement> ms, ProductVariant variant) {
        return new OperationResult(ms.stream().map(StockService::toResponse).toList(),
                balances.findByVariant(variant.getId()).stream().map(StockService::toResponse).toList());
    }

    private Specification<StockMovement> movementSpec(Long productId, Long variantId, Long warehouseId,
                                                      List<MovementType> types, Long actorId, Instant from, Instant to) {
        Long businessId = CurrentUser.get().businessId();
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("businessId"), businessId));
            if (productId != null) p.add(cb.equal(root.get("product").get("id"), productId));
            if (variantId != null) p.add(cb.equal(root.get("variant").get("id"), variantId));
            if (warehouseId != null) p.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            if (types != null && !types.isEmpty()) p.add(root.get("type").in(types));
            if (actorId != null) p.add(cb.equal(root.get("actor").get("id"), actorId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static String bucket(MovementType type) {
        return switch (type) {
            case DAMAGE -> "DAMAGED";
            case RESERVE, RELEASE -> "RESERVED";
            default -> "ON_HAND";
        };
    }

    public static MovementResponse toResponse(StockMovement m) {
        ProductVariant v = m.getVariant();
        Product p = m.getProduct();
        return new MovementResponse(m.getId(), m.getType(), bucket(m.getType()), p.getId(), p.getName(), v.getId(),
                v.getName(), v.getSku(), m.getWarehouse().getId(), m.getWarehouse().getName(),
                m.getBatch() != null ? m.getBatch().getId() : null,
                m.getBatch() != null ? m.getBatch().getLotNumber() : null,
                m.getQuantity(), m.getQuantityBefore(), m.getQuantityAfter(), p.getBaseUnit().getCode(), m.getReason(),
                m.getNotes(), m.getSerials(), m.getActor().getId(), m.getActor().getUser().getFullName(),
                m.getTransferGroupId(), m.getStockCountId(), m.getCreatedAt());
    }

    public static BalanceResponse toResponse(StockBalance b) {
        return new BalanceResponse(b.getWarehouse().getId(), b.getWarehouse().getName(), b.getVariant().getId(),
                b.getVariant().getName(), b.getBatch() != null ? b.getBatch().getId() : null,
                b.getBatch() != null ? b.getBatch().getLotNumber() : null,
                b.getBatch() != null ? b.getBatch().getExpiryDate() : null,
                b.getOnHand(), b.getReserved(), b.getDamaged(), b.available());
    }
}
