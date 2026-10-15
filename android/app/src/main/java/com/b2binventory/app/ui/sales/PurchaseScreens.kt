package com.b2binventory.app.ui.sales

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.sync.showSavedOffline
import com.b2binventory.app.data.sync.OfflineQueue
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class PurchaseLine(
    val key: String = UUID.randomUUID().toString(),
    val product: ProductDetail,
    val variantId: Long,
    val quantity: String = "",
    val unitCost: String = "",
    val discount: String = "",
    val taxRate: String = "",
    val lotNumber: String = "",
    val expiryDate: String = "",
    val serials: String = "",
)

data class PurchaseHeader(
    val warehouseId: Long? = null,
    val supplier: Party? = null,
    val supplierName: String = "",
    val saveSupplier: Boolean = true,
    val referenceNumber: String = "",
    val date: String = LocalDate.now().toString(),
    val paymentMethod: String = "CREDIT",
    val amountPaid: String? = null,
    val notes: String = "",
)

class RecordPurchaseViewModel(val api: ApiService, saved: SavedStateHandle, private val offline: OfflineQueue) : ViewModel() {
    private val _lines = MutableStateFlow<List<PurchaseLine>>(emptyList())
    val lines = _lines.asStateFlow()
    private val _header = MutableStateFlow(PurchaseHeader())
    val header = _header.asStateFlow()
    private val _state = MutableStateFlow(RecordState())
    val state = _state.asStateFlow()
    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()
    private val _settings = MutableStateFlow(BusinessSettings())
    val settings = _settings.asStateFlow()
    private val _suppliers = MutableStateFlow<List<Party>>(emptyList())
    val suppliers = _suppliers.asStateFlow()
    private val requestId = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            (apiCall { api.warehouses() } as? ApiResult.Ok)?.let { r ->
                _warehouses.value = r.value
                _header.update { it.copy(warehouseId = (r.value.firstOrNull { w -> w.isDefault } ?: r.value.firstOrNull())?.id) }
            }
            (apiCall { api.settings() } as? ApiResult.Ok)?.let { _settings.value = it.value }
            (apiCall { api.suppliers() } as? ApiResult.Ok)?.let { _suppliers.value = it.value }
        }
        saved.get<String>("productId")?.toLongOrNull()?.let(::addProduct)
    }

    fun addProduct(id: Long) {
        viewModelScope.launch {
            when (val r = apiCall { api.product(id) }) {
                is ApiResult.Ok -> {
                    val p = r.value
                    val v = p.variants.firstOrNull { it.isDefault && !it.archived } ?: p.variants.firstOrNull { !it.archived }
                    if (v == null || p.archived) {
                        _state.update { it.copy(error = "${p.name} is archived") }
                    } else if (_lines.value.none { it.variantId == v.id }) {
                        _lines.update { it + PurchaseLine(product = p, variantId = v.id,
                            unitCost = p.purchasePrice?.let { c -> Fmt.qty(c) } ?: "",
                            taxRate = p.taxRate?.let { t -> Fmt.qty(t) } ?: "") }
                        if (_header.value.supplier == null && p.supplierId != null) {
                            _suppliers.value.firstOrNull { it.id == p.supplierId }?.let { s -> _header.update { it.copy(supplier = s) } }
                        }
                    }
                }
                is ApiResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun updateLine(key: String, t: (PurchaseLine) -> PurchaseLine) {
        _lines.update { l -> l.map { if (it.key == key) t(it) else it } }
        _state.update { it.copy(fieldErrors = emptyMap(), error = null) }
    }

    fun removeLine(key: String) = _lines.update { l -> l.filterNot { it.key == key } }

    fun updateHeader(t: (PurchaseHeader) -> PurchaseHeader) {
        _header.update(t)
        _state.update { it.copy(fieldErrors = emptyMap(), error = null) }
    }

    fun figures(l: PurchaseLine): Calc.Line? {
        val q = Calc.dec(l.quantity) ?: return null
        val c = Calc.dec(l.unitCost) ?: return null
        return Calc.line(q, c, Calc.dec(l.discount) ?: BigDecimal.ZERO, Calc.dec(l.taxRate) ?: BigDecimal.ZERO)
    }

    fun total(): BigDecimal = _lines.value.mapNotNull(::figures).fold(BigDecimal.ZERO) { a, f -> a.add(f.total) }

    fun paid(): BigDecimal = if (_header.value.paymentMethod == "CREDIT") BigDecimal.ZERO
        else _header.value.amountPaid?.let { Calc.dec(it) } ?: total()

    fun submit() {
        if (_state.value.submitting) return
        val s = _settings.value
        val h = _header.value
        val errors = buildMap {
            if (_lines.value.isEmpty()) put("items", "Add at least one product")
            _lines.value.forEach { l ->
                val q = Calc.dec(l.quantity)
                if (q == null || q.signum() <= 0) put("${l.key}.quantity", "Enter the quantity received")
                else if (q.stripTrailingZeros().scale() > l.product.baseUnit.decimals) put("${l.key}.quantity", "Too many decimals")
                val c = Calc.dec(l.unitCost)
                if (c == null || c.signum() < 0) put("${l.key}.unitCost", "Enter the purchase price")
                val f = figures(l)
                if (f != null && f.discount > f.gross) put("${l.key}.discount", "More than the line amount")
                if (l.product.trackBatches && s.trackBatches && l.lotNumber.isBlank()) put("${l.key}.lot", "Lot number is required")
                if (l.product.trackBatches && s.trackBatches && s.trackExpiry && l.expiryDate.isBlank()) put("${l.key}.expiry", "Expiry date is required")
                if (l.product.trackSerials && s.trackSerials && q != null && serialList(l.serials).size != q.toInt()) {
                    put("${l.key}.serials", "Enter ${Fmt.qty(q.toDouble())} serial numbers")
                }
            }
            if (paid() > total()) put("amountPaid", "More than the total")
        }
        if (errors.isNotEmpty()) {
            _state.value = RecordState(fieldErrors = errors, error = errors["items"] ?: "Please correct the highlighted fields")
            return
        }
        _state.value = RecordState(submitting = true)
        viewModelScope.launch {
            val paid = paid()
            val at = runCatching {
                val d = LocalDate.parse(h.date)
                (if (d == LocalDate.now()) d.atTime(LocalTime.now()) else d.atTime(LocalTime.NOON)).atZone(ZoneId.systemDefault()).toInstant().toString()
            }.getOrNull()
            val request = PurchaseRequest(
                items = _lines.value.map { l ->
                    PurchaseLineRequest(l.variantId, h.warehouseId, l.quantity.trim().toDouble(), l.unitCost.trim().toDouble(),
                        Calc.dec(l.discount)?.toDouble(), Calc.dec(l.taxRate)?.toDouble() ?: 0.0, l.lotNumber.blankToNull(),
                        l.expiryDate.blankToNull(), serialList(l.serials).ifEmpty { null })
                },
                supplierId = h.supplier?.id,
                supplierName = (h.supplier?.name ?: h.supplierName).blankToNull(),
                saveSupplier = h.supplier == null && h.saveSupplier,
                referenceNumber = h.referenceNumber.blankToNull(),
                purchasedAt = at,
                payment = if (paid.signum() > 0) PaymentInput(h.paymentMethod, paid.toDouble()) else null,
                notes = h.notes.blankToNull(),
                clientRequestId = requestId,
            )
            _state.value = when (val r = apiCall { api.recordPurchase(request) }) {
                is ApiResult.Ok -> RecordState(savedId = r.value.id)
                is ApiResult.Err -> if (r.offline &&
                    offline.purchase(request, _lines.value.joinToString { "${it.product.name} × ${it.quantity.trim()}" })) {
                    RecordState(queued = true)
                } else RecordState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }
}

@Composable
fun RecordPurchaseScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel { c, s -> RecordPurchaseViewModel(c.api, s, OfflineQueue(c)) }
    val lines by vm.lines.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val warehouses by vm.warehouses.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    val queuedContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.queued) { if (state.queued) { showSavedOffline(queuedContext); onBack() } }
    val e = state.fieldErrors
    val cur = settings.currencyCode

    Scaffold(topBar = { AppTopBar("Record purchase", onBack = onBack) }) { padding ->
        FormColumn(padding) {
            ErrorBanner(state.error)
            Text("Goods received from a dealer or supplier. Stock goes up when you save.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PartyField("Supplier (optional)", suppliers, header.supplier, header.supplierName,
                { s -> vm.updateHeader { it.copy(supplier = s) } }, { v -> vm.updateHeader { it.copy(supplierName = v) } })
            if (header.supplier == null && header.supplierName.isNotBlank()) {
                SwitchRow("Save this supplier", header.saveSupplier, { v -> vm.updateHeader { it.copy(saveSupplier = v) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(header.referenceNumber, { v -> vm.updateHeader { it.copy(referenceNumber = v) } }, "Bill / reference no.",
                    Modifier.weight(1f))
                DateField("Date received", header.date, { v -> vm.updateHeader { it.copy(date = v.ifBlank { LocalDate.now().toString() }) } },
                    Modifier.weight(1f), error = e["purchasedAt"])
            }
            if (warehouses.size > 1) {
                DropdownField("Receive into warehouse", warehouses, warehouses.firstOrNull { it.id == header.warehouseId },
                    { w -> vm.updateHeader { it.copy(warehouseId = w.id) } }, { it.name })
            }

            Text("Products received", style = MaterialTheme.typography.titleMedium)
            lines.forEach { l ->
                val p = l.product
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text("In stock ${Fmt.qty(p.stock.onHand)} ${p.baseUnit.code}" +
                                    (p.averageCost?.let { " · average cost ${Fmt.money(it, cur)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.removeLine(l.key) }) { Icon(Icons.Default.Delete, "Remove ${p.name}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormField(l.quantity, { v -> vm.updateLine(l.key) { it.copy(quantity = v) } }, "Qty (${p.baseUnit.code})",
                                Modifier.weight(1f), keyboardType = KeyboardType.Decimal, error = e["${l.key}.quantity"], required = true)
                            FormField(l.unitCost, { v -> vm.updateLine(l.key) { it.copy(unitCost = v) } }, "Cost / ${p.baseUnit.code}",
                                Modifier.weight(1f), keyboardType = KeyboardType.Decimal, error = e["${l.key}.unitCost"], required = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormField(l.discount, { v -> vm.updateLine(l.key) { it.copy(discount = v) } }, "Discount", Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal, error = e["${l.key}.discount"], placeholder = "0")
                            FormField(l.taxRate, { v -> vm.updateLine(l.key) { it.copy(taxRate = v) } }, "Tax %", Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal, placeholder = "0")
                        }
                        if (p.trackBatches && settings.trackBatches) {
                            FormField(l.lotNumber, { v -> vm.updateLine(l.key) { it.copy(lotNumber = v) } }, "Lot number", required = true,
                                error = e["${l.key}.lot"])
                            if (settings.trackExpiry) DateField("Expiry date", l.expiryDate, { v -> vm.updateLine(l.key) { it.copy(expiryDate = v) } },
                                error = e["${l.key}.expiry"], required = true)
                        }
                        if (p.trackSerials && settings.trackSerials) {
                            FormField(l.serials, { v -> vm.updateLine(l.key) { it.copy(serials = v) } }, "Serial numbers", required = true,
                                singleLine = false, minLines = 2, error = e["${l.key}.serials"], supporting = "One per line")
                        }
                        vm.figures(l)?.let { f -> Text("Line total ${Calc.money(f.total, cur)}", fontWeight = FontWeight.Medium) }
                    }
                }
            }
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if (lines.isEmpty()) "Add product" else "Add another product")
            }

            Text("Payment to supplier", style = MaterialTheme.typography.titleMedium)
            DropdownField("Payment", listOf("CREDIT") + paymentMethods.filter { it != "CREDIT" }, header.paymentMethod,
                { m -> vm.updateHeader { it.copy(paymentMethod = m) } }, { if (it == "CREDIT") "Not paid yet" else paymentMethodLabel(it) })
            if (header.paymentMethod != "CREDIT") {
                FormField(header.amountPaid ?: Calc.amount(vm.total()).toPlainString(), { v -> vm.updateHeader { it.copy(amountPaid = v) } },
                    "Amount paid ($cur)", keyboardType = KeyboardType.Decimal, error = e["amountPaid"] ?: e["payment.amount"])
            }
            FormField(header.notes, { v -> vm.updateHeader { it.copy(notes = v) } }, "Notes (optional)", singleLine = false, minLines = 2)

            if (lines.isNotEmpty()) {
                SectionCard("Summary") {
                    var gross = BigDecimal.ZERO; var disc = BigDecimal.ZERO; var tax = BigDecimal.ZERO
                    lines.mapNotNull(vm::figures).forEach { gross += it.gross; disc += it.discount; tax += it.tax }
                    TotalRow("Subtotal", Calc.money(gross, cur))
                    if (disc.signum() > 0) TotalRow("Discount", "− " + Calc.money(disc, cur))
                    if (tax.signum() > 0) TotalRow("Tax", Calc.money(tax, cur))
                    TotalRow("Purchase total", Calc.money(vm.total(), cur), bold = true)
                    TotalRow("Paid now", Calc.money(vm.paid(), cur))
                }
            }
            PrimaryButton("Review and save purchase", { confirm = true }, loading = state.submitting, enabled = lines.isNotEmpty())
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
    if (picking) ProductPickerDialog(vm.api, "Add product to purchase", { picking = false; vm.addProduct(it.id) }, { picking = false })
    if (confirm) {
        val text = lines.joinToString("\n") { l -> "${l.quantity} ${l.product.baseUnit.code} × ${l.product.name} @ ${l.unitCost}" } +
            "\n\nStock will increase by these quantities.\nTotal: ${Calc.money(vm.total(), settings.currencyCode)}"
        ConfirmDialog("Save purchase?", text, "Save purchase", onConfirm = { confirm = false; vm.submit() }, onDismiss = { confirm = false })
    }
}

// ------------------------------------------------------------------ detail

class PurchaseDetailViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    private val id: Long = saved.get<Long>("id")!!
    private val _state = MutableStateFlow(Loadable<Purchase>())
    val state = _state.asStateFlow()
    private val _currency = MutableStateFlow("INR")
    val currency = _currency.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch { (apiCall { api.settings() } as? ApiResult.Ok)?.let { _currency.value = it.value.currencyCode } }
    }

    fun load() = _state.load(viewModelScope) { api.purchase(id) }

    private fun act(msg: String, block: suspend () -> Purchase) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> { _state.value = Loadable(r.value, false); _messages.emit(msg) }
                is ApiResult.Err -> _messages.emit(r.message)
            }
            _busy.value = false
        }
    }

    fun pay(amount: Double, method: String, reference: String?) {
        val rid = UUID.randomUUID().toString()
        act("Payment recorded") { api.addPurchasePayment(id, PaymentRequest(amount, method, reference, rid)) }
    }

    fun cancel(reason: String) = act("Purchase cancelled and stock removed") { api.cancelPurchase(id, CancelPurchaseRequest(reason)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, s -> PurchaseDetailViewModel(c.api, s) }
    val state by vm.state.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var paying by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }

    Scaffold(topBar = { AppTopBar(state.data?.purchaseNumber ?: "Purchase", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = vm::load, modifier = Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { p ->
                val cancelled = p.status == "CANCELLED"
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.purchaseNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(Fmt.dateTime(p.purchasedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (cancelled) DocumentStatusChip(p.status) else PaymentStatusChip(p.paymentStatus)
                        }
                        LabelValue("Supplier", p.supplierName ?: "—")
                        LabelValue("Reference", p.referenceNumber)
                        LabelValue("Recorded by", p.recordedBy)
                        LabelValue("Notes", p.notes)
                        if (cancelled) ErrorBanner("Cancelled by ${p.cancelledBy ?: ""}: ${p.cancelReason ?: ""}")
                    }
                    SectionCard("Items") {
                        p.items.forEach { it ->
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(it.productName, fontWeight = FontWeight.Medium)
                                    Text("${Fmt.qty(it.quantity)} ${it.unitCode} × ${Fmt.money(it.unitCost, currency)}" +
                                        (it.lotNumber?.let { l -> " · lot $l" } ?: ""), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Fmt.money(it.lineTotal, currency), fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider()
                        }
                        TotalRow("Subtotal", Fmt.money(p.subtotal, currency))
                        if (p.discountTotal > 0) TotalRow("Discount", "− " + Fmt.money(p.discountTotal, currency))
                        if (p.taxTotal > 0) TotalRow("Tax", Fmt.money(p.taxTotal, currency))
                        TotalRow("Total", Fmt.money(p.total, currency), bold = true)
                        TotalRow("Paid", Fmt.money(p.amountPaid, currency))
                        if (!cancelled) TotalRow("Still to pay", Fmt.money(p.outstanding, currency), bold = true,
                            color = if (p.outstanding > 0) StatusColors.low else StatusColors.ok)
                    }
                    if (p.payments.isNotEmpty()) {
                        SectionCard("Payments") {
                            p.payments.forEach { pay -> TotalRow("${paymentMethodLabel(pay.method)} · ${Fmt.dateTime(pay.paidAt)}", Fmt.money(pay.amount, currency)) }
                        }
                    }
                    if (!cancelled) {
                        if (p.outstanding > 0) Button(onClick = { paying = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Record payment to supplier") }
                        OutlinedButton(onClick = { cancelling = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancel purchase") }
                    }
                }
            }
        }
    }
    val p = state.data
    if (paying && p != null) AddPaymentDialog(p.outstanding, currency, busy, { a, m, r -> paying = false; vm.pay(a, m, r) }, { paying = false })
    if (cancelling) {
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = { Text("Cancel this purchase?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The received quantities will be removed from stock. This fails if some of them were already sold or used.")
                    FormField(reason, { reason = it }, "Reason", required = true, placeholder = "e.g. Goods sent back to supplier")
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank() && !busy, onClick = { cancelling = false; vm.cancel(reason.trim()) }) {
                    Text("Cancel purchase", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { cancelling = false }) { Text("Keep") } },
        )
    }
}
