package com.b2binventory.dto;

import com.b2binventory.dto.SaleDtos.PaymentInput;
import com.b2binventory.dto.SaleDtos.PaymentResponse;
import com.b2binventory.entity.PaymentStatus;
import com.b2binventory.entity.PurchaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Goods received from suppliers. Unit costs exclude tax; the net cost per unit feeds the weighted-average cost. */
public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record PurchaseLineRequest(
            @NotNull(message = "Choose a product") Long variantId,
            Long warehouseId,
            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero") BigDecimal quantity,
            @NotNull(message = "Purchase price is required")
            @DecimalMin(value = "0", message = "Purchase price cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal unitCost,
            @DecimalMin(value = "0", message = "Discount cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal discount,
            @DecimalMin(value = "0") @DecimalMax(value = "100") @Digits(integer = 3, fraction = 2) BigDecimal taxRate,
            @Size(max = 80) String lotNumber,
            LocalDate expiryDate,
            List<@NotBlank @Size(max = 120) String> serials) {
    }

    public record PurchaseRequest(
            @NotEmpty(message = "Add at least one product") @Size(max = 200) List<@Valid PurchaseLineRequest> items,
            Long supplierId,
            @Size(max = 200) String supplierName,
            boolean saveSupplier,
            @Size(max = 80) String referenceNumber,
            Instant purchasedAt,
            @Valid PaymentInput payment,
            @Size(max = 1000) String notes,
            @NotNull(message = "Request id is required") UUID clientRequestId) {
    }

    public record CancelPurchaseRequest(@NotBlank(message = "A reason is required") @Size(max = 200) String reason) {
    }

    public record PurchaseItemResponse(
            Long id, int lineNo, Long productId, String productName, Long variantId, String variantName, String sku,
            String unitCode, Long warehouseId, String warehouseName, String lotNumber, LocalDate expiryDate,
            BigDecimal quantity, BigDecimal unitCost, BigDecimal grossAmount, BigDecimal discount, BigDecimal netAmount,
            BigDecimal taxRate, BigDecimal taxAmount, BigDecimal lineTotal, BigDecimal landedUnitCost) {
    }

    public record PurchaseSummary(
            Long id, String purchaseNumber, String referenceNumber, Instant purchasedAt, String supplierName,
            int itemCount, BigDecimal totalQuantity, String products, BigDecimal total, BigDecimal amountPaid,
            BigDecimal outstanding, PaymentStatus paymentStatus, PurchaseStatus status) {
    }

    public record PurchaseResponse(
            Long id, String purchaseNumber, String referenceNumber, PurchaseStatus status, Instant purchasedAt,
            Long supplierId, String supplierName, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal,
            BigDecimal total, BigDecimal amountPaid, BigDecimal outstanding, PaymentStatus paymentStatus, String notes,
            String recordedBy, String cancelReason, Instant cancelledAt, String cancelledBy, Instant createdAt,
            List<PurchaseItemResponse> items, List<PaymentResponse> payments) {
    }
}
