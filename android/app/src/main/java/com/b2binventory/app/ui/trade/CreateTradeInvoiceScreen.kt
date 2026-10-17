package com.b2binventory.app.ui.trade

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.common.Text
import com.b2binventory.app.ui.sales.Calc
import com.b2binventory.app.ui.sales.ProductPickerDialog
import com.b2binventory.app.ui.sales.TotalRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

/** One line being typed: the product, which size/variant, and the editable numbers. */
data class DraftLine(
    val key: String = UUID.randomUUID().toString(),
    val product: ProductDetail,
    val variantId: Long,
    val quantity: String = "",
    val price: String = "",
    val discount: String = "",
    val taxRate: String = "",
) {
    val variant: Variant? get() = product.variants.firstOrNull { it.id == variantId }
    val unit: String get() = product.baseUnit.code
    fun figures(): Calc.Line? {
        val q = Calc.dec(quantity) ?: return null
        val p = Calc.dec(price) ?: return null
        return Calc.line(q, p, Calc.dec(discount) ?: BigDecimal.ZERO, Calc.dec(taxRate) ?: BigDecimal.ZERO)
    }
}

data class CreateInvoiceState(
    val submitting: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val createdId: Long? = null,
)

class CreateTradeInvoiceViewModel(val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    private val _buyers = MutableStateFlow(Loadable<List<Connection>>())
    val buyers = _buyers.asStateFlow()
    private val _buyerId = MutableStateFlow(saved.get<String>("buyerId")?.toLongOrNull())
    val buyerId = _buyerId.asStateFlow()
    private val _lines = MutableStateFlow<List<DraftLine>>(emptyList())
    val lines = _lines.asStateFlow()
    val notes = MutableStateFlow("")
    val delivery = MutableStateFlow("")
    private val _currency = MutableStateFlow("INR")
    val currency = _currency.asStateFlow()
    private val _state = MutableStateFlow(CreateInvoiceState())
    val state = _state.asStateFlow()
    private var requestId = UUID.randomUUID().toString()

    init {
        _buyers.load(viewModelScope) { api.connections("ACCEPTED") }
        viewModelScope.launch { (apiCall { api.settings() } as? ApiResult.Ok)?.let { _currency.value = it.value.currencyCode } }
    }

    fun selectBuyer(id: Long) { _buyerId.value = id }

    fun addProduct(summary: ProductSummary) {
        viewModelScope.launch {
            when (val r = apiCall { api.product(summary.id) }) {
                is ApiResult.Ok -> {
                    val p = r.value
                    val v = p.variants.filter { !it.archived }.let { active -> active.firstOrNull { !it.isDefault && active.size > 1 } ?: active.first() }
                    _lines.update {
                        it + DraftLine(product = p, variantId = v.id, price = p.salePrice?.let(Fmt::qty) ?: "",
                            taxRate = p.taxRate?.takeIf { t -> t > 0 }?.let(Fmt::qty) ?: "")
                    }
                }
                is ApiResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun updateLine(key: String, transform: (DraftLine) -> DraftLine) = _lines.update { list -> list.map { if (it.key == key) transform(it) else it } }
    fun removeLine(key: String) = _lines.update { list -> list.filterNot { it.key == key } }

    /** Checks the form before showing the preview; returns true when it can be sent. */
    fun validate(): Boolean {
        val errors = mutableMapOf<String, String>()
        if (_buyerId.value == null) errors["buyer"] = "Choose the business you are invoicing"
        if (_lines.value.isEmpty()) errors["lines"] = "Add at least one product"
        _lines.value.forEachIndexed { i, l ->
            val q = Calc.dec(l.quantity)
            if (q == null || q.signum() <= 0) errors["lines[$i].quantity"] = "Enter a quantity"
            else if (q.stripTrailingZeros().scale() > l.product.baseUnit.decimals) {
                errors["lines[$i].quantity"] = if (l.product.baseUnit.decimals == 0) "Whole ${l.unit} only" else "Too many decimals"
            }
            val available = l.variant?.stock?.available ?: 0.0
            if (q != null && q.toDouble() > available) errors["lines[$i].quantity"] = "Only ${Fmt.qty(available)} ${l.unit} available"
            if (Calc.dec(l.price) == null) errors["lines[$i].unitPrice"] = "Enter a price"
            val f = l.figures()
            if (f != null && f.discount > f.gross) errors["lines[$i].discount"] = "More than the line amount"
        }
        if (_lines.value.map { it.variantId }.toSet().size != _lines.value.size) errors["lines"] = "Each product size can be listed once"
        _state.value = CreateInvoiceState(fieldErrors = errors, error = if (errors.isEmpty()) null else "Please correct the highlighted fields")
        return errors.isEmpty()
    }

    fun submit(send: Boolean) {
        if (_state.value.submitting || !validate()) return
        val buyer = _buyerId.value ?: return
        _state.value = CreateInvoiceState(submitting = true)
        viewModelScope.launch {
            val body = TradeInvoiceBody(
                buyerBusinessId = buyer,
                notes = notes.value.blankToNull(),
                deliveryNotes = delivery.value.blankToNull(),
                lines = _lines.value.map {
                    TradeLineBody(it.variantId, Calc.dec(it.quantity)!!.toDouble(), Calc.dec(it.price)?.toDouble(),
                        Calc.dec(it.discount)?.toDouble(), Calc.dec(it.taxRate)?.toDouble() ?: 0.0)
                },
                send = send,
                clientRequestId = requestId,
            )
            _state.value = when (val r = apiCall { api.createTradeInvoice(body) }) {
                is ApiResult.Ok -> CreateInvoiceState(createdId = r.value.id)
                is ApiResult.Err -> {
                    // A failed send keeps the same request id only if nothing was saved; a new id avoids reusing it.
                    if (r.status != null) requestId = UUID.randomUUID().toString()
                    CreateInvoiceState(error = r.message, fieldErrors = r.fieldErrors)
                }
            }
        }
    }
}

@Composable
fun CreateTradeInvoiceScreen(onBack: () -> Unit, onCreated: (Long) -> Unit, onFindBusiness: () -> Unit) {
    val vm = appViewModel { c, s -> CreateTradeInvoiceViewModel(c.api, s) }
    val buyers by vm.buyers.collectAsStateWithLifecycle()
    val buyerId by vm.buyerId.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val delivery by vm.delivery.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    val e = state.fieldErrors

    Scaffold(topBar = { AppTopBar("New business invoice", onBack = onBack) }) { padding ->
        FormColumn(padding) {
            ErrorBanner(state.error)
            SectionCard("Bill to") {
                val list = buyers.data.orEmpty()
                when {
                    buyers.loading && buyers.data == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    list.isEmpty() -> {
                        Text("You are not connected with any business yet. Connect with your buyer first.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SecondaryButton("Find a business", onFindBusiness, icon = Icons.Default.Hub)
                    }
                    else -> DropdownField("Buyer business", list, list.firstOrNull { it.business.id == buyerId },
                        { vm.selectBuyer(it.business.id) }, { c -> listOfNotNull(c.business.name, c.business.city).joinToString(" · ") },
                        error = e["buyer"] ?: e["buyerBusinessId"], required = true)
                }
            }

            SectionCard("Products", actionLabel = "Add product", onAction = { picking = true }) {
                if (lines.isEmpty()) {
                    Text("Add the products you are selling. Stock is held for this buyer when you send the invoice.",
                        color = if (e["lines"] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                e["lines"]?.takeIf { lines.isNotEmpty() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                lines.forEachIndexed { i, l -> LineEditor(i, l, currency, e, vm) }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add product")
                }
            }

            SectionCard("Notes") {
                FormField(notes, { vm.notes.value = it }, "Notes for the buyer", singleLine = false, minLines = 2)
                FormField(delivery, { vm.delivery.value = it }, "Delivery details", placeholder = "e.g. Vehicle, date, address",
                    singleLine = false, minLines = 2)
            }

            val totals = lines.mapNotNull { it.figures() }
            if (totals.isNotEmpty()) {
                SectionCard("Total") {
                    TotalRow("Subtotal", Calc.money(totals.sumOf { it.gross }, currency))
                    val disc = totals.sumOf { it.discount }
                    if (disc.signum() > 0) TotalRow("Discount", "− " + Calc.money(disc, currency))
                    val tax = totals.sumOf { it.tax }
                    if (tax.signum() > 0) TotalRow("Tax", Calc.money(tax, currency))
                    TotalRow("Total", Calc.money(totals.sumOf { it.total }, currency), bold = true)
                }
            }
            PrimaryButton("Preview and send", { if (vm.validate()) previewing = true }, loading = state.submitting,
                icon = Icons.Default.Send)
            SecondaryButton("Save as draft", { vm.submit(send = false) }, enabled = !state.submitting, icon = Icons.Default.Save)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picking) {
        ProductPickerDialog(vm.api, "Add product", onPick = { picking = false; vm.addProduct(it) }, onDismiss = { picking = false })
    }
    if (previewing) {
        val buyer = buyers.data.orEmpty().firstOrNull { it.business.id == buyerId }?.business
        ConfirmDialog("Send invoice?", "Send", destructive = false, onConfirm = { previewing = false; vm.submit(send = true) },
            onDismiss = { previewing = false }, icon = Icons.Default.Send) {
            InfoRow("To", buyer?.name ?: "", emphasis = true)
            lines.forEach { l ->
                val f = l.figures()
                InfoRow("${l.product.name}${l.variant?.takeIf { !it.isDefault }?.let { " · ${it.name}" } ?: ""}",
                    "${l.quantity} ${l.unit} × ${l.price}" + (f?.let { " = " + Calc.money(it.total, currency) } ?: ""))
            }
            HorizontalDivider()
            InfoRow("Total", Calc.money(lines.mapNotNull { it.figures() }.sumOf { it.total }, currency), emphasis = true)
            MessageBanner(BannerKind.INFO, "The stock is held for the buyer now. It is deducted only when they approve.")
        }
    }
}

@Composable
private fun LineEditor(i: Int, l: DraftLine, currency: String, e: Map<String, String>, vm: CreateTradeInvoiceViewModel) {
    val active = l.product.variants.filter { !it.archived }
    AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(l.product.name, style = MaterialTheme.typography.titleSmall)
                    val v = l.variant
                    Text("${Fmt.qty(v?.stock?.available ?: 0.0)} ${l.unit} available", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.removeLine(l.key) }) { Icon(Icons.Default.Delete, tr("Remove")) }
            }
            if (active.size > 1) {
                DropdownField("Size / variant", active, l.variant, { v -> vm.updateLine(l.key) { it.copy(variantId = v.id) } },
                    { v -> if (v.isDefault) v.name else v.name + (specsText(emptyMap(), v.attributes)?.let { s -> " ($s)" } ?: "") },
                    error = e["lines[$i].variantId"], required = true)
            } else {
                e["lines[$i].variantId"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(l.quantity, { v -> vm.updateLine(l.key) { it.copy(quantity = v) } }, "Qty (${l.unit})", Modifier.weight(1f),
                    error = e["lines[$i].quantity"], keyboardType = KeyboardType.Decimal, required = true)
                FormField(l.price, { v -> vm.updateLine(l.key) { it.copy(price = v) } }, "Price / ${l.unit}", Modifier.weight(1f),
                    error = e["lines[$i].unitPrice"], keyboardType = KeyboardType.Decimal, required = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(l.discount, { v -> vm.updateLine(l.key) { it.copy(discount = v) } }, "Discount", Modifier.weight(1f),
                    error = e["lines[$i].discount"], keyboardType = KeyboardType.Decimal)
                FormField(l.taxRate, { v -> vm.updateLine(l.key) { it.copy(taxRate = v) } }, "Tax %", Modifier.weight(1f),
                    error = e["lines[$i].taxRate"], keyboardType = KeyboardType.Decimal)
            }
            l.figures()?.let { InfoRow("Line total", Calc.money(it.total, currency), emphasis = true) }
        }
    }
}
