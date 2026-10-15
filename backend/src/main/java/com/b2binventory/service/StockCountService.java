package com.b2binventory.service;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.StockDtos.*;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.MembershipRepository;
import com.b2binventory.repository.ProductVariantRepository;
import com.b2binventory.repository.StockBalanceRepository;
import com.b2binventory.repository.StockCountRepository;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Stock counts snapshot the expected quantities of a warehouse, collect counted quantities and
 * reconcile them into COUNT_ADJUST movements.
 */
@Service
public class StockCountService {

    private final StockCountRepository counts;
    private final ProductVariantRepository variants;
    private final StockBalanceRepository balances;
    private final MembershipRepository memberships;
    private final WarehouseService warehouseService;
    private final CategoryService categoryService;
    private final StockService stockService;
    private final AuditService audit;

    public StockCountService(StockCountRepository counts, ProductVariantRepository variants,
                             StockBalanceRepository balances, MembershipRepository memberships,
                             WarehouseService warehouseService, CategoryService categoryService,
                             StockService stockService, AuditService audit) {
        this.counts = counts;
        this.variants = variants;
        this.balances = balances;
        this.memberships = memberships;
        this.warehouseService = warehouseService;
        this.categoryService = categoryService;
        this.stockService = stockService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockCountSummary> list(int page, int size) {
        return PageResponse.of(counts.findByBusinessIdOrderByCreatedAtDesc(CurrentUser.get().businessId(),
                PageRequest.of(page, size)), StockCountService::summary);
    }

    @Transactional(readOnly = true)
    public StockCountResponse get(Long id) {
        return response(find(id));
    }

    @Transactional
    public StockCountResponse create(StockCountRequest request) {
        CurrentUser me = CurrentUser.get();
        Warehouse warehouse = warehouseService.find(request.warehouseId());
        if (warehouse.isArchived()) throw new ConflictException("This warehouse is archived");
        Set<Long> categoryIds = request.categoryId() != null ? categoryService.withDescendants(request.categoryId()) : Set.of(-1L);

        StockCount count = new StockCount();
        count.setBusinessId(me.businessId());
        count.setWarehouse(warehouse);
        count.setNotes(AuthService.blankToNull(request.notes()));
        count.setCreatedBy(memberships.getReferenceById(me.membershipId()));

        Map<Long, List<StockBalance>> byVariant = new HashMap<>();
        balances.findByWarehouse(warehouse.getId()).forEach(b -> byVariant
                .computeIfAbsent(b.getVariant().getId(), k -> new ArrayList<>()).add(b));
        for (ProductVariant v : variants.findActiveForCount(me.businessId(), categoryIds, request.categoryId() == null)) {
            List<StockBalance> rows = byVariant.getOrDefault(v.getId(), List.of());
            if (rows.isEmpty()) {
                count.getLines().add(line(count, v, null, BigDecimal.ZERO));
            } else {
                rows.forEach(b -> count.getLines().add(line(count, v, b.getBatch(), b.getOnHand())));
            }
        }
        if (count.getLines().isEmpty()) {
            throw new BadRequestException("There are no products to count for this selection");
        }
        counts.save(count);
        audit.record(me.businessId(), me.membershipId(), "STOCK_COUNT_CREATED", "StockCount", count.getId(),
                Map.of("warehouseId", warehouse.getId(), "lines", count.getLines().size()));
        return response(count);
    }

    @Transactional
    public StockCountResponse updateLines(Long id, CountLinesRequest request) {
        StockCount count = open(id);
        Map<Long, StockCountLine> byId = new HashMap<>();
        count.getLines().forEach(l -> byId.put(l.getId(), l));
        for (CountLineUpdate u : request.lines()) {
            StockCountLine line = byId.get(u.lineId());
            if (line == null) throw new NotFoundException("Count line", u.lineId());
            if (u.countedQty() != null) {
                Unit unit = line.getVariant().getProduct().getBaseUnit();
                if (u.countedQty().stripTrailingZeros().scale() > unit.getDecimals()) {
                    throw new BadRequestException("Validation failed", Map.of("lines",
                            unit.getName() + " allow at most " + unit.getDecimals() + " decimal places"));
                }
            }
            line.setCountedQty(u.countedQty());
        }
        return response(count);
    }

    @Transactional
    public ReconcileResult reconcile(Long id) {
        Permissions.require(Permission.STOCK_ADJUST);
        CurrentUser me = CurrentUser.get();
        StockCount count = open(id);
        int adjusted = 0;
        for (StockCountLine line : count.getLines()) {
            if (line.getCountedQty() == null) continue;
            StockMovement m = stockService.countAdjust(line.getVariant(), count.getWarehouse(),
                    line.getBatch() != null ? line.getBatch().getId() : null, line.getCountedQty(), count.getId());
            if (m != null) adjusted++;
        }
        count.setStatus(CountStatus.RECONCILED);
        count.setReconciledAt(Instant.now());
        audit.record(me.businessId(), me.membershipId(), "STOCK_COUNT_RECONCILED", "StockCount", id,
                Map.of("adjustedLines", adjusted));
        return new ReconcileResult(summary(count), adjusted);
    }

    @Transactional
    public StockCountSummary cancel(Long id) {
        CurrentUser me = CurrentUser.get();
        StockCount count = open(id);
        count.setStatus(CountStatus.CANCELLED);
        audit.record(me.businessId(), me.membershipId(), "STOCK_COUNT_CANCELLED", "StockCount", id, null);
        return summary(count);
    }

    private StockCount find(Long id) {
        return counts.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Stock count", id));
    }

    private StockCount open(Long id) {
        StockCount count = find(id);
        if (count.getStatus() != CountStatus.OPEN) {
            throw new ConflictException("This stock count is already " + count.getStatus().name().toLowerCase());
        }
        return count;
    }

    private static StockCountLine line(StockCount count, ProductVariant v, Batch batch, BigDecimal expected) {
        StockCountLine l = new StockCountLine();
        l.setStockCount(count);
        l.setVariant(v);
        l.setBatch(batch);
        l.setExpectedQty(expected);
        return l;
    }

    private static StockCountSummary summary(StockCount c) {
        int counted = (int) c.getLines().stream().filter(l -> l.getCountedQty() != null).count();
        return new StockCountSummary(c.getId(), c.getWarehouse().getId(), c.getWarehouse().getName(), c.getStatus(),
                c.getNotes(), c.getCreatedBy().getUser().getFullName(), c.getCreatedAt(), c.getReconciledAt(),
                c.getLines().size(), counted);
    }

    private static StockCountResponse response(StockCount c) {
        List<CountLineResponse> lines = c.getLines().stream().map(l -> {
            ProductVariant v = l.getVariant();
            Product p = v.getProduct();
            BigDecimal diff = l.getCountedQty() != null ? l.getCountedQty().subtract(l.getExpectedQty()) : null;
            return new CountLineResponse(l.getId(), v.getId(), p.getId(), p.getName(), v.getName(), v.getSku(),
                    p.getBaseUnit().getCode(), l.getBatch() != null ? l.getBatch().getId() : null,
                    l.getBatch() != null ? l.getBatch().getLotNumber() : null, l.getExpectedQty(), l.getCountedQty(), diff);
        }).toList();
        return new StockCountResponse(summary(c), lines);
    }
}
