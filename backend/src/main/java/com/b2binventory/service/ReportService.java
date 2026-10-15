package com.b2binventory.service;

import com.b2binventory.dto.PageResponse;
import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.dto.ReportDtos.*;
import com.b2binventory.dto.StockDtos.MovementResponse;
import com.b2binventory.entity.BusinessSettings;
import com.b2binventory.entity.MovementType;
import com.b2binventory.repository.ProductRepository;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.service.notification.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class ReportService {

    private static final String INVENTORY_SQL = """
            SELECT p.id, v.id, p.name, v.name, v.sku, v.barcode, c.name, u.code,
                   COALESCE(SUM(b.on_hand), 0), COALESCE(SUM(b.reserved), 0), COALESCE(SUM(b.damaged), 0),
                   COALESCE(v.min_stock, p.min_stock, :def)
            FROM product_variants v
            JOIN products p ON p.id = v.product_id
            JOIN units u ON u.id = p.base_unit_id
            LEFT JOIN categories c ON c.id = p.category_id
            LEFT JOIN stock_balances b ON b.variant_id = v.id
                 AND (CAST(:wid AS BIGINT) IS NULL OR b.warehouse_id = CAST(:wid AS BIGINT))
            WHERE v.business_id = :bid AND v.archived = FALSE AND p.archived = FALSE
              AND (:allCats OR p.category_id IN (:cats))
              AND (CAST(:q AS TEXT) IS NULL
                   OR LOWER(p.name) LIKE CAST(:q AS TEXT) OR LOWER(v.name) LIKE CAST(:q AS TEXT)
                   OR LOWER(COALESCE(v.sku, '')) LIKE CAST(:q AS TEXT) OR LOWER(COALESCE(v.barcode, '')) LIKE CAST(:q AS TEXT)
                   OR LOWER(COALESCE(p.brand, '')) LIKE CAST(:q AS TEXT))
            GROUP BY p.id, v.id, c.name, u.code
            ORDER BY LOWER(p.name), v.is_default DESC, LOWER(v.name)
            """;

    /** Product-level availability (all non-archived variants and warehouses). */
    private static final String PRODUCT_STOCK_SQL = """
            SELECT p.id, p.name, u.code, COALESCE(SUM(b.on_hand - b.reserved - b.damaged), 0),
                   COALESCE(p.min_stock, :def), COALESCE(SUM(b.on_hand), 0), p.updated_at
            FROM products p
            JOIN units u ON u.id = p.base_unit_id
            LEFT JOIN product_variants v ON v.product_id = p.id AND v.archived = FALSE
            LEFT JOIN stock_balances b ON b.variant_id = v.id
            WHERE p.business_id = :bid AND p.archived = FALSE
            GROUP BY p.id, u.code
            """;

    private static final String CATEGORY_SQL = """
            SELECT c.id, COALESCE(c.name, 'Uncategorized'), COUNT(DISTINCT p.id), COALESCE(SUM(b.on_hand), 0)
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            LEFT JOIN product_variants v ON v.product_id = p.id AND v.archived = FALSE
            LEFT JOIN stock_balances b ON b.variant_id = v.id
            WHERE p.business_id = :bid AND p.archived = FALSE
            GROUP BY c.id, c.name
            ORDER BY COUNT(DISTINCT p.id) DESC
            """;

    private final EntityManager em;
    private final BusinessService businessService;
    private final CategoryService categoryService;
    private final StockService stockService;
    private final WarehouseService warehouseService;
    private final ProductRepository products;
    private final NotificationService notifications;
    private final AnalyticsService analytics;

    public ReportService(EntityManager em, BusinessService businessService, CategoryService categoryService,
                         StockService stockService, WarehouseService warehouseService, ProductRepository products,
                         NotificationService notifications, AnalyticsService analytics) {
        this.em = em;
        this.businessService = businessService;
        this.categoryService = categoryService;
        this.stockService = stockService;
        this.warehouseService = warehouseService;
        this.products = products;
        this.notifications = notifications;
        this.analytics = analytics;
    }

    @Transactional(readOnly = true)
    public List<InventoryRow> inventoryRows(Long warehouseId, Long categoryId, String q, Set<StockStatus> statuses) {
        Long businessId = CurrentUser.get().businessId();
        if (warehouseId != null) warehouseService.find(warehouseId);
        BusinessSettings settings = businessService.settingsEntity(businessId);
        Collection<Long> cats = categoryId != null ? categoryService.withDescendants(categoryId) : List.of(-1L);
        Query query = em.createNativeQuery(INVENTORY_SQL)
                .setParameter("def", settings.getDefaultLowStockThreshold())
                .setParameter("wid", warehouseId)
                .setParameter("bid", businessId)
                .setParameter("allCats", categoryId == null)
                .setParameter("cats", cats)
                .setParameter("q", q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%");
        List<InventoryRow> rows = new ArrayList<>();
        for (Object r : query.getResultList()) {
            Object[] a = (Object[]) r;
            BigDecimal onHand = (BigDecimal) a[8];
            BigDecimal reserved = (BigDecimal) a[9];
            BigDecimal damaged = (BigDecimal) a[10];
            BigDecimal available = onHand.subtract(reserved).subtract(damaged);
            int min = ((Number) a[11]).intValue();
            StockStatus status = StockLevels.status(available, min);
            if (statuses != null && !statuses.isEmpty() && !statuses.contains(status)) continue;
            rows.add(new InventoryRow(((Number) a[0]).longValue(), ((Number) a[1]).longValue(), (String) a[2],
                    (String) a[3], (String) a[4], (String) a[5], (String) a[6], (String) a[7], onHand, reserved,
                    damaged, available, min, status));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryRow> inventory(Long warehouseId, Long categoryId, String q, Set<StockStatus> statuses,
                                                int page, int size) {
        return paginate(inventoryRows(warehouseId, categoryId, q, statuses), page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(Long productId, Long warehouseId, List<MovementType> types,
                                                    Long actorId, Instant from, Instant to, int page, int size) {
        return stockService.history(productId, null, warehouseId, types, actorId, from, to, page, size);
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Long businessId = CurrentUser.get().businessId();
        BusinessSettings settings = businessService.settingsEntity(businessId);

        List<StockAlert> low = new ArrayList<>();
        List<StockAlert> out = new ArrayList<>();
        List<RecentProduct> recent = new ArrayList<>();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        for (Object r : em.createNativeQuery(PRODUCT_STOCK_SQL)
                .setParameter("def", settings.getDefaultLowStockThreshold())
                .setParameter("bid", businessId).getResultList()) {
            Object[] a = (Object[]) r;
            BigDecimal available = (BigDecimal) a[3];
            int min = ((Number) a[4]).intValue();
            totalQuantity = totalQuantity.add((BigDecimal) a[5]);
            StockStatus status = StockLevels.status(available, min);
            StockAlert alert = new StockAlert(((Number) a[0]).longValue(), (String) a[1], (String) a[2], available, min, status);
            if (status == StockStatus.LOW_STOCK) low.add(alert);
            if (status == StockStatus.OUT_OF_STOCK) out.add(alert);
            recent.add(new RecentProduct(alert.productId(), alert.productName(), alert.unit(), available, status,
                    toInstant(a[6])));
        }
        recent.sort(Comparator.comparing(RecentProduct::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        low.sort(Comparator.comparing(StockAlert::available));

        List<CategorySummary> categories = new ArrayList<>();
        for (Object r : em.createNativeQuery(CATEGORY_SQL).setParameter("bid", businessId).getResultList()) {
            Object[] a = (Object[]) r;
            categories.add(new CategorySummary(a[0] != null ? ((Number) a[0]).longValue() : null, (String) a[1],
                    ((Number) a[2]).longValue(), (BigDecimal) a[3]));
        }
        List<WarehouseSummary> warehouses = warehouseService.list(false).stream()
                .map(w -> new WarehouseSummary(w.id(), w.name(), w.productCount(), w.totalQuantity())).toList();

        return new DashboardResponse(products.countByBusinessIdAndArchivedFalse(businessId), totalQuantity,
                low.size(), out.size(), low.stream().limit(10).toList(), out.stream().limit(10).toList(),
                stockService.historyAll(null, null, null, null, null, null, null, 10),
                categories.stream().limit(10).toList(), warehouses, notifications.unreadCount().count(),
                recent.stream().limit(5).toList(), highlights(businessId));
    }

    private SalesHighlights highlights(Long businessId) {
        if (!CurrentUser.get().has(Permission.ANALYTICS_VIEW)) return null;
        LocalDate today = LocalDate.now(businessService.zone(businessId));
        var month = analytics.overview(today.withDayOfMonth(1), today);
        return new SalesHighlights(analytics.snapshot(today, today),
                new com.b2binventory.dto.AnalyticsDtos.SalesSnapshot(month.sales().value().longValue(), month.units().value(),
                        month.revenue().value(), month.grossProfit() != null ? month.grossProfit().value() : null,
                        month.period().currencyCode()),
                month.bestSeller(), month.lowestSeller(), month.productsWithNoSales(), month.inventory());
    }

    private static Instant toInstant(Object o) {
        return switch (o) {
            case null -> null;
            case Instant i -> i;
            case java.sql.Timestamp t -> t.toInstant();
            case java.time.OffsetDateTime odt -> odt.toInstant();
            default -> throw new IllegalStateException("Unexpected timestamp type " + o.getClass());
        };
    }

    private static <T> PageResponse<T> paginate(List<T> rows, int page, int size) {
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        int pages = size == 0 ? 0 : (int) Math.ceil(rows.size() / (double) size);
        return new PageResponse<>(rows.subList(from, to), page, size, rows.size(), pages);
    }
}
