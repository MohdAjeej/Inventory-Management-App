package com.b2binventory.service;

import com.b2binventory.dto.AnalyticsDtos.*;
import com.b2binventory.dto.AnalyticsDtos.Period;
import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.entity.BusinessSettings;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sales, profit and product analytics, aggregated in the database from COMPLETED sales. Every query is scoped to
 * the caller's business. Periods are inclusive local dates in the business timezone and are compared with the
 * previous period of the same length.
 * <p>
 * Each sale line contributes: units = quantity - returned quantity; net = net amount - net value returned;
 * cost = saved cost - cost of resalable returns (damaged returns stay in cost of goods sold). Returns are counted
 * against the date of the original sale.
 */
@Service
public class AnalyticsService {

    static final int MAX_DAYS = 3660;
    /** Growth within this band (percent) counts as stable. */
    static final double STABLE_BAND = 10.0;
    static final int DORMANT_DAYS = 90;

    /** Sale lines of completed sales from :pfrom (inclusive) to :to (exclusive), net of returns. */
    private static final String ITEMS_CTE = """
            WITH r AS (
                SELECT ri.sale_item_id, SUM(ri.net_amount) AS net, SUM(ri.tax_amount) AS tax,
                       SUM(CASE WHEN ri.resalable THEN ri.cost_amount ELSE 0 END) AS cost
                FROM sale_return_items ri JOIN sale_returns sr ON sr.id = ri.return_id
                WHERE sr.business_id = :bid
                GROUP BY ri.sale_item_id
            ), items AS (
                SELECT i.product_id, s.id AS sale_id, s.sold_at,
                       i.quantity - i.returned_quantity AS units,
                       i.gross_amount AS gross, i.discount AS discount, COALESCE(r.net, 0) AS returns,
                       i.net_amount - COALESCE(r.net, 0) AS net,
                       i.tax_amount - COALESCE(r.tax, 0) AS tax,
                       CASE WHEN i.cost_amount IS NULL THEN NULL ELSE i.cost_amount - COALESCE(r.cost, 0) END AS cost
                FROM sale_items i
                JOIN sales s ON s.id = i.sale_id
                LEFT JOIN r ON r.sale_item_id = i.id
                WHERE s.business_id = :bid AND s.status = 'COMPLETED' AND s.sold_at >= :pfrom AND s.sold_at < :to
            )
            """;

    private static final String PRODUCT_SALES_SQL = ITEMS_CTE + """
            SELECT product_id,
                   COALESCE(SUM(units) FILTER (WHERE sold_at >= :from), 0),
                   COALESCE(SUM(net) FILTER (WHERE sold_at >= :from), 0),
                   COUNT(DISTINCT sale_id) FILTER (WHERE sold_at >= :from),
                   COUNT(DISTINCT CAST(sold_at AT TIME ZONE CAST(:tz AS TEXT) AS DATE)) FILTER (WHERE sold_at >= :from),
                   COALESCE(SUM(units) FILTER (WHERE sold_at < :from), 0),
                   COALESCE(SUM(net) FILTER (WHERE sold_at < :from), 0),
                   MAX(sold_at) FILTER (WHERE sold_at >= :from),
                   COALESCE(SUM(gross) FILTER (WHERE sold_at >= :from), 0),
                   COALESCE(SUM(discount) FILTER (WHERE sold_at >= :from), 0),
                   COALESCE(SUM(returns) FILTER (WHERE sold_at >= :from), 0),
                   COALESCE(SUM(tax) FILTER (WHERE sold_at >= :from), 0),
                   SUM(cost) FILTER (WHERE sold_at >= :from),
                   COUNT(*) FILTER (WHERE sold_at >= :from AND cost IS NULL),
                   SUM(cost) FILTER (WHERE sold_at < :from),
                   COUNT(*) FILTER (WHERE sold_at < :from AND cost IS NULL)
            FROM items
            GROUP BY product_id
            """;

    private static final String SALE_COUNT_SQL = """
            SELECT COUNT(*) FILTER (WHERE s.sold_at >= :from), COUNT(*) FILTER (WHERE s.sold_at < :from)
            FROM sales s
            WHERE s.business_id = :bid AND s.status = 'COMPLETED' AND s.sold_at >= :pfrom AND s.sold_at < :to
            """;

