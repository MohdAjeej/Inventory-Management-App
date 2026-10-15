package com.b2binventory.app.ui.sales

import com.b2binventory.app.ui.common.tr
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.b2binventory.app.data.remote.Sale
import com.b2binventory.app.ui.common.Fmt
import java.io.File

/** Draws a simple A4 receipt for a sale and returns a shareable Uri (open, print or send as PDF). */
object ReceiptPdf {

    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40f

    fun create(context: Context, sale: Sale, businessName: String, currency: String): Uri {
        val doc = PdfDocument()
        val text = Paint().apply { textSize = 10f; isAntiAlias = true }
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

        fun row(left: String, right: String, paint: Paint = text) {
            newPageIfNeeded(16f)
            canvas.drawText(tr(left), MARGIN, y, paint)
            canvas.drawText(right, WIDTH - MARGIN - paint.measureText(right), y, paint)
            y += 16f
        }

        fun money(v: Double) = Fmt.money(v, currency)

        canvas.drawText(businessName, MARGIN, y, title); y += 24f
        canvas.drawText(tr(if (sale.status == "CANCELLED") "SALES RECEIPT (CANCELLED)" else "SALES RECEIPT"), MARGIN, y, bold); y += 20f
        row("Invoice: ${sale.invoiceNumber}", tr("Date: ${Fmt.dateTime(sale.soldAt)}"))
        sale.customerName?.let { row("Customer: $it" + (sale.customerPhone?.let { p -> " ($p)" } ?: ""), "") }
        row("Served by: ${sale.soldBy}", "")
        y += 8f

        // Item table
        val cols = floatArrayOf(MARGIN, 280f, 340f, 410f, 470f)
        newPageIfNeeded(20f)
        listOf("Item", "Qty", "Price", "Disc.", "Amount").map { tr(it) }.forEachIndexed { i, h ->
            if (i == 4) canvas.drawText(h, WIDTH - MARGIN - bold.measureText(h), y, bold) else canvas.drawText(h, cols[i], y, bold)
        }
        y += 6f
        canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, text); y += 14f
        sale.items.forEach { it ->
            newPageIfNeeded(30f)
            val name = if (it.productName.length > 40) it.productName.take(38) + "…" else it.productName
            canvas.drawText(name, cols[0], y, text)
            canvas.drawText("${Fmt.qty(it.quantity)} ${it.unitCode}", cols[1], y, text)
            canvas.drawText(Fmt.qty(it.unitPrice), cols[2], y, text)
            canvas.drawText(if (it.discount > 0) Fmt.qty(it.discount) else "-", cols[3], y, text)
            val amount = money(it.lineTotal)
            canvas.drawText(amount, WIDTH - MARGIN - text.measureText(amount), y, text)
            y += 14f
            if (it.taxAmount > 0) {
                canvas.drawText(tr("Tax ${Fmt.qty(it.taxRate)}%: ${money(it.taxAmount)}"), cols[0] + 10, y, text); y += 14f
            }
            if (it.returnedQuantity > 0) {
                canvas.drawText(tr("Returned: ${Fmt.qty(it.returnedQuantity)} ${it.unitCode}"), cols[0] + 10, y, text); y += 14f
            }
        }
        canvas.drawLine(MARGIN, y - 6f, WIDTH - MARGIN, y - 6f, text); y += 8f

        row("Subtotal", money(sale.subtotal))
        if (sale.discountTotal > 0) row("Discount", "- " + money(sale.discountTotal))
        if (sale.taxTotal > 0) row("Tax", money(sale.taxTotal))
        row("Total", money(sale.total), bold)
        if (sale.returnedTotal > 0) row("Returned", "- " + money(sale.returnedTotal))
        row("Paid", money(sale.amountPaid))
        if (sale.status != "CANCELLED") row("Balance due", money(sale.outstanding), bold)
        y += 10f
        sale.payments.forEach { p ->
            row("${if (p.kind == "REFUND") "Refund" else "Payment"} · ${paymentMethodLabel(p.method)} · ${Fmt.dateTime(p.paidAt)}",
                money(p.amount))
        }
        sale.notes?.let { y += 6f; row("Notes: $it", "") }
        y += 20f
        newPageIfNeeded(20f)
        canvas.drawText(tr("Thank you for your business."), MARGIN, y, text)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "receipt-${sale.invoiceNumber}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }
}
