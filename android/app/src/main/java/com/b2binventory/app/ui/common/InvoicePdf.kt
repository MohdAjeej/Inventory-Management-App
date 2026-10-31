package com.b2binventory.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/** One table row: quantities and amounts are already formatted. */
data class InvoiceRow(
    val name: String,
    val detail: String? = null,
    val qty: String,
    val rate: String,
    val discount: String? = null,
    val tax: String? = null,
    val total: String,
)

/** A line under the item table; [strong] is the grand total, drawn between accent rules. */
data class InvoiceTotal(val label: String, val value: String, val strong: Boolean = false)

data class InvoicePdfData(
    val fileName: String,
    val title: String,
    val copyLabel: String?,
    val sellerName: String,
    /** Label and value, e.g. "GSTIN" to "09ABCDE1234F1Z2". */
    val sellerTaxId: Pair<String, String>? = null,
    val sellerPhone: String? = null,
    val sellerAddress: String? = null,
    /** "Invoice No." to "9", "Invoice Date" to "27 Oct 2026", ... */
    val meta: List<Pair<String, String>>,
    val billToName: String,
    /** Label (may be null) and value lines under the buyer's name. */
    val billToLines: List<Pair<String?, String>> = emptyList(),
    val sideTitle: String? = null,
    val sideLines: List<Pair<String?, String>> = emptyList(),
    val rows: List<InvoiceRow>,
    val qtyTotal: String?,
    val subtotal: String,
    val totals: List<InvoiceTotal>,
    val amountInWords: String,
    /** Titled text blocks beside the totals: notes, delivery notes, cancellation reason. */
    val notes: List<Pair<String, String>> = emptyList(),
)

/**
 * Draws an A4 invoice in the bordered "bill of supply" layout: business header, invoice details, bill-to panel,
 * tinted item table, subtotal band, totals with the amount in words. Shared by sale receipts and business invoices.
 */
object InvoicePdf {

    private const val WIDTH = 595f
    private const val HEIGHT = 842f
    private const val FRAME = 18f
    private const val PAD = 14f
    private const val LEFT = FRAME + PAD
    private const val RIGHT = WIDTH - FRAME - PAD
    private const val MID = WIDTH / 2
    private const val TABLE_HEADER = 22f
    /** Height kept free at the bottom of the last page for the subtotal band and totals. */
    private const val FOOTER = 190f

    private val ACCENT = Color.rgb(0xE0, 0x6A, 0x9A)
    private val ACCENT_DARK = Color.rgb(0xAD, 0x14, 0x57)
    private val TINT = Color.rgb(0xFD, 0xE7, 0xEF)
    private val INK = Color.rgb(0x3A, 0x2E, 0x39)
    private val MUTED = Color.rgb(0x7A, 0x6B, 0x76)

    fun create(context: Context, data: InvoicePdfData): Uri {
        val doc = PdfDocument()
        val text = Paint().apply { textSize = 9f; isAntiAlias = true; color = INK }
        val muted = Paint(text).apply { color = MUTED }
        val small = Paint(muted).apply { textSize = 7.5f }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val heading = Paint(bold).apply { textSize = 10f }
        val brand = Paint(bold).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize = 20f; color = ACCENT_DARK; letterSpacing = 0.04f
        }
        val title = Paint(bold).apply { textSize = 13f; color = ACCENT_DARK }
        val line = Paint().apply { color = ACCENT; strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true }
        val fill = Paint().apply { color = TINT; style = Paint.Style.FILL }

        // Numeric columns, right-aligned from the right edge; the item name takes the remaining width.
        val showDiscount = data.rows.any { it.discount != null }
        val showTax = data.rows.any { it.tax != null }
        val columns = buildList<Pair<String, (InvoiceRow) -> String>> {
            add("Qty." to { it.qty })
            add("Rate" to { it.rate })
            if (showDiscount) add("Disc." to { it.discount ?: "-" })
            if (showTax) add("Tax" to { it.tax ?: "-" })
            add("Total" to { it.total })
        }
        val widths = columns.mapIndexed { i, _ -> if (i == columns.lastIndex) 72f else 60f }
        val rightEdges = FloatArray(columns.size).also { edges ->
            var x = WIDTH - FRAME - 8f
            for (i in columns.indices.reversed()) { edges[i] = x; x -= widths[i] }
        }
        val itemsX = LEFT + 36f
        val itemsWidth = rightEdges[0] - widths[0] - itemsX - 8f

        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f