    /** Active products (and archived ones, kept in Java only when they sold in the period). */
    private static final String PRODUCTS_SQL = """
            SELECT p.id, p.name, dv.sku, p.category_id, c.name, u.code,
                   COALESCE((SELECT SUM(b.on_hand - b.reserved - b.damaged)
                             FROM stock_balances b JOIN product_variants v ON v.id = b.variant_id
                             WHERE v.product_id = p.id AND v.archived = FALSE), 0),
                   COALESCE(p.min_stock, :def), p.archived, p.created_at, p.average_cost, p.purchase_price, p.sale_price
            FROM products p
            JOIN units u ON u.id = p.base_unit_id
            LEFT JOIN categories c ON c.id = p.category_id
            LEFT JOIN product_variants dv ON dv.product_id = p.id AND dv.is_default = TRUE
            WHERE p.business_id = :bid
            """;

    private static final String LAST_SOLD_SQL = """
            SELECT i.product_id, MAX(s.sold_at)
            FROM sale_items i JOIN sales s ON s.id = i.sale_id
            WHERE s.business_id = :bid AND s.status = 'COMPLETED'
            GROUP BY i.product_id
            """;

    private static final String TREND_SQL = ITEMS_CTE + """
            SELECT CAST(date_trunc(CAST(:g AS TEXT), sold_at AT TIME ZONE CAST(:tz AS TEXT)) AS DATE),
                   SUM(units), SUM(net), SUM(cost), COUNT(*) FILTER (WHERE cost IS NULL), COUNT(DISTINCT sale_id)
            FROM items
            WHERE CAST(:pid AS BIGINT) IS NULL OR product_id = CAST(:pid AS BIGINT)
            GROUP BY 1
            ORDER BY 1
            """;

    private static final String CATEGORY_SQL = ITEMS_CTE + """
            SELECT p.category_id, COALESCE(c.name, 'Uncategorized'),
                   COALESCE(SUM(items.units) FILTER (WHERE items.sold_at >= :from), 0),
                   COALESCE(SUM(items.net) FILTER (WHERE items.sold_at >= :from), 0),
                   COUNT(DISTINCT items.product_id) FILTER (WHERE items.sold_at >= :from AND items.units > 0),
                   COALESCE(SUM(items.units) FILTER (WHERE items.sold_at < :from), 0),
                   SUM(items.cost) FILTER (WHERE items.sold_at >= :from),
                   COUNT(*) FILTER (WHERE items.sold_at >= :from AND items.cost IS NULL)
            FROM items
            JOIN products p ON p.id = items.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            GROUP BY p.category_id, c.name
            """;

    private static final String PURCHASE_SQL = """
            SELECT COUNT(*) FILTER (WHERE pu.purchased_at >= :from),
                   COALESCE(SUM(pu.total) FILTER (WHERE pu.purchased_at >= :from), 0),
                   COALESCE(SUM(pu.total) FILTER (WHERE pu.purchased_at < :from), 0),
                   COALESCE((SELECT SUM(pi.quantity) FROM purchase_items pi JOIN purchases p2 ON p2.id = pi.purchase_id
                             WHERE p2.business_id = :bid AND p2.status = 'RECEIVED'
                               AND p2.purchased_at >= :from AND p2.purchased_at < :to), 0)
            FROM purchases pu
            WHERE pu.business_id = :bid AND pu.status = 'RECEIVED' AND pu.purchased_at >= :pfrom AND pu.purchased_at < :to
            """;

    private final EntityManager em;
    private final BusinessService businessService;
    private final CategoryService categoryService;

    public AnalyticsService(EntityManager em, BusinessService businessService, CategoryService categoryService) {
        this.em = em;
        this.businessService = businessService;
        this.categoryService = categoryService;
    }

