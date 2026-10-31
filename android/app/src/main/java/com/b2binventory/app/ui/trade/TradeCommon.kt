package com.b2binventory.app.ui.trade

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.b2binventory.app.data.remote.PartyDetails
import com.b2binventory.app.data.remote.TradeInvoice
import com.b2binventory.app.ui.common.AmountWords
import com.b2binventory.app.ui.common.Fmt
import com.b2binventory.app.ui.common.InvoicePdf
import com.b2binventory.app.ui.common.InvoicePdfData
import com.b2binventory.app.ui.common.InvoiceRow
import com.b2binventory.app.ui.common.InvoiceTotal
import com.b2binventory.app.ui.common.Pill
import com.b2binventory.app.ui.common.tr
import com.b2binventory.app.ui.theme.appColors
import kotlinx.serialization.json.JsonElement

/** Invoice statuses in plain words. */
fun tradeStatusLabel(status: String, seller: Boolean): String = when (status) {
    "DRAFT" -> "Draft"
    "PENDING_APPROVAL" -> if (seller) "Waiting for buyer" else "Needs your approval"
    "POSTED" -> "Approved"
    "REJECTED" -> "Rejected"
    "CANCELLED" -> "Cancelled"
    else -> status
}

fun returnStatusLabel(status: String): String = when (status) {
    "REQUESTED" -> "Return requested"
    "ACCEPTED" -> "Return accepted"
    "REJECTED" -> "Return rejected"
    "CANCELLED" -> "Return withdrawn"
    else -> status
}

fun tradeEventLabel(action: String): String = when (action) {
    "CREATED" -> "Invoice created"
    "EDITED" -> "Draft edited"
    "SENT" -> "Sent to buyer"
    "APPROVED" -> "Approved · stock updated"
    "REJECTED" -> "Rejected"
    "CANCELLED" -> "Cancelled"
    "RETURN_REQUESTED" -> "Return requested"
    "RETURN_ACCEPTED" -> "Return accepted · stock moved back"
    "RETURN_REJECTED" -> "Return rejected"
    "RETURN_CANCELLED" -> "Return withdrawn"
    else -> action
}

@Composable
fun tradeStatusColor(status: String): Color {
    val c = MaterialTheme.appColors
    return when (status) {
        "PENDING_APPROVAL", "REQUESTED" -> c.warning
        "POSTED", "ACCEPTED" -> c.success
        "REJECTED" -> c.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

fun tradeStatusIcon(status: String): ImageVector = when (status) {
    "DRAFT" -> Icons.Default.EditNote
    "PENDING_APPROVAL", "REQUESTED" -> Icons.Default.HourglassTop
    "POSTED", "ACCEPTED" -> Icons.Default.CheckCircle
    "REJECTED" -> Icons.Default.Block
    else -> Icons.Default.Cancel
}

@Composable
fun TradeStatusPill(status: String, seller: Boolean) {
    Pill(tradeStatusLabel(status, seller), tradeStatusColor(status), tradeStatusIcon(status))
}

/** "Size: 8 x 4 feet · Thickness: 1 mm" from the product and variant specifications. */
fun specsText(attributes: Map<String, JsonElement>, variantAttributes: Map<String, JsonElement>): String? =
    (attributes + variantAttributes).entries.joinToString(" · ") { "${it.key}: ${Fmt.attribute(it.value)}" }.ifEmpty { null }

/** Draws an A4 business invoice (seller, buyer, lines with size and unit, totals) and returns a shareable Uri. */
object TradeInvoicePdf {

    fun create(context: Context, inv: TradeInvoice): Uri {
        fun money(v: Double) = Fmt.money(v, inv.currencyCode)
        fun address(p: PartyDetails) = listOfNotNull(p.address, p.city, p.state, p.postalCode)
            .filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
        val taxLabel = if (inv.currencyCode == "INR") "GSTIN" else "Tax ID"
        val units = inv.lines.map { it.unitCode }.distinct()

        val rows = inv.lines.map { l ->
            InvoiceRow(
                name = l.label,
                detail = listOfNotNull(l.sku?.let { "SKU $it" }, specsText(l.attributes, l.variantAttributes),
                    if (l.returnedQuantity > 0) "Returned ${Fmt.qty(l.returnedQuantity)} ${l.unitCode}" else null)
                    .joinToString(" · ").ifEmpty { null },
                qty = "${Fmt.qty(l.quantity)} ${l.unitCode}",
                rate = Fmt.qty(l.unitPrice),
                discount = if (l.discount > 0) Fmt.qty(l.discount) else null,
                tax = if (l.taxAmount > 0) "${Fmt.qty(l.taxRate)}%" else null,
                total = Fmt.qty(l.lineTotal),
            )
        }
        val totals = buildList {
            if (inv.discountTotal > 0 || inv.taxTotal > 0) add(InvoiceTotal("Gross amount", money(inv.subtotal)))
            if (inv.discountTotal > 0) add(InvoiceTotal("Discount", "- " + money(inv.discountTotal)))
            if (inv.taxTotal > 0) add(InvoiceTotal("Tax", money(inv.taxTotal)))
            add(InvoiceTotal("Total Amount", money(inv.total), strong = true))
        }
        val notes = listOfNotNull(
            inv.notes?.let { "Notes" to it },
            inv.deliveryNotes?.let { "Delivery" to it },
            inv.cancelReason?.let { "Cancelled" to it },
            inv.rejectionReason?.let { "Rejected" to it },
        )

        return InvoicePdf.create(context, InvoicePdfData(
            fileName = "invoice-${inv.invoiceNumber}.pdf",
            title = if (inv.taxTotal > 0) "TAX INVOICE" else "BILL OF SUPPLY",
            copyLabel = when (inv.status) {
                "CANCELLED" -> "CANCELLED"
                "REJECTED" -> "REJECTED"
                "DRAFT" -> "DRAFT"
                else -> "ORIGINAL FOR RECIPIENT"
            },
            sellerName = inv.seller.name,
            sellerTaxId = inv.seller.taxId?.let { taxLabel to it },
            sellerPhone = inv.seller.phone,
            sellerAddress = address(inv.seller),
            meta = listOf("Invoice No." to inv.invoiceNumber, "Invoice Date" to Fmt.date(inv.invoiceDate),
                "Status" to tr(tradeStatusLabel(inv.status, true))),
            billToName = inv.buyer.name,
            billToLines = listOfNotNull(
                inv.buyer.phone?.let { "Mobile" to it },
                inv.buyer.taxId?.let { taxLabel to it },
                address(inv.buyer)?.let { null to it },
                inv.buyer.state?.let { "Place of Supply" to it },
            ),
            sideTitle = inv.warehouseName?.let { "Dispatch From" },
            sideLines = listOfNotNull(inv.warehouseName?.let { null to it }),
            rows = rows,
            qtyTotal = if (units.size == 1) "${Fmt.qty(inv.lines.sumOf { it.quantity })} ${units.first()}" else null,
            subtotal = money(inv.total),
            totals = totals,
            amountInWords = AmountWords.of(inv.total, inv.currencyCode),
            notes = notes,
        ))
    }
}
