package com.b2binventory.app.ui.trade

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import com.b2binventory.app.data.remote.TradeInvoice
import com.b2binventory.app.ui.common.Fmt
import com.b2binventory.app.ui.common.Pill
import com.b2binventory.app.ui.common.tr
import com.b2binventory.app.ui.theme.appColors
import kotlinx.serialization.json.JsonElement
import java.io.File

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

    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40f

    fun create(context: Context, inv: TradeInvoice): Uri {
        val doc = PdfDocument()
        val text = Paint().apply { textSize = 9.5f; isAntiAlias = true }
        val small = Paint(text).apply { textSize = 8f }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 18f }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNo).create())
        var canvas = page.canvas
        var y = MARGIN + 10

        fun newPageIfNeeded(space: Float) {
            if (y + space > HEIGHT - MARGIN) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNo).create())
                canvas = page.canvas
                y = MARGIN + 10
            }
        }

        fun right(s: String, paint: Paint) = canvas.drawText(s, WIDTH - MARGIN - paint.measureText(s), y, paint)
        fun money(v: Double) = Fmt.money(v, inv.currencyCode)
        fun clip(s: String, max: Int) = if (s.length > max) s.take(max - 1) + "…" else s

        canvas.drawText(inv.seller.name, MARGIN, y, title)
        right(tr("TAX INVOICE"), bold); y += 16f
        listOfNotNull(inv.seller.address, listOfNotNull(inv.seller.city, inv.seller.state, inv.seller.postalCode).joinToString(", ")
            .ifEmpty { null }, inv.seller.phone?.let { tr("Phone: $it") }, inv.seller.taxId?.let { tr("Tax id $it") })
            .forEach { canvas.drawText(it, MARGIN, y, text); y += 13f }
        y += 6f
        canvas.drawText(tr("Invoice: ${inv.invoiceNumber}"), MARGIN, y, bold)
        right(tr("Date: ${Fmt.date(inv.invoiceDate)}"), text); y += 14f
        canvas.drawText(tr(tradeStatusLabel(inv.status, true)), MARGIN, y, text); y += 20f

        canvas.drawText(tr("Bill to"), MARGIN, y, bold); y += 14f
        canvas.drawText(inv.buyer.name, MARGIN, y, text); y += 13f
        listOfNotNull(inv.buyer.address, inv.buyer.city, inv.buyer.phone?.let { tr("Phone: $it") },
            inv.buyer.taxId?.let { tr("Tax id $it") }).forEach { canvas.drawText(it, MARGIN, y, text); y += 13f }
        y += 10f

        val cols = floatArrayOf(MARGIN, 270f, 340f, 400f, 450f)
        listOf("Item", "Qty", "Price", "Disc.", "Tax", "Amount").map { tr(it) }.forEachIndexed { i, h ->
            if (i == 5) right(h, bold) else canvas.drawText(h, cols[i], y, bold)
        }
        y += 6f
        canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, text); y += 14f
        inv.lines.forEach { l ->
            newPageIfNeeded(40f)
            canvas.drawText(clip(l.label, 44), cols[0], y, text)
            canvas.drawText("${Fmt.qty(l.quantity)} ${l.unitCode}", cols[1], y, text)
            canvas.drawText(Fmt.qty(l.unitPrice), cols[2], y, text)
            canvas.drawText(if (l.discount > 0) Fmt.qty(l.discount) else "-", cols[3], y, text)
            canvas.drawText(if (l.taxRate > 0) "${Fmt.qty(l.taxRate)}%" else "-", cols[4], y, text)
            right(money(l.lineTotal), text)
            y += 12f
            val detail = listOfNotNull(l.sku?.let { tr("SKU $it") }, specsText(l.attributes, l.variantAttributes)).joinToString(" · ")
            if (detail.isNotEmpty()) {
                canvas.drawText(clip(detail, 95), cols[0] + 8, y, small); y += 11f
            }
            y += 3f
        }
        canvas.drawLine(MARGIN, y - 6f, WIDTH - MARGIN, y - 6f, text); y += 8f

        fun total(label: String, value: String, paint: Paint = text) {
            newPageIfNeeded(16f)
            canvas.drawText(tr(label), 340f, y, paint)
            right(value, paint)
            y += 15f
        }
        total("Subtotal", money(inv.subtotal))
        if (inv.discountTotal > 0) total("Discount", "- " + money(inv.discountTotal))
        if (inv.taxTotal > 0) total("Tax", money(inv.taxTotal))
        total("Total", money(inv.total), bold)
        y += 10f
        inv.notes?.let { newPageIfNeeded(14f); canvas.drawText(clip(tr("Notes: $it"), 100), MARGIN, y, text); y += 14f }
        inv.deliveryNotes?.let { newPageIfNeeded(14f); canvas.drawText(clip(tr("Delivery: $it"), 100), MARGIN, y, text); y += 14f }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "invoice-${inv.invoiceNumber}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }
}