    /** Per-product figures before sorting and labelling. Cost fields are null when unknown. */
    private record Row(Long productId, String name, String sku, Long categoryId, String categoryName, String unit,
                       BigDecimal available, int minStock, boolean archived, Instant createdAt, BigDecimal averageCost,
                       BigDecimal purchasePrice, BigDecimal salePrice, BigDecimal units, BigDecimal net, long sales,
                       int days, BigDecimal prevUnits, BigDecimal prevNet, Instant lastSoldAt, BigDecimal gross,
                       BigDecimal discounts, BigDecimal returns, BigDecimal tax, BigDecimal cost, long linesWithoutCost,
                       BigDecimal prevCost, long prevLinesWithoutCost) {

        BigDecimal profit() {
            return linesWithoutCost > 0 ? null : net.subtract(Money.orZero(cost));
        }

        BigDecimal prevProfit() {
            return prevLinesWithoutCost > 0 ? null : prevNet.subtract(Money.orZero(prevCost));
        }
    }

    private record Ctx(Long businessId, ZoneId zone, Period period, Instant from, Instant to, Instant previousFrom,
                       BusinessSettings settings) {
    }

    // ------------------------------------------------------------------ public API

    @Transactional(readOnly = true)
    public Overview overview(LocalDate from, LocalDate to) {
        Ctx ctx = ctx(from, to);
        List<Row> rows = rows(ctx);
        List<ProductPerformance> ranked = rank(rows, ctx);

        BigDecimal units = BigDecimal.ZERO, net = BigDecimal.ZERO, prevUnits = BigDecimal.ZERO, prevNet = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO, discounts = BigDecimal.ZERO, returns = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO, prevCost = BigDecimal.ZERO;
        long missing = 0, prevMissing = 0;
        for (Row r : rows) {
            units = units.add(r.units());
            net = net.add(r.net());
            prevUnits = prevUnits.add(r.prevUnits());
            prevNet = prevNet.add(r.prevNet());
            gross = gross.add(r.gross());
            discounts = discounts.add(r.discounts());
            returns = returns.add(r.returns());
            tax = tax.add(r.tax());
            cost = cost.add(Money.orZero(r.cost()));
            prevCost = prevCost.add(Money.orZero(r.prevCost()));
            missing += r.linesWithoutCost();
            prevMissing += r.prevLinesWithoutCost();
        }
        BigDecimal profit = missing > 0 ? null : net.subtract(cost);
        BigDecimal prevProfit = prevMissing > 0 ? null : prevNet.subtract(prevCost);
        Financials financials = new Financials(gross, discounts, returns, net, tax, missing > 0 ? null : cost, profit,
                margin(profit, net), missing == 0, missing);

        Object[] counts = (Object[]) bind(em.createNativeQuery(SALE_COUNT_SQL), ctx).getSingleResult();
        long sales = ((Number) counts[0]).longValue();
        long prevSales = ((Number) counts[1]).longValue();

        List<ProductPerformance> sold = ranked.stream().filter(p -> p.unitsSold().signum() > 0).toList();
        ProductRef best = sold.isEmpty() ? null : ref(sold.getFirst());
        ProductRef lowest = sold.size() < 2 ? null : ref(sold.getLast());
        long active = ranked.stream().filter(p -> !p.archived()).count();
        long noSales = ranked.stream().filter(p -> !p.archived() && p.unitsSold().signum() == 0).count();
        BigDecimal days = BigDecimal.valueOf(ctx.period().days());
        return new Overview(ctx.period(),
                metric(BigDecimal.valueOf(sales), BigDecimal.valueOf(prevSales)),
                metric(units, prevUnits), metric(net, prevNet),
                profit == null ? null : new Metric(profit, prevProfit, prevProfit == null ? null : growth(profit, prevProfit)),
                financials, units.divide(days, 2, RoundingMode.HALF_UP), net.divide(days, 2, RoundingMode.HALF_UP),
                best, lowest, noSales, sold.size(), active, inventory(rows), purchases(ctx));
    }

    /** Compact figures for the home screen. */
    @Transactional(readOnly = true)
    public SalesSnapshot snapshot(LocalDate from, LocalDate to) {
        Overview o = overview(from, to);
        return new SalesSnapshot(o.sales().value().longValue(), o.units().value(), o.revenue().value(),
                o.grossProfit() != null ? o.grossProfit().value() : null, o.period().currencyCode());
    }

