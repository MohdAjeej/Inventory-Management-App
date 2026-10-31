package com.b2binventory.dto;

import com.b2binventory.dto.AnalyticsDtos.InventoryValue;
import com.b2binventory.dto.AnalyticsDtos.Metric;
import com.b2binventory.dto.AnalyticsDtos.Period;
import com.b2binventory.dto.AnalyticsDtos.ProductRef;
import com.b2binventory.dto.AnalyticsDtos.SalesSnapshot;
import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.dto.StockDtos.MovementResponse;
import com.b2binventory.entity.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record InventoryRow(Long productId, Long variantId, String productName, String variantName, String sku,
                               String barcode, String category, String unit, BigDecimal onHand, BigDecimal reserved,
                               BigDecimal damaged, BigDecimal available, int minStock, StockStatus status) {
    }

    public record StockAlert(Long productId, String productName, String unit, BigDecimal available, int minStock,
                             StockStatus status) {
    }

    public record CategorySummary(Long categoryId, String categoryName, long productCount, BigDecimal quantity) {
    }

    public record WarehouseSummary(Long warehouseId, String warehouseName, long productCount, BigDecimal quantity) {
    }

    public record RecentProduct(Long productId, String productName, String unit, BigDecimal available,
                                StockStatus status, Instant updatedAt) {
    }

    /**
     * Sales figures for the home screen; only sent to members with ANALYTICS_VIEW. "This month" runs from the
     * first of the month to today in the business timezone.
     */
    /** monthRevenue and monthGrossProfit compare with the previous period of the same length (growth is null without one). */
    public record SalesHighlights(SalesSnapshot today, SalesSnapshot month, ProductRef monthBestSeller,
                                  ProductRef monthLowestSeller, long monthProductsWithNoSales, InventoryValue inventory,
                                  Period monthPeriod, Metric monthRevenue, Metric monthGrossProfit) {
    }

    public record DashboardResponse(
            long activeProducts,
            BigDecimal totalQuantity,
            long lowStockCount,
            long outOfStockCount,
            List<StockAlert> lowStockItems,
            List<StockAlert> outOfStockItems,
            List<MovementResponse> recentMovements,
            List<CategorySummary> categories,
            List<WarehouseSummary> warehouses,
            long unreadNotifications,
            List<RecentProduct> recentlyUpdated,
            SalesHighlights sales,
            /* products with the most available stock, highest first */
            List<RecentProduct> mostStocked) {
    }

    /** Stock movement totals for a period, with the products that move fastest and slowest. */
    public record MovementSummary(Instant from, Instant to, BigDecimal totalIn, BigDecimal totalOut, long movementCount,
                                  List<TypeTotal> byType, List<ProductMovement> fastMoving,
                                  List<ProductMovement> slowMoving) {
    }

    public record TypeTotal(MovementType type, long count, BigDecimal quantity) {
    }

    /** quantityOut is what left stock in the period (reductions, sales, corrections down); available is current. */
    public record ProductMovement(Long productId, String productName, String unit, BigDecimal quantityOut,
                                  BigDecimal available) {
    }
}
