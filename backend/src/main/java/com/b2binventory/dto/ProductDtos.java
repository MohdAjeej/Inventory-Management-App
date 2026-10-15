package com.b2binventory.dto;

import com.b2binventory.dto.CatalogDtos.UnitResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ProductDtos {

    private ProductDtos() {
    }

    public enum StockStatus { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

    public record OpeningStock(
            @NotNull @DecimalMin(value = "0", message = "Quantity cannot be negative") BigDecimal quantity,
            Long warehouseId,
            @Size(max = 80) String lotNumber,
            LocalDate expiryDate,
            List<@NotBlank @Size(max = 120) String> serials) {
    }

    public record ProductRequest(
            @NotBlank(message = "Product name is required") @Size(max = 200) String name,
            Long categoryId,
            Long baseUnitId,
            @Size(max = 120) String brand,
            @Size(max = 120) String model,
            @Size(max = 2000) String description,
            @Min(value = 0, message = "Minimum stock cannot be negative") Integer minStock,
            Map<String, Object> attributes,
            @Size(max = 80) String sku,
            @Size(max = 80) String barcode,
            boolean trackBatches,
            boolean trackSerials,
            @Valid OpeningStock openingStock,
            @DecimalMin(value = "0", message = "Selling price cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal salePrice,
            @DecimalMin(value = "0", message = "Purchase price cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal purchasePrice,
            @DecimalMin(value = "0", message = "Tax rate cannot be negative")
            @DecimalMax(value = "100", message = "Tax rate cannot be more than 100%")
            @Digits(integer = 3, fraction = 2) BigDecimal taxRate,
            Long supplierId) {
    }

    public record VariantRequest(
            @NotBlank(message = "Variant name is required") @Size(max = 200) String name,
            @Size(max = 80) String sku,
            @Size(max = 80) String barcode,
            Map<String, Object> attributes,
            @Min(value = 0, message = "Minimum stock cannot be negative") Integer minStock) {
    }

    public record StockTotals(BigDecimal onHand, BigDecimal reserved, BigDecimal damaged, BigDecimal available) {
        public static final StockTotals ZERO = new StockTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public record VariantResponse(Long id, String name, String sku, String barcode, Map<String, Object> attributes,
                                  Integer minStock, int effectiveMinStock, boolean isDefault, boolean archived,
                                  StockTotals stock, StockStatus status) {
    }

    public record ImageResponse(Long id, String url) {
    }

    public record BalanceResponse(Long warehouseId, String warehouseName, Long variantId, String variantName,
                                  Long batchId, String lotNumber, LocalDate expiryDate, BigDecimal onHand,
                                  BigDecimal reserved, BigDecimal damaged, BigDecimal available) {
    }

    /**
     * Price calculations for the product page. unitCost is the weighted-average cost (costSource AVERAGE) or,
     * before any purchase, the default purchase price (PURCHASE_PRICE). Stock figures use available stock.
     * potentialProfit assumes all available stock sells at the selling price, before other costs.
     * Null fields mean the inputs are missing; the whole object is omitted for members who cannot see costs.
     */
    public record Pricing(BigDecimal unitCost, String costSource, BigDecimal profitPerUnit, BigDecimal marginPercent,
                          BigDecimal stockValueAtCost, BigDecimal stockValueAtSellingPrice, BigDecimal potentialProfit) {
    }

    public record ProductSummary(Long id, String name, String brand, String model, Long categoryId, String categoryName,
                                 String unitCode, String sku, String barcode, String imageUrl, StockTotals stock,
                                 int effectiveMinStock, StockStatus status, int variantCount, boolean archived,
                                 Instant updatedAt, BigDecimal salePrice, BigDecimal purchasePrice) {
    }

    public record ProductDetail(Long id, String name, String brand, String model, String description,
                                Long categoryId, String categoryName, String categoryPath, UnitResponse baseUnit,
                                Integer minStock, int effectiveMinStock, Map<String, Object> attributes,
                                boolean trackBatches, boolean trackSerials, boolean archived, StockTotals stock,
                                StockStatus status, List<VariantResponse> variants, List<ImageResponse> images,
                                List<BalanceResponse> balances, Instant createdAt, Instant updatedAt,
                                BigDecimal salePrice, BigDecimal purchasePrice, BigDecimal averageCost,
                                BigDecimal taxRate, Long supplierId, String supplierName, Pricing pricing) {
    }
}