    /** sort: units (default) | revenue | profit | growth | name. Rank is always by units sold. */
    @Transactional(readOnly = true)
    public ProductAnalytics products(LocalDate from, LocalDate to, Long categoryId, String q, String sort) {
        Ctx ctx = ctx(from, to);
        List<ProductPerformance> ranked = rank(rows(ctx), ctx);
        Set<Long> cats = categoryId != null ? categoryService.withDescendants(categoryId) : null;
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        List<ProductPerformance> list = new ArrayList<>(ranked.stream()
                .filter(p -> cats == null || (p.categoryId() != null && cats.contains(p.categoryId())))
                .filter(p -> needle == null || p.name().toLowerCase(Locale.ROOT).contains(needle)
                        || (p.sku() != null && p.sku().toLowerCase(Locale.ROOT).contains(needle)))
                .toList());
        Comparator<ProductPerformance> byName = Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT));
        switch (sort == null ? "units" : sort) {
            case "units" -> { /* already ranked */ }
            case "revenue" -> list.sort(Comparator.comparing(ProductPerformance::revenue).reversed().thenComparing(byName));
            case "profit" -> list.sort(Comparator.comparing((ProductPerformance p) -> p.grossProfit() == null
                    ? new BigDecimal("-1E30") : p.grossProfit()).reversed().thenComparing(byName));
            case "growth" -> list.sort(Comparator.comparing((ProductPerformance p) -> p.growthPercent() == null
                    ? Double.NEGATIVE_INFINITY : p.growthPercent()).reversed().thenComparing(byName));
            case "name" -> list.sort(byName);
            default -> throw new BadRequestException("Sort must be units, revenue, profit, growth or name");
        }
        return new ProductAnalytics(ctx.period(), list);
    }

    /** granularity: day | week | month. Buckets with no sales are returned as zero. */
    @Transactional(readOnly = true)
    public Trend trend(LocalDate from, LocalDate to, String granularity, Long productId) {
        Ctx ctx = ctx(from, to);
        String g = granularity == null ? "day" : granularity;
        if (!Set.of("day", "week", "month").contains(g)) {
            throw new BadRequestException("Granularity must be day, week or month");
        }
        Map<LocalDate, TrendPoint> found = new HashMap<>();
        Query query = em.createNativeQuery(TREND_SQL)
                .setParameter("g", g).setParameter("tz", ctx.zone().getId()).setParameter("bid", ctx.businessId())
                .setParameter("pfrom", ctx.from()).setParameter("to", ctx.to()).setParameter("pid", productId);
        for (Object o : query.getResultList()) {
            Object[] a = (Object[]) o;
            LocalDate d = toDate(a[0]);
            BigDecimal net = dec(a[2]);
            BigDecimal profit = ((Number) a[4]).longValue() > 0 ? null : net.subtract(dec(a[3]));
            found.put(d, new TrendPoint(d, dec(a[1]), net, profit, ((Number) a[5]).longValue()));
        }
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate d = bucketStart(ctx.period().from(), g); !d.isAfter(ctx.period().to()); d = next(d, g)) {
            points.add(found.getOrDefault(d, new TrendPoint(d, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0)));
        }
        return new Trend(ctx.period(), g, points);
    }

    @Transactional(readOnly = true)
    public CategoryAnalytics categories(LocalDate from, LocalDate to) {
        Ctx ctx = ctx(from, to);
        List<CategoryPerformance> list = new ArrayList<>();
        for (Object o : bind(em.createNativeQuery(CATEGORY_SQL), ctx).getResultList()) {
            Object[] a = (Object[]) o;
            BigDecimal units = dec(a[2]);
            BigDecimal prev = dec(a[5]);
            if (units.signum() == 0 && prev.signum() == 0) continue;
            BigDecimal net = dec(a[3]);
            BigDecimal profit = ((Number) a[7]).longValue() > 0 ? null : net.subtract(dec(a[6]));
            list.add(new CategoryPerformance(a[0] != null ? ((Number) a[0]).longValue() : null, (String) a[1], units,
                    net, profit, ((Number) a[4]).longValue(), prev, growth(units, prev)));
        }
        list.sort(Comparator.comparing(CategoryPerformance::units).reversed()
                .thenComparing(CategoryPerformance::categoryName));
        return new CategoryAnalytics(ctx.period(), list);
    }

    /** Plain-language observations derived only from recorded sales and current stock. */
    @Transactional(readOnly = true)
    public Insights insights(LocalDate from, LocalDate to) {
        Ctx ctx = ctx(from, to);
        List<Row> rows = rows(ctx);
        List<ProductPerformance> ranked = rank(rows, ctx);
        List<ProductPerformance> active = ranked.stream().filter(p -> !p.archived()).toList();
        List<ProductPerformance> sold = ranked.stream().filter(p -> p.unitsSold().signum() > 0).toList();
        List<Insight> out = new ArrayList<>();
        String period = describe(ctx.period());

        if (sold.isEmpty()) {
            out.add(new Insight("NO_SALES_PERIOD", "INFO", "No sales recorded",
                    "No sales were recorded " + period + ". Use Record Sale to add your first sale and see product insights.",
                    null, null));
        } else {
            ProductPerformance best = sold.getFirst();
            out.add(new Insight("BEST_SELLER", "GOOD", "Best seller",
                    "You sold " + qty(best.unitsSold(), best.unit()) + " of " + best.name() + " " + period
                            + ". It is your best-selling product.", best.productId(), "VIEW_PRODUCT"));
            if (sold.size() >= 2) {
                ProductPerformance low = sold.getLast();
                out.add(new Insight("LOWEST_SELLER", "INFO", "Lowest seller",
                        low.name() + " sold only " + qty(low.unitsSold(), low.unit()) + " " + period + ".",
                        low.productId(), "VIEW_PRODUCT"));
            }
        }

        // Profit trend, only when both periods have complete cost information.
        BigDecimal profit = BigDecimal.ZERO, prevProfit = BigDecimal.ZERO;
        boolean complete = true;
        for (Row r : rows) {
            if (r.profit() == null || r.prevProfit() == null) {
                complete = false;
                break;
            }
            profit = profit.add(r.profit());
            prevProfit = prevProfit.add(r.prevProfit());
        }
        Double profitGrowth = complete ? growth(profit, prevProfit) : null;
        if (profitGrowth != null && Math.abs(profitGrowth) >= 1) {
            boolean up = profitGrowth > 0;
            out.add(new Insight("PROFIT_TREND", up ? "GOOD" : "WARNING", up ? "Profit is up" : "Profit is down",
                    "Your gross profit " + (up ? "increased" : "decreased") + " " + pct(Math.abs(profitGrowth))
                            + " compared with the previous period.", null, null));
        }

        // Fast sellers that are about to run out.
        sold.stream()
                .filter(p -> p.stockStatus() != StockStatus.IN_STOCK || daysOfCover(p) < 7)
                .filter(p -> p.labels().contains("FAST_MOVING") || p.labels().contains("TOP_SELLER"))
                .limit(3)
                .forEach(p -> out.add(new Insight("REPLENISH", "WARNING", "Add stock soon",
                        p.name() + (p.stockStatus() == StockStatus.OUT_OF_STOCK ? " is out of stock" : " is running low")
                                + " and sells about " + qty(p.averageDailySales(), p.unit()) + " a day"
                                + coverText(p) + ". Consider restocking it.",
                        p.productId(), "ADD_STOCK")));

        sold.stream().filter(p -> p.performance() == Performance.GROWING)
                .max(Comparator.comparing(ProductPerformance::growthPercent))
                .ifPresent(p -> out.add(new Insight("TRENDING", "GOOD", "Selling faster",
                        p.name() + " is selling faster than in the previous period (" + pct(p.growthPercent()) + ").",
                        p.productId(), "VIEW_PRODUCT")));
        ranked.stream().filter(p -> p.performance() == Performance.DECLINING)
                .min(Comparator.comparing(ProductPerformance::growthPercent))
                .ifPresent(p -> out.add(new Insight("DECLINING", "WARNING", "Sales are falling",
                        p.name() + " sold " + pct(Math.abs(p.growthPercent())).substring(1) + " fewer units than in the "
                                + "previous period. You may want to look into why.", p.productId(), "VIEW_PRODUCT")));

        List<ProductPerformance> unsold = active.stream().filter(p -> p.unitsSold().signum() == 0).toList();
        if (!sold.isEmpty() && !unsold.isEmpty()) {
            String names = unsold.stream().limit(3).map(ProductPerformance::name).collect(Collectors.joining(", "));
            String more = unsold.size() > 3 ? " and " + (unsold.size() - 3) + " more" : "";
            out.add(new Insight("NO_SALES", "INFO", unsold.size() + (unsold.size() == 1 ? " product" : " products")
                    + " with no sales", names + more + " had no recorded sales " + period + ".",
                    unsold.size() == 1 ? unsold.getFirst().productId() : null,
                    unsold.size() == 1 ? "VIEW_PRODUCT" : null));
        }

        // Stock that has been sitting for a long time without any sale.
        Instant dormantBefore = Instant.now().minus(DORMANT_DAYS, ChronoUnit.DAYS);
        Map<Long, Instant> lastSold = lastSold(ctx.businessId());
        Map<Long, Row> rowsById = rows.stream().collect(Collectors.toMap(Row::productId, r -> r));
        active.stream()
                .filter(p -> p.currentStock().signum() > 0)
                .filter(p -> rowsById.get(p.productId()).createdAt().isBefore(dormantBefore))
                .filter(p -> !lastSold.containsKey(p.productId()) || lastSold.get(p.productId()).isBefore(dormantBefore))
                .limit(2)
                .forEach(p -> out.add(new Insight("DORMANT", "WARNING", "Slow-moving stock",
                        p.name() + " has " + qty(p.currentStock(), p.unit()) + " in stock but no recorded sales in over "
                                + DORMANT_DAYS + " days. Review it before ordering more.",
                        p.productId(), "VIEW_PRODUCT")));
        return new Insights(ctx.period(), out);
    }

    // ------------------------------------------------------------------ core

    private Ctx ctx(LocalDate from, LocalDate to) {
        Long businessId = CurrentUser.get().businessId();
        BusinessSettings settings = businessService.settingsEntity(businessId);
        ZoneId zone = businessService.zone(businessId);
        LocalDate today = LocalDate.now(zone);
        LocalDate t = to != null ? to : today;
        LocalDate f = from != null ? from : t.minusDays(29);
        if (f.isAfter(t)) throw new BadRequestException("The start date must be on or before the end date");
        long days = ChronoUnit.DAYS.between(f, t) + 1;
        if (days > MAX_DAYS) throw new BadRequestException("Choose a period of at most 10 years");
        LocalDate pf = f.minusDays(days);
        Period period = new Period(f, t, (int) days, pf, f.minusDays(1), zone.getId(), settings.getCurrencyCode());
        return new Ctx(businessId, zone, period, f.atStartOfDay(zone).toInstant(),
                t.plusDays(1).atStartOfDay(zone).toInstant(), pf.atStartOfDay(zone).toInstant(), settings);
    }

    private static Query bind(Query q, Ctx ctx) {
        return q.setParameter("bid", ctx.businessId()).setParameter("from", ctx.from())
                .setParameter("pfrom", ctx.previousFrom()).setParameter("to", ctx.to());
    }

    private List<Row> rows(Ctx ctx) {
        Map<Long, Object[]> salesByProduct = new HashMap<>();
        for (Object o : bind(em.createNativeQuery(PRODUCT_SALES_SQL), ctx).setParameter("tz", ctx.zone().getId())
                .getResultList()) {
            Object[] a = (Object[]) o;
            salesByProduct.put(((Number) a[0]).longValue(), a);
        }
        List<Row> rows = new ArrayList<>();
        for (Object o : em.createNativeQuery(PRODUCTS_SQL)
                .setParameter("def", ctx.settings().getDefaultLowStockThreshold())
                .setParameter("bid", ctx.businessId()).getResultList()) {
            Object[] a = (Object[]) o;
            long id = ((Number) a[0]).longValue();
            Object[] s = salesByProduct.get(id);
            boolean archived = (Boolean) a[8];
            boolean hasSales = s != null && (dec(s[1]).signum() != 0 || dec(s[5]).signum() != 0);
            if (archived && !hasSales) continue;
            rows.add(new Row(id, (String) a[1], (String) a[2], a[3] != null ? ((Number) a[3]).longValue() : null,
                    (String) a[4], (String) a[5], dec(a[6]), ((Number) a[7]).intValue(), archived, toInstant(a[9]),
                    decOrNull(a[10]), decOrNull(a[11]), decOrNull(a[12]),
                    s == null ? BigDecimal.ZERO : dec(s[1]), s == null ? BigDecimal.ZERO : dec(s[2]),
                    s == null ? 0 : ((Number) s[3]).longValue(), s == null ? 0 : ((Number) s[4]).intValue(),
                    s == null ? BigDecimal.ZERO : dec(s[5]), s == null ? BigDecimal.ZERO : dec(s[6]),
                    s == null ? null : toInstant(s[7]),
                    s == null ? BigDecimal.ZERO : dec(s[8]), s == null ? BigDecimal.ZERO : dec(s[9]),
                    s == null ? BigDecimal.ZERO : dec(s[10]), s == null ? BigDecimal.ZERO : dec(s[11]),
                    s == null ? BigDecimal.ZERO : decOrNull(s[12]), s == null ? 0 : ((Number) s[13]).longValue(),
                    s == null ? BigDecimal.ZERO : decOrNull(s[14]), s == null ? 0 : ((Number) s[15]).longValue()));
        }
        return rows;
    }

    /** Orders by units sold (then revenue, then name), and assigns rank, performance and labels. */
    private List<ProductPerformance> rank(List<Row> rows, Ctx ctx) {
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(Row::units).reversed()
                .thenComparing(Comparator.comparing(Row::net).reversed())
                .thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
        long sellers = sorted.stream().filter(r -> r.units().signum() > 0).count();
        // Sellers come first in the sorted list, so a seller's index is its position among sellers.
        int topCount = sellers == 0 ? 0 : Math.max(1, (int) Math.ceil(sellers * 0.1));
        long slowFrom = sellers >= 4 ? sellers - Math.max(1, sellers / 4) : Long.MAX_VALUE;
        int fastDays = Math.max(2, (int) Math.ceil(ctx.period().days() * 0.5));
        BigDecimal days = BigDecimal.valueOf(ctx.period().days());

        List<ProductPerformance> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Row r = sorted.get(i);
            int sellerIndex = r.units().signum() > 0 ? i : -1;
            List<String> labels = new ArrayList<>();
            if (r.units().signum() == 0) labels.add("NO_SALES");
            if (sellerIndex >= 0 && sellerIndex < topCount) labels.add("TOP_SELLER");
            if (sellerIndex >= topCount && sellerIndex >= slowFrom) labels.add("SLOW_MOVING");
            if (r.days() >= fastDays) labels.add("FAST_MOVING");
            Double growth = growth(r.units(), r.prevUnits());
            BigDecimal profit = r.profit();
            out.add(new ProductPerformance(i + 1, r.productId(), r.name(), r.sku(), r.categoryId(), r.categoryName(),
                    r.unit(), r.units(), r.net(), profit, margin(profit, r.net()), r.sales(), r.days(),
                    r.units().divide(days, 2, RoundingMode.HALF_UP), r.available(),
                    StockLevels.status(r.available(), r.minStock()), r.prevUnits(), growth,
                    performance(r.units(), r.prevUnits(), growth), labels, r.lastSoldAt(), r.archived()));
        }
        return out;
    }

    private static InventoryValue inventory(List<Row> rows) {
        BigDecimal atCost = BigDecimal.ZERO, atPrice = BigDecimal.ZERO, potential = BigDecimal.ZERO;
        long noCost = 0, noPrice = 0;
        for (Row r : rows) {
            if (r.archived() || r.available().signum() <= 0) continue;
            BigDecimal cost = r.averageCost() != null ? r.averageCost() : r.purchasePrice();
            if (cost == null) noCost++;
            else atCost = atCost.add(r.available().multiply(cost));
            if (r.salePrice() == null) noPrice++;
            else atPrice = atPrice.add(r.available().multiply(r.salePrice()));
            if (cost != null && r.salePrice() != null) {
                potential = potential.add(r.available().multiply(r.salePrice().subtract(cost)));
            }
        }
        return new InventoryValue(Money.amount(atCost), Money.amount(atPrice), Money.amount(potential), noCost, noPrice);
    }

    private PurchaseTotals purchases(Ctx ctx) {
        Object[] a = (Object[]) bind(em.createNativeQuery(PURCHASE_SQL), ctx).getSingleResult();
        return new PurchaseTotals(((Number) a[0]).longValue(), dec(a[3]), dec(a[1]), dec(a[2]));
    }

    private Map<Long, Instant> lastSold(Long businessId) {
        Map<Long, Instant> map = new HashMap<>();
        for (Object o : em.createNativeQuery(LAST_SOLD_SQL).setParameter("bid", businessId).getResultList()) {
            Object[] a = (Object[]) o;
            map.put(((Number) a[0]).longValue(), toInstant(a[1]));
        }
        return map;
    }

    // ------------------------------------------------------------------ helpers

    static Double growth(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, RoundingMode.HALF_UP).doubleValue();
    }

    static BigDecimal margin(BigDecimal profit, BigDecimal net) {
        if (profit == null || net == null || net.signum() <= 0) return null;
        return profit.multiply(Money.HUNDRED).divide(net, 1, RoundingMode.HALF_UP);
    }

    static Performance performance(BigDecimal current, BigDecimal previous, Double growth) {
        if (growth == null) return current.signum() > 0 ? Performance.NEW : Performance.NO_DATA;
        if (growth > STABLE_BAND) return Performance.GROWING;
        if (growth < -STABLE_BAND) return Performance.DECLINING;
        return Performance.STABLE;
    }

    private static Metric metric(BigDecimal value, BigDecimal previous) {
        return new Metric(value, previous, growth(value, previous));
    }

    private static ProductRef ref(ProductPerformance p) {
        return new ProductRef(p.productId(), p.name(), p.unit(), p.unitsSold(), p.revenue());
    }

    private static double daysOfCover(ProductPerformance p) {
        if (p.averageDailySales().signum() == 0) return Double.MAX_VALUE;
        return p.currentStock().max(BigDecimal.ZERO).divide(p.averageDailySales(), 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String coverText(ProductPerformance p) {
        double cover = daysOfCover(p);
        if (p.currentStock().signum() <= 0 || cover == Double.MAX_VALUE) return "";
        long d = Math.max(1, Math.round(Math.floor(cover)));
        return " (about " + d + (d == 1 ? " day" : " days") + " of stock left)";
    }

    static LocalDate bucketStart(LocalDate d, String g) {
        return switch (g) {
            case "week" -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> d.withDayOfMonth(1);
            default -> d;
        };
    }

    private static LocalDate next(LocalDate d, String g) {
        return switch (g) {
            case "week" -> d.plusWeeks(1);
            case "month" -> d.plusMonths(1);
            default -> d.plusDays(1);
        };
    }

    private static String describe(Period p) {
        if (p.days() == 1) return "on " + p.from();
        return "from " + p.from() + " to " + p.to();
    }

    private static String qty(BigDecimal q, String unit) {
        return q.stripTrailingZeros().toPlainString() + " " + unit;
    }

    private static String pct(Double g) {
        return (g > 0 ? "+" : "") + BigDecimal.valueOf(g).stripTrailingZeros().toPlainString() + "%";
    }

    private static BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal d) return d;
        return new BigDecimal(o.toString());
    }

    private static BigDecimal decOrNull(Object o) {
        return o == null ? null : dec(o);
    }

    private static Instant toInstant(Object o) {
        return switch (o) {
            case null -> null;
            case Instant i -> i;
            case Timestamp t -> t.toInstant();
            case OffsetDateTime odt -> odt.toInstant();
            case ZonedDateTime zdt -> zdt.toInstant();
            case LocalDateTime ldt -> ldt.toInstant(ZoneOffset.UTC);
            default -> throw new IllegalStateException("Unexpected timestamp type " + o.getClass());
        };
    }

    private static LocalDate toDate(Object o) {
        return switch (o) {
            case LocalDate d -> d;
            case java.sql.Date d -> d.toLocalDate();
            case Timestamp t -> t.toLocalDateTime().toLocalDate();
            case LocalDateTime ldt -> ldt.toLocalDate();
            default -> throw new IllegalStateException("Unexpected date type " + o.getClass());
        };
    }
}
