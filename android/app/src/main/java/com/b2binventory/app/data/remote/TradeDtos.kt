package com.b2binventory.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Business network

@Serializable
data class BusinessCard(
    val id: Long,
    val name: String,
    val businessType: String? = null,
    val city: String? = null,
    val businessCode: String? = null,
)

@Serializable
data class PartyDetails(
    val id: Long,
    val name: String,
    val businessType: String? = null,
    val businessCode: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val taxId: String? = null,
)

/** direction: INCOMING (they asked) or OUTGOING (we asked). status: PENDING, ACCEPTED, REJECTED, REMOVED. */
@Serializable
data class Connection(
    val id: Long,
    val status: String,
    val direction: String,
    val business: BusinessCard,
    val message: String? = null,
    val createdAt: String? = null,
    val respondedAt: String? = null,
)

@Serializable
data class BusinessSearchResult(
    val business: BusinessCard,
    val connectionId: Long? = null,
    val connectionStatus: String? = null,
    val direction: String? = null,
)

@Serializable
data class ConnectionRequestBody(val businessId: Long, val message: String? = null)

@Serializable
data class MyBusinessCode(val businessCode: String? = null, val name: String = "", val discoverable: Boolean = true)

@Serializable
data class DiscoverableBody(val discoverable: Boolean)

// ---- Trade invoices

@Serializable
data class TradeLineBody(
    val variantId: Long,
    val quantity: Double,
    val unitPrice: Double? = null,
    val discount: Double? = null,
    val taxRate: Double? = null,
)

@Serializable
data class TradeInvoiceBody(
    val buyerBusinessId: Long,
    val warehouseId: Long? = null,
    val invoiceDate: String? = null,
    val notes: String? = null,
    val deliveryNotes: String? = null,
    val lines: List<TradeLineBody>,
    val send: Boolean,
    val clientRequestId: String? = null,
)

@Serializable
data class ReasonBody(val reason: String)

@Serializable
data class LineChoiceBody(val lineId: Long, val variantId: Long? = null, val createNew: Boolean = false)

@Serializable
data class ApproveBody(val warehouseId: Long? = null, val lines: List<LineChoiceBody> = emptyList())

@Serializable
data class TradeReturnLineBody(val invoiceLineId: Long, val quantity: Double)

@Serializable
data class TradeReturnBody(val lines: List<TradeReturnLineBody>, val reason: String, val notes: String? = null,
                           val clientRequestId: String? = null)

@Serializable
data class RespondBody(val note: String? = null)

@Serializable
data class TradeLine(
    val id: Long,
    val lineNo: Int,
    val productName: String,
    val variantName: String,
    val defaultVariant: Boolean = true,
    val sku: String? = null,
    val barcode: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val categoryName: String? = null,
    val description: String? = null,
    val attributes: Map<String, JsonElement> = emptyMap(),
    val variantAttributes: Map<String, JsonElement> = emptyMap(),
    val unitCode: String,
    val unitName: String = "",
    val unitDecimals: Int = 0,
    val quantity: Double,
    val unitPrice: Double,
    val grossAmount: Double = 0.0,
    val discount: Double = 0.0,
    val netAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val lineTotal: Double = 0.0,
    val sellerVariantId: Long? = null,
    val buyerVariantId: Long? = null,
    val returnedQuantity: Double = 0.0,
    val buyerProductId: Long? = null,
) {
    /** "Mica Sheet · 8x4 ft · 1 mm" for variants, the product name otherwise. */
    val label: String get() = if (defaultVariant || variantName == productName) productName else "$productName · $variantName"
}

@Serializable
data class TradeReturnLine(val invoiceLineId: Long, val productName: String, val variantName: String = "",
                           val unitCode: String = "", val quantity: Double, val amount: Double = 0.0)

@Serializable
data class TradeReturn(
    val id: Long,
    val returnNumber: String,
    val status: String,
    val reason: String,
    val notes: String? = null,
    val total: Double = 0.0,
    val requestedAt: String? = null,
    val respondedAt: String? = null,
    val responseNote: String? = null,
    val lines: List<TradeReturnLine> = emptyList(),
)

@Serializable
data class TradeEvent(val action: String, val businessId: Long, val businessName: String? = null,
                      val actorName: String? = null, val note: String? = null, val at: String)

/** role: SELLER or BUYER for the signed-in business. */
@Serializable
data class TradeInvoice(
    val id: Long,
    val invoiceNumber: String,
    val status: String,
    val role: String,
    val seller: PartyDetails,
    val buyer: PartyDetails,
    val invoiceDate: String,
    val notes: String? = null,
    val deliveryNotes: String? = null,
    val warehouseName: String? = null,
    val lines: List<TradeLine> = emptyList(),
    val subtotal: Double = 0.0,
    val discountTotal: Double = 0.0,
    val taxTotal: Double = 0.0,
    val total: Double = 0.0,
    val currencyCode: String = "INR",
    val createdAt: String? = null,
    val sentAt: String? = null,
    val respondedAt: String? = null,
    val rejectionReason: String? = null,
    val cancelReason: String? = null,
    val saleId: Long? = null,
    val purchaseId: Long? = null,
    val returns: List<TradeReturn> = emptyList(),
    val events: List<TradeEvent> = emptyList(),
) {
    val isSeller: Boolean get() = role == "SELLER"
    val counterparty: PartyDetails get() = if (isSeller) buyer else seller
}

@Serializable
data class TradeInvoiceSummary(
    val id: Long,
    val invoiceNumber: String,
    val status: String,
    val role: String,
    val counterparty: BusinessCard,
    val invoiceDate: String,
    val lineCount: Int = 0,
    val total: Double = 0.0,
    val currencyCode: String = "INR",
    val createdAt: String? = null,
    val sentAt: String? = null,
    val respondedAt: String? = null,
    val returnPending: Boolean = false,
)

@Serializable
data class ReceiveCandidate(
    val variantId: Long,
    val productId: Long,
    val productName: String,
    val variantName: String? = null,
    val unitCode: String = "",
    val available: Double = 0.0,
) {
    val label: String get() = if (variantName == null) productName else "$productName · $variantName"
}

/** mode: MAPPED, MATCHED, NEW, REVIEW or RECEIVED. */
@Serializable
data class LineProposal(val lineId: Long, val mode: String, val target: ReceiveCandidate? = null,
                        val candidates: List<ReceiveCandidate> = emptyList())

@Serializable
data class ReceivePreview(val invoiceId: Long, val warehouseId: Long? = null, val warehouseName: String? = null,
                          val lines: List<LineProposal> = emptyList())

@Serializable
data class ProductLink(
    val id: Long,
    val seller: BusinessCard,
    val sellerVariantId: Long,
    val sellerProductName: String? = null,
    val buyerVariantId: Long,
    val buyerProductId: Long,
    val buyerProductName: String,
    val buyerVariantName: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class TradeSummary(
    val pendingApprovals: Long = 0,
    val awaitingBuyer: Long = 0,
    val drafts: Long = 0,
    val incomingConnections: Long = 0,
    val returnsToReview: Long = 0,
    val connectedBusinesses: Long = 0,
)
