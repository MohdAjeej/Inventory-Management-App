package com.b2binventory.dto;

import com.b2binventory.entity.ConnectionStatus;
import com.b2binventory.entity.TradeInvoiceStatus;
import com.b2binventory.entity.TradeReturnStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Business network (connections between businesses) and trade invoices sent between them. */
public final class TradeDtos {

    private TradeDtos() {
    }

    // ------------------------------------------------------------------ businesses and connections

    /** The minimum needed to recognise a business in search results and lists. */
    public record BusinessCard(Long id, String name, String businessType, String city, String businessCode) {
    }

    /** Contact details printed on an invoice; shown only to the two businesses on that invoice. */
    public record PartyDetails(Long id, String name, String businessType, String businessCode, String phone, String email,
                               String address, String city, String state, String postalCode, String taxId) {
    }

    /** direction: INCOMING when the other business asked, OUTGOING when this business asked. */
    public record ConnectionResponse(Long id, ConnectionStatus status, String direction, BusinessCard business,
                                     String message, Instant createdAt, Instant respondedAt) {
    }

    public record SearchResult(BusinessCard business, Long connectionId, ConnectionStatus connectionStatus,
                               String direction) {
    }

    public record ConnectionRequest(@NotNull(message = "Choose a business") Long businessId,
                                    @Size(max = 300) String message) {
    }

    public record MyBusinessCode(String businessCode, String name, boolean discoverable) {
    }

    public record DiscoverableRequest(boolean discoverable) {
    }

    // ------------------------------------------------------------------ invoices: requests

    public record TradeLineRequest(
            @NotNull(message = "Choose a product") Long variantId,
            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero") BigDecimal quantity,
            /* null uses the product's selling price */
            @DecimalMin(value = "0", message = "Price cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal unitPrice,
            @DecimalMin(value = "0", message = "Discount cannot be negative")
            @Digits(integer = 16, fraction = 2, message = "Use at most 2 decimal places") BigDecimal discount,
            /* null uses the product's tax rate */
            @DecimalMin(value = "0", message = "Tax rate cannot be negative")
            @DecimalMax(value = "100", message = "Tax rate cannot be more than 100%")
            @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {
    }

    public record TradeInvoiceRequest(
            @NotNull(message = "Choose the buyer") Long buyerBusinessId,
            Long warehouseId,
            LocalDate invoiceDate,
            @Size(max = 1000) String notes,
            @Size(max = 1000) String deliveryNotes,
            @NotEmpty(message = "Add at least one product") @Size(max = 200) List<@Valid TradeLineRequest> lines,
            /* true sends it to the buyer straight away; false keeps it as a draft */
            boolean send,
            UUID clientRequestId) {
    }

    public record ReasonRequest(@NotBlank(message = "A reason is required") @Size(max = 300) String reason) {
    }

    /** For each line: receive into an existing product (variantId) or create a new product (createNew). */
    public record LineChoice(@NotNull Long lineId, Long variantId, boolean createNew) {
    }

    public record ApproveRequest(Long warehouseId, List<@Valid LineChoice> lines) {
    }

    public record ReturnLineRequest(@NotNull Long invoiceLineId,
                                    @NotNull @DecimalMin(value = "0", inclusive = false,
                                            message = "Quantity must be greater than zero") BigDecimal quantity) {
    }

    public record TradeReturnRequest(
            @NotEmpty(message = "Choose the items being returned") List<@Valid ReturnLineRequest> lines,
            @NotBlank(message = "A reason is required") @Size(max = 300) String reason,
            @Size(max = 1000) String notes,
            UUID clientRequestId) {
    }

    public record RespondRequest(@Size(max = 300) String note) {
    }

    // ------------------------------------------------------------------ invoices: responses

    public record TradeLineResponse(
            Long id, int lineNo, String productName, String variantName, boolean defaultVariant, String sku,
            String barcode, String brand, String model, String categoryName, String description,
            Map<String, Object> attributes, Map<String, Object> variantAttributes, String unitCode, String unitName,
            int unitDecimals, BigDecimal quantity, BigDecimal unitPrice, BigDecimal grossAmount, BigDecimal discount,
            BigDecimal netAmount, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal lineTotal,
            /* the seller's own product (seller only) and the product it was received into (buyer only) */
            Long sellerVariantId, Long buyerVariantId, BigDecimal returnedQuantity,
            /* the buyer's product the line was received into (buyer only) */
            Long buyerProductId) {
    }

    public record TradeReturnLineResponse(Long invoiceLineId, String productName, String variantName, String unitCode,
                                          BigDecimal quantity, BigDecimal amount) {
    }

    public record TradeReturnResponse(Long id, String returnNumber, TradeReturnStatus status, String reason, String notes,
                                      BigDecimal total, Instant requestedAt, Instant respondedAt, String responseNote,
                                      List<TradeReturnLineResponse> lines) {
    }

    public record TradeEventResponse(String action, Long businessId, String businessName, String actorName, String note,
                                     Instant at) {
    }

    /** role: SELLER or BUYER, from the point of view of the business asking. */
    public record TradeInvoiceResponse(
            Long id, String invoiceNumber, TradeInvoiceStatus status, String role, PartyDetails seller,
            PartyDetails buyer, LocalDate invoiceDate, String notes, String deliveryNotes, String warehouseName,
            List<TradeLineResponse> lines, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal,
            BigDecimal total, String currencyCode, Instant createdAt, Instant sentAt, Instant respondedAt,
            String rejectionReason, String cancelReason, Long saleId, Long purchaseId,
            List<TradeReturnResponse> returns, List<TradeEventResponse> events) {
    }

    public record TradeInvoiceSummary(
            Long id, String invoiceNumber, TradeInvoiceStatus status, String role, BusinessCard counterparty,
            LocalDate invoiceDate, int lineCount, BigDecimal total, String currencyCode, Instant createdAt,
            Instant sentAt, Instant respondedAt, boolean returnPending) {
    }

    // ------------------------------------------------------------------ receiving (buyer side)

    public record Candidate(Long variantId, Long productId, String productName, String variantName, String unitCode,
                            BigDecimal available) {
    }

    /**
     * How a line will be received: MAPPED (saved link), MATCHED (same barcode or name), NEW (a product will be
     * created) or REVIEW (several possible products; the buyer must choose).
     */
    public record LineProposal(Long lineId, String mode, Candidate target, List<Candidate> candidates) {
    }

    public record ReceivePreview(Long invoiceId, Long warehouseId, String warehouseName, List<LineProposal> lines) {
    }

    public record ProductMappingResponse(Long id, BusinessCard seller, Long sellerVariantId, String sellerProductName,
                                         Long buyerVariantId, Long buyerProductId, String buyerProductName,
                                         String buyerVariantName, Instant updatedAt) {
    }

    /** Counts for the dashboard card. */
    public record TradeSummary(long pendingApprovals, long awaitingBuyer, long drafts, long incomingConnections,
                               long returnsToReview, long connectedBusinesses) {
    }
}
