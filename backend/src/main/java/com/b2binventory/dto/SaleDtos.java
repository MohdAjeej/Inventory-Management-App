package com.b2binventory.dto;

import com.b2binventory.dto.ProductDtos.StockStatus;
import com.b2binventory.entity.PaymentKind;
import com.b2binventory.entity.PaymentStatus;
import com.b2binventory.entity.SaleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sales, payments and returns. Prices exclude tax: line net = quantity x unit price - discount, tax is
 * charged on the net, and the line total is net + tax. Cost and profit fields are omitted for members
 * who cannot see costs.
 */
public final class SaleDtos {

    private SaleDtos() {
    }

    public record SaleLineRequest(
            @NotNull(message = "Choose a product") Long variantId,
            Long warehouseId,
            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero") BigDecimal quantity,
            @NotNull(message = "Selling price is required")
            @DecimalMin(value = "0", message = "Selling price cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal unitPrice,
            @DecimalMin(value = "0", message = "Discount cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal discount,
            /* null uses the product's default tax rate */
            @DecimalMin(value = "0", message = "Tax rate cannot be negative")
            @DecimalMax(value = "100", message = "Tax rate cannot be more than 100%")
            @Digits(integer = 3, fraction = 2) BigDecimal taxRate,
            Long batchId,
            @Size(max = 80) String lotNumber,
            List<@NotBlank @Size(max = 120) String> serials) {
    }

    /** Money received with the sale (or paid with a purchase). method: CASH, UPI, CARD, BANK_TRANSFER, CREDIT, OTHER… */
    public record PaymentInput(
            @NotBlank(message = "Payment method is required") @Size(max = 30) String method,
            @NotNull(message = "Amount is required") @DecimalMin(value = "0", message = "Amount cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal amount,
            @Size(max = 100) String reference) {
    }

    public record SaleRequest(
            @NotEmpty(message = "Add at least one product") @Size(max = 200) List<@Valid SaleLineRequest> items,
            Long customerId,
            @Size(max = 200) String customerName,
            @Size(max = 50) String customerPhone,
            /* save the typed customer for next time */
            boolean saveCustomer,
            /* leave empty to number the invoice automatically */
            @Size(max = 40) String invoiceNumber,
            /* defaults to now; cannot be in the future */
            Instant soldAt,
            @Valid PaymentInput payment,
            @Size(max = 1000) String notes,
            @NotNull(message = "Request id is required") UUID clientRequestId) {
    }

    public record CancelSaleRequest(
            @NotBlank(message = "A reason is required") @Size(max = 200) String reason,
            /* how money already received is refunded; defaults to CASH */
            @Size(max = 30) String refundMethod) {
    }

    public record PaymentRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal amount,
            @NotBlank(message = "Payment method is required") @Size(max = 30) String method,
            @Size(max = 100) String reference,
            Instant paidAt,
            @NotNull(message = "Request id is required") UUID clientRequestId) {
    }

    public record ReturnLineRequest(
            @NotNull Long saleItemId,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero") BigDecimal quantity,
            /* resalable items go back into stock; damaged ones do not */
            boolean resalable,
            List<@NotBlank @Size(max = 120) String> serials) {
    }

    public record ReturnRequest(
            @NotEmpty(message = "Choose the items being returned") List<@Valid ReturnLineRequest> items,
            @NotBlank(message = "A reason is required") @Size(max = 200) String reason,
            /* money paid back now; may be less than the value returned */
            @DecimalMin(value = "0", message = "Refund cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal refundAmount,
            @Size(max = 30) String refundMethod,
            @Size(max = 1000) String notes,
            @NotNull(message = "Request id is required") UUID clientRequestId) {
    }

    public record SaleItemResponse(
            Long id, int lineNo, Long productId, String productName, Long variantId, String variantName, String sku,
            String unitCode, Long warehouseId, String warehouseName, String lotNumber, BigDecimal quantity,
            BigDecimal returnedQuantity, BigDecimal unitPrice, BigDecimal grossAmount, BigDecimal discount,
            BigDecimal netAmount, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal lineTotal, BigDecimal unitCost,
            BigDecimal costAmount, BigDecimal grossProfit, List<String> serials) {
    }

    public record PaymentResponse(Long id, PaymentKind kind, BigDecimal amount, String method, String reference,
                                  Instant paidAt, String recordedBy) {
    }

    public record ReturnItemResponse(Long saleItemId, String productName, BigDecimal quantity, boolean resalable,
                                     BigDecimal netAmount, BigDecimal taxAmount) {
    }

    public record ReturnResponse(Long id, String returnNumber, Instant returnedAt, String reason, BigDecimal creditAmount,
                                 BigDecimal refundAmount, String refundMethod, String notes, String recordedBy,
                                 List<ReturnItemResponse> items) {
    }

    /** Product availability after the sale, so the app can show the updated stock straight away. */
    public record StockAfter(Long productId, String productName, String unitCode, BigDecimal available,
                             StockStatus status) {
    }

    public record SaleSummary(
            Long id, String invoiceNumber, Instant soldAt, String customerName, String customerPhone, int itemCount,
            BigDecimal totalQuantity, String products, BigDecimal total, BigDecimal returnedTotal, BigDecimal amountPaid,
            BigDecimal outstanding, PaymentStatus paymentStatus, SaleStatus status) {
    }

    /**
     * netSales = subtotal - discounts - net value of returns (tax excluded). grossProfit = netSales - cost of goods
     * sold (resalable returns put their cost back); null when a line has no known cost.
     */
    public record SaleResponse(
            Long id, String invoiceNumber, SaleStatus status, Instant soldAt, Long customerId, String customerName,
            String customerPhone, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total,
            BigDecimal returnedTotal, BigDecimal amountPaid, BigDecimal outstanding, PaymentStatus paymentStatus,
            BigDecimal netSales, BigDecimal costTotal, BigDecimal grossProfit, String notes, String soldBy,
            String cancelReason, Instant cancelledAt, String cancelledBy, Instant createdAt,
            List<SaleItemResponse> items, List<PaymentResponse> payments, List<ReturnResponse> returns,
            List<StockAfter> stock) {
    }
}
