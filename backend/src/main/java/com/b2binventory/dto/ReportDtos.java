package com.b2binventory.dto;

import com.b2binventory.dto.AnalyticsDtos.InventoryValue;
import com.b2binventory.dto.AnalyticsDtos.ProductRef;
import com.b2binventory.dto.AnalyticsDtos.SalesSnapshot;
import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.dto.StockDtos.MovementResponse;

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
    public record SalesHighlights(SalesSnapshot today, SalesSnapshot month, ProductRef monthBestSeller,
                                  ProductRef monthLowestSeller, long monthProductsWithNoSales, InventoryValue inventory) {
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
            SalesHighlights sales) {
    }
}
