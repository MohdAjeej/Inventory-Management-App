package com.b2binventory.dto;

import com.b2binventory.dto.ProductDtos.StockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sales analytics, built from COMPLETED sales only. Returns are counted against the period of the original sale.
 * "Revenue" is net sales: gross sales - discounts - returns, excluding tax. Gross profit = net sales - cost of goods
 * sold, using the cost saved on each sale line; it is null ("unavailable") when any line in scope has no cost.
 * Dates are inclusive and in the business timezone. A null growthPercent means there is nothing to compare with.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public enum Performance { GROWING, STABLE, DECLINING, NEW, NO_DATA }

    public record Period(LocalDate from, LocalDate to, int days, LocalDate previousFrom, LocalDate previousTo,
                         String timezone, String currencyCode) {
    }

    public record Metric(BigDecimal value, BigDecimal previous, Double growthPercent) {
    }

    public record ProductRef(Long productId, String name, String unit, BigDecimal units, BigDecimal revenue) {
    }

    public record Financials(BigDecimal grossSales, BigDecimal discounts, BigDecimal returns, BigDecimal netSales,
                             BigDecimal taxCollected, BigDecimal costOfGoodsSold, BigDecimal grossProfit,
                             BigDecimal marginPercent, boolean profitAvailable, long linesWithoutCost) {
    }

    /** Current stock valued at cost and at selling price (available stock only). */
    public record InventoryValue(BigDecimal valueAtCost, BigDecimal valueAtSellingPrice, BigDecimal potentialProfit,
                                 long productsWithoutCost, long productsWithoutPrice) {
    }

    public record PurchaseTotals(long purchases, BigDecimal unitsReceived, BigDecimal totalSpent,
                                 BigDecimal previousTotalSpent) {
    }

    public record Overview(Period period, Metric sales, Metric units, Metric revenue, Metric grossProfit,
                           Financials financials, BigDecimal averageDailyUnits, BigDecimal averageDailyRevenue,
                           ProductRef bestSeller, ProductRef lowestSeller, long productsWithNoSales, long productsSold,
                           long activeProducts, InventoryValue inventory, PurchaseTotals purchases) {
    }

    public record ProductPerformance(int rank, Long productId, String name, String sku, Long categoryId,
                                     String categoryName, String unit, BigDecimal unitsSold, BigDecimal revenue,
                                     BigDecimal grossProfit, BigDecimal marginPercent, long salesCount,
                                     int daysWithSales, BigDecimal averageDailySales, BigDecimal currentStock,
                                     StockStatus stockStatus, BigDecimal previousUnits, Double growthPercent,
                                     Performance performance, List<String> labels, Instant lastSoldAt,
                                     boolean archived) {
    }

    public record ProductAnalytics(Period period, List<ProductPerformance> products) {
    }

    public record TrendPoint(LocalDate start, BigDecimal units, BigDecimal revenue, BigDecimal grossProfit, long sales) {
    }

    public record Trend(Period period, String granularity, List<TrendPoint> points) {
    }

    public record CategoryPerformance(Long categoryId, String categoryName, BigDecimal units, BigDecimal revenue,
                                      BigDecimal grossProfit, long productsSold, BigDecimal previousUnits,
                                      Double growthPercent) {
    }

    public record CategoryAnalytics(Period period, List<CategoryPerformance> categories) {
    }

    /** severity: GOOD, INFO or WARNING. action: ADD_STOCK, VIEW_PRODUCT or null. */
    public record Insight(String kind, String severity, String title, String message, Long productId, String action) {
    }

    public record Insights(Period period, List<Insight> insights) {
    }

    /** Small summary for the home screen. grossProfit is null when unavailable. */
    public record SalesSnapshot(long sales, BigDecimal units, BigDecimal revenue, BigDecimal grossProfit,
                                String currencyCode) {
    }
}