        fun drawRight(s: String, x: Float, paint: Paint) = canvas.drawText(s, x - paint.measureText(s), y, paint)

        fun tableHeader() {
            canvas.drawRect(FRAME + 0.5f, y, WIDTH - FRAME - 0.5f, y + TABLE_HEADER, fill)
            y += 14.5f
            canvas.drawText(tr("No"), LEFT, y, bold)
            canvas.drawText(tr("Items"), itemsX, y, bold)
            columns.forEachIndexed { i, (label, _) -> drawRight(tr(label), rightEdges[i], bold) }
            y += TABLE_HEADER - 14.5f + 14f
        }

        fun startPage() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH.toInt(), HEIGHT.toInt(), pageNo).create())
            canvas = page.canvas
            canvas.drawRect(FRAME, FRAME, WIDTH - FRAME, HEIGHT - FRAME, line)
            ornaments(canvas, line)
            y = FRAME
        }

        fun continuePage() {
            doc.finishPage(page)
            startPage()
            y += 12f
            tableHeader()
        }

        startPage()

        // Header: business on the left, document title and copy label on the right.
        y += 34f
        canvas.drawText(fit(data.sellerName, brand, MID + 40f - LEFT), LEFT, y, brand)
        drawRight(tr(data.title), RIGHT, title)
        data.copyLabel?.let { label ->
            val s = tr(label)
            val w = small.measureText(s) + 14f
            val box = RectF(RIGHT - w, y + 7f, RIGHT, y + 22f)
            canvas.drawRoundRect(box, 2f, 2f, Paint(line).apply { color = MUTED; strokeWidth = 0.7f })
            canvas.drawText(s, box.left + 7f, box.bottom - 4.5f, small)
        }
        y += 18f
        data.sellerTaxId?.let { (label, value) ->
            canvas.drawText(label, LEFT, y, bold)
            canvas.drawText(value, LEFT + bold.measureText(label) + 5f, y, text)
            y += 15f
        }
        data.sellerPhone?.let {
            bullet(canvas, LEFT + 3f, y - 3f)
            canvas.drawText(it, LEFT + 13f, y, Paint(text).apply { textSize = 10f })
            y += 16f
        }
        data.sellerAddress?.let { address ->
            wrap(address, text, RIGHT - LEFT - 13f).forEachIndexed { i, l ->
                if (i == 0) bullet(canvas, LEFT + 3f, y - 3f)
                canvas.drawText(l, LEFT + 13f, y, text); y += 12f
            }
        }
        y += 6f
        canvas.drawLine(FRAME, y, WIDTH - FRAME, y, line)

        // Invoice details in columns.
        y += 18f
        var x = LEFT
        data.meta.forEach { (label, value) ->
            val w = maxOf(bold.measureText(tr(label)), text.measureText(value)) + 22f
            canvas.drawText(tr(label), x, y, bold)
            canvas.drawText(value, x, y + 15f, text)
            x += maxOf(w, 80f)
        }
        y += 30f
        canvas.drawLine(FRAME, y, WIDTH - FRAME, y, line)

        // Bill to on the left, optional details on the right.
        val panelTop = y
        fun panel(x0: Float, title: String, name: String?, lines: List<Pair<String?, String>>, width: Float): Float {
            var py = panelTop + 20f
            canvas.drawText(tr(title), x0, py, heading); py += 14f
            name?.let { canvas.drawText(fit(it, Paint(text).apply { textSize = 10f }, width), x0, py, Paint(text).apply { textSize = 10f }); py += 13f }
            lines.forEach { (label, value) ->
                var vx = x0
                label?.let { canvas.drawText(tr(it), x0, py, bold); vx += bold.measureText(tr(it)) + 4f }
                canvas.drawText(fit(value, text, width - (vx - x0)), vx, py, text); py += 13f
            }
            return py
        }
        val leftBottom = panel(LEFT, "Bill To", data.billToName, data.billToLines, MID - LEFT - 10f)
        val rightBottom = data.sideTitle?.let { panel(MID + PAD, it, null, data.sideLines, RIGHT - MID - PAD) } ?: panelTop
        y = maxOf(leftBottom, rightBottom, panelTop + 60f) + 8f
        canvas.drawLine(MID, panelTop, MID, y, line)

        // Items.
        tableHeader()
        data.rows.forEachIndexed { index, row ->
            val nameLines = wrap(row.name, text, itemsWidth).take(2)
            val detailLines = row.detail?.let { wrap(it, small, itemsWidth).take(2) }.orEmpty()
            val height = nameLines.size * 12f + detailLines.size * 10f + 8f
            if (y + height > HEIGHT - FRAME - 20f) continuePage()
            canvas.drawText("${index + 1}", LEFT + 4f, y, text)
            columns.forEachIndexed { i, (_, value) -> drawRight(value(row), rightEdges[i], text) }
            nameLines.forEach { canvas.drawText(it, itemsX, y, text); y += 12f }
            detailLines.forEach { canvas.drawText(it, itemsX, y - 1f, small); y += 10f }
            y += 8f
        }

        // Subtotal band and totals sit at the bottom of the last page.
        val footerTop = HEIGHT - FRAME - FOOTER
        if (y - 10f > footerTop) {
            doc.finishPage(page)
            startPage()
        }
        y = footerTop
        canvas.drawRect(FRAME + 0.5f, y, WIDTH - FRAME - 0.5f, y + TABLE_HEADER, fill)
        y += 14.5f
        canvas.drawText(tr("SUBTOTAL"), itemsX, y, bold)
        data.qtyTotal?.let { drawRight(it, rightEdges[0], bold) }
        drawRight(data.subtotal, rightEdges.last(), bold)

        val blockTop = footerTop + TABLE_HEADER + 18f
        // Left: notes.
        y = blockTop
        data.notes.forEach { (heading_, body) ->
            if (y > HEIGHT - FRAME - 40f) return@forEach
            canvas.drawText(tr(heading_), LEFT, y, Paint(text).apply { textSize = 9.5f }); y += 12f
            wrap(body, Paint(muted).apply { textSize = 8f }, MID - LEFT - 20f).take(4).forEach {
                canvas.drawText(it, LEFT, y, Paint(muted).apply { textSize = 8f }); y += 10.5f
            }
            y += 6f
        }

        // Right: totals and the amount in words.
        val tx0 = MID + 20f
        y = blockTop - 8f
        val totalLabel = Paint(bold).apply { textSize = 11f }
        data.totals.forEach { t ->
            if (t.strong) {
                canvas.drawLine(tx0, y, RIGHT, y, Paint(line).apply { strokeWidth = 1.2f })
                y += 15f
                canvas.drawText(tr(t.label), tx0, y, totalLabel)
                drawRight(t.value, RIGHT, totalLabel)
                y += 8f
                canvas.drawLine(tx0, y, RIGHT, y, Paint(line).apply { strokeWidth = 1.2f })
                y += 14f
            } else {
                y += 4f
                canvas.drawText(tr(t.label), tx0, y, muted)
                drawRight(t.value, RIGHT, muted)
                y += 12f
            }
        }
        y += 10f
        canvas.drawText(tr("Total Amount (in words)"), tx0, y, bold); y += 12f
        wrap(data.amountInWords, muted, RIGHT - tx0).take(3).forEach { canvas.drawText(it, tx0, y, muted); y += 11f }

        doc.finishPage(page)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, data.fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    /** Small flourishes in the four corners of the frame: a ring, a curl and a dotted rule along each edge. */
    private fun ornaments(canvas: Canvas, line: Paint) {
        val p = Paint(line).apply { strokeWidth = 0.8f }
        val dot = Paint().apply { color = ACCENT; style = Paint.Style.FILL; isAntiAlias = true }
        for ((cx, sx) in listOf(FRAME to 1f, WIDTH - FRAME to -1f)) {
            for ((cy, sy) in listOf(FRAME to 1f, HEIGHT - FRAME to -1f)) {
                val ox = cx + sx * 4f
                val oy = cy + sy * 4f
                canvas.drawCircle(ox + sx * 5f, oy + sy * 5f, 3.5f, p)
                canvas.drawCircle(ox + sx * 5f, oy + sy * 5f, 1.2f, dot)
                canvas.drawArc(RectF(ox + sx * 8f - 4f, oy - 4f + sy * 2f, ox + sx * 8f + 4f, oy + 4f + sy * 2f), 0f, 360f, false, p)
                canvas.drawArc(RectF(ox - 4f + sx * 2f, oy + sy * 8f - 4f, ox + 4f + sx * 2f, oy + sy * 8f + 4f), 0f, 360f, false, p)
                // Rules along the horizontal and vertical edges.
                canvas.drawLine(ox + sx * 16f, oy + sy * 2f, ox + sx * 40f, oy + sy * 2f, p)
                canvas.drawCircle(ox + sx * 22f, oy + sy * 2f, 1.3f, dot)
                canvas.drawCircle(ox + sx * 34f, oy + sy * 2f, 1.3f, dot)
                canvas.drawLine(ox + sx * 2f, oy + sy * 16f, ox + sx * 2f, oy + sy * 40f, p)
                canvas.drawCircle(ox + sx * 2f, oy + sy * 22f, 1.3f, dot)
                canvas.drawCircle(ox + sx * 2f, oy + sy * 34f, 1.3f, dot)
            }
        }
    }

    private fun bullet(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, 3f, Paint().apply { color = ACCENT; style = Paint.Style.FILL; isAntiAlias = true })
    }

    /** Ellipsizes [s] to fit [width]. */
    private fun fit(s: String, paint: Paint, width: Float): String {
        if (paint.measureText(s) <= width) return s
        var end = s.length
        while (end > 1 && paint.measureText(s.take(end) + "…") > width) end--
        return s.take(end) + "…"
    }

    /** Word-wraps [s] into lines no wider than [width]; very long words are cut. */
    private fun wrap(s: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        s.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            current = when {
                paint.measureText(candidate) <= width -> candidate
                current.isEmpty() -> fit(word, paint, width).also { lines += it }.let { "" }
                else -> { lines += current; if (paint.measureText(word) <= width) word else { lines += fit(word, paint, width); "" } }
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}

/** Amounts written out for invoices. */
object AmountWords {

    /** "Seven Hundred Rupees and Fifty Paise"; lakh/crore grouping for rupees, thousand/million otherwise. */
    fun of(value: Double, currencyCode: String): String {
        val amount = BigDecimal.valueOf(Math.abs(value)).setScale(2, RoundingMode.HALF_UP)
        val whole = amount.toBigInteger().toLong()
        val fraction = amount.remainder(BigDecimal.ONE).movePointRight(2).toInt()
        if (currencyCode == "INR") {
            val rupees = "${indian(whole)} ${if (whole == 1L) "Rupee" else "Rupees"}"
            return if (fraction > 0) "$rupees and ${below1000(fraction.toLong())} Paise" else rupees
        }
        val name = runCatching { Currency.getInstance(currencyCode).getDisplayName(Locale.ENGLISH) }.getOrDefault(currencyCode)
        val main = "${international(whole)} ${if (whole == 1L) name else name + "s"}"
        return if (fraction > 0) "$main and $fraction/100" else main
    }

    private val ONES = listOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven",
        "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    private val TENS = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun below1000(n: Long): String {
        val parts = mutableListOf<String>()
        if (n >= 100) parts += "${ONES[(n / 100).toInt()]} Hundred"
        val rest = (n % 100).toInt()
        if (rest in 1..19) parts += ONES[rest]
        else if (rest >= 20) parts += listOf(TENS[rest / 10], ONES[rest % 10]).filter { it.isNotEmpty() }.joinToString(" ")
        return parts.joinToString(" ")
    }

    private fun indian(n: Long): String {
        if (n == 0L) return "Zero"
        val parts = mutableListOf<String>()
        var rest = n
        listOf(10_000_000L to "Crore", 100_000L to "Lakh", 1_000L to "Thousand").forEach { (size, label) ->
            if (rest >= size) {
                val count = rest / size
                parts += "${if (size == 10_000_000L) indian(count) else below1000(count)} $label"
                rest %= size
            }
        }
        if (rest > 0) parts += below1000(rest)
        return parts.joinToString(" ")
    }

    private fun international(n: Long): String {
        if (n == 0L) return "Zero"
        val parts = mutableListOf<String>()
        var rest = n
        listOf(1_000_000_000L to "Billion", 1_000_000L to "Million", 1_000L to "Thousand").forEach { (size, label) ->
            if (rest >= size) {
                val count = rest / size
                parts += "${if (count >= 1000) international(count) else below1000(count)} $label"
                rest %= size
            }
        }
        if (rest > 0) parts += below1000(rest)
        return parts.joinToString(" ")
    }
}
