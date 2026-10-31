package com.b2binventory.app.ui.sales

import android.content.Context
import android.net.Uri
import com.b2binventory.app.data.remote.Business
import com.b2binventory.app.data.remote.Sale
import com.b2binventory.app.ui.common.AmountWords
import com.b2binventory.app.ui.common.Fmt
import com.b2binventory.app.ui.common.InvoicePdf
import com.b2binventory.app.ui.common.InvoicePdfData
import com.b2binventory.app.ui.common.InvoiceRow
import com.b2binventory.app.ui.common.InvoiceTotal

/** Draws an A4 invoice for a sale and returns a shareable Uri (open, print or send as PDF). */
object ReceiptPdf {

    /** [business] adds the address, phone and tax id to the header; without it only [businessName] is shown. */
    fun create(context: Context, sale: Sale, business: Business?, businessName: String, currency: String): Uri {
        fun money(v: Double) = Fmt.money(v, currency)
        val cancelled = sale.status == "CANCELLED"
        val units = sale.items.map { it.unitCode }.distinct()

        val rows = sale.items.map { it ->
            val name = if (it.variantName.isBlank() || it.variantName == it.productName) it.productName
                else "${it.productName} · ${it.variantName}"
            val detail = listOfNotNull(
                it.sku?.let { s -> "SKU $s" },
                if (it.returnedQuantity > 0) "Returned ${Fmt.qty(it.returnedQuantity)} ${it.unitCode}" else null,
                it.serials?.takeIf { s -> s.isNotEmpty() }?.let { s -> "S/N ${s.joinToString(", ")}" },
            ).joinToString(" · ").ifEmpty { null }
            InvoiceRow(
                name = name,
                detail = detail,
                qty = "${Fmt.qty(it.quantity)} ${it.unitCode}",
                rate = Fmt.qty(it.unitPrice),
                discount = if (it.discount > 0) Fmt.qty(it.discount) else null,
                tax = if (it.taxAmount > 0) "${Fmt.qty(it.taxRate)}%" else null,
                total = Fmt.qty(it.lineTotal),
            )
        }

        val totals = buildList {
            if (sale.discountTotal > 0 || sale.taxTotal > 0) add(InvoiceTotal("Gross amount", money(sale.subtotal)))
            if (sale.discountTotal > 0) add(InvoiceTotal("Discount", "- " + money(sale.discountTotal)))
            if (sale.taxTotal > 0) add(InvoiceTotal("Tax", money(sale.taxTotal)))
            add(InvoiceTotal("Total Amount", money(sale.total), strong = true))
            if (sale.returnedTotal > 0) add(InvoiceTotal("Returned", "- " + money(sale.returnedTotal)))
            add(InvoiceTotal("Received Amount", money(sale.amountPaid)))
            if (!cancelled && sale.outstanding > 0) add(InvoiceTotal("Balance Due", money(sale.outstanding)))
        }

        val notes = buildList {
            if (cancelled) add("Cancelled" to (sale.cancelReason ?: "This sale was cancelled."))
            sale.notes?.let { add("Notes" to it) }
            sale.payments.takeIf { it.isNotEmpty() }?.let { payments ->
                add("Payments" to payments.joinToString("\n") { p ->
                    "${if (p.kind == "REFUND") "Refund" else "Payment"} · ${paymentMethodLabel(p.method)} · " +
                        "${Fmt.date(p.paidAt)} · ${money(p.amount)}"
                })
            }
        }

        return InvoicePdf.create(context, InvoicePdfData(
            fileName = "receipt-${sale.invoiceNumber}.pdf",
            title = if (sale.taxTotal > 0) "TAX INVOICE" else "BILL OF SUPPLY",
            copyLabel = if (cancelled) "CANCELLED" else "ORIGINAL FOR RECIPIENT",
            sellerName = business?.name ?: businessName,
            sellerTaxId = business?.taxId?.let { (if (currency == "INR") "GSTIN" else "Tax ID") to it },
            sellerPhone = business?.phone,
            sellerAddress = business?.let {
                listOfNotNull(it.address, it.city, it.state, it.postalCode).filter { s -> s.isNotBlank() }
                    .joinToString(", ").ifEmpty { null }
            },
            meta = listOf("Invoice No." to sale.invoiceNumber, "Invoice Date" to Fmt.date(sale.soldAt),
                "Served by" to sale.soldBy).filter { it.second.isNotBlank() },
            billToName = sale.customerName ?: "Walk-in customer",
            billToLines = listOfNotNull(sale.customerPhone?.let { "Mobile" to it }),
            rows = rows,
            qtyTotal = if (units.size == 1) "${Fmt.qty(sale.items.sumOf { it.quantity })} ${units.first()}" else null,
            subtotal = money(sale.total),
            totals = totals,
            amountInWords = AmountWords.of(sale.total, currency),
            notes = notes,
        ))
    }
}
