package com.b2binventory.app.ui.sales

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

/** Payment methods offered in the app; the server accepts any short code. */
val paymentMethods = listOf("CASH", "UPI", "CARD", "BANK_TRANSFER", "CREDIT", "OTHER")

fun paymentMethodLabel(m: String): String = when (m) {
    "CASH" -> "Cash"
    "UPI" -> "UPI"
    "CARD" -> "Card"
    "BANK_TRANSFER" -> "Bank transfer"
    "CREDIT" -> "Credit (pay later)"
    "OTHER" -> "Other"
    else -> m.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
}

fun paymentStatusLabel(s: String): String = when (s) {
    "PAID" -> "Paid"
    "PARTIALLY_PAID" -> "Partly paid"
    "UNPAID" -> "Unpaid"
    else -> s
}

@Composable
fun PaymentStatusChip(status: String) {
    val color = when (status) {
        "PAID" -> StatusColors.ok
        "PARTIALLY_PAID" -> StatusColors.low
        else -> StatusColors.out
    }
    TagChip(paymentStatusLabel(status), color)
}

@Composable
fun DocumentStatusChip(status: String) {
    if (status == "CANCELLED") TagChip("Cancelled", MaterialTheme.colorScheme.error)
}

/**
 * Decimal arithmetic for on-screen previews, matching the server's rules (amounts rounded half-up to 2 places).
 * The server recalculates and stores the real figures.
 */
object Calc {
    fun dec(text: String): BigDecimal? = text.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
    fun dec(value: Double?): BigDecimal? = value?.let { BigDecimal.valueOf(it) }
    fun amount(v: BigDecimal): BigDecimal = v.setScale(2, RoundingMode.HALF_UP)
    fun percent(base: BigDecimal, rate: BigDecimal): BigDecimal =
        amount(base.multiply(rate).divide(BigDecimal(100), 6, RoundingMode.HALF_UP))

    /** Line figures: gross, discount, net (revenue), tax, total. */
    data class Line(val gross: BigDecimal, val discount: BigDecimal, val net: BigDecimal, val tax: BigDecimal, val total: BigDecimal)

    fun line(qty: BigDecimal, price: BigDecimal, discount: BigDecimal, taxRate: BigDecimal): Line {
        val gross = amount(qty.multiply(price))
        val d = amount(discount)
        val net = gross.subtract(d)
        val tax = percent(net, taxRate)
        return Line(gross, d, net, tax, net.add(tax))
    }

    fun money(v: BigDecimal, currency: String) = Fmt.money(v.toDouble(), currency)
}

/** Full-screen product search used by the sale and purchase screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerDialog(api: ApiService, title: String, onPick: (ProductSummary) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProductSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(300)
        loading = true
        when (val r = apiCall { api.products(q = query.blankToNull(), size = 40) }) {
            is ApiResult.Ok -> { results = r.value.content; error = null }
            is ApiResult.Err -> error = r.message
        }
        loading = false
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = { AppTopBar(title, onBack = onDismiss) }) { padding ->
                Column(Modifier.padding(padding)) {
                    SearchField(query, { query = it }, "Search name, SKU or barcode", Modifier.padding(16.dp, 8.dp))
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { ErrorBanner(it) }
                    LazyColumn {
                        if (!loading && results.isEmpty()) item { EmptyState("No products found", "Try another name or code.") }
                        items(results, key = { it.id }) { p ->
                            ListItem(
                                headlineContent = { Text(p.name, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(listOfNotNull(p.sku, p.categoryName, p.salePrice?.let { "Price ${Fmt.qty(it)}" })
                                        .joinToString(" · ").ifEmpty { p.unitCode })
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                        Text("${Fmt.qty(p.stock.available)} ${p.unitCode}", fontWeight = FontWeight.SemiBold)
                                        StatusChip(p.status)
                                    }
                                },
                                modifier = Modifier.clickable { onPick(p) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Customer or supplier chooser with the option to type a new name. */
@Composable
fun PartyField(
    label: String,
    parties: List<Party>,
    selected: Party?,
    typedName: String,
    onSelect: (Party?) -> Unit,
    onTypedName: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (selected != null) {
            AppCard {
                ListItem(
                    headlineContent = { Text(selected.name) },
                    supportingContent = selected.phone?.let { { Text(it) } },
                    overlineContent = { Text(label) },
                    trailingContent = { TextButton(onClick = { onSelect(null) }) { Text("Change") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        } else {
            FormField(typedName, onTypedName, label, placeholder = "Type a name or pick a saved one")
            if (parties.isNotEmpty()) {
                val matches = parties.filter { typedName.isBlank() || it.name.contains(typedName.trim(), true) }.take(5)
                if (matches.isNotEmpty() && (open || typedName.isNotBlank())) {
                    matches.forEach { p ->
                        Text("• ${p.name}" + (p.phone?.let { " ($it)" } ?: ""), Modifier.fillMaxWidth().clickable { onSelect(p) }
                            .padding(vertical = 6.dp), color = MaterialTheme.colorScheme.primary)
                    }
                } else if (!open) {
                    TextButton(onClick = { open = true }) { Text("Choose a saved ${label.lowercase()}") }
                }
            }
        }
    }
}

/** Row of totals used in summaries and receipts. */
@Composable
fun TotalRow(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
        Text(value, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}

/** Dialog to record a payment against a sale or purchase. */
@Composable
fun AddPaymentDialog(outstanding: Double, currency: String, submitting: Boolean, onConfirm: (Double, String, String?) -> Unit,
                     onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(Fmt.qty(outstanding)) }
    var method by remember { mutableStateOf("CASH") }
    var reference by remember { mutableStateOf("") }
    val value = amount.toDoubleOrNull()
    val error = when {
        value == null || value <= 0 -> "Enter an amount"
        value > outstanding + 0.0001 -> "Only ${Fmt.money(outstanding, currency)} is outstanding"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding: ${Fmt.money(outstanding, currency)}")
                FormField(amount, { amount = it }, "Amount ($currency)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    error = if (amount.isNotBlank()) error else null, required = true)
                DropdownField("Method", paymentMethods.filter { it != "CREDIT" }, method, { method = it }, ::paymentMethodLabel)
                FormField(reference, { reference = it }, "Reference (optional)", placeholder = "e.g. UPI transaction id")
            }
        },
        confirmButton = {
            TextButton(enabled = error == null && !submitting, onClick = { onConfirm(value!!, method, reference.blankToNull()) }) {
                Text("Save payment")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
