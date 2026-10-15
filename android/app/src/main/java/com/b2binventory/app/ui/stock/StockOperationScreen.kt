package com.b2binventory.app.ui.stock

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.sync.showSavedOffline
import com.b2binventory.app.data.sync.OfflineQueue
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Operation types offered in the app; TRANSFER uses the transfer endpoint. */
enum class OpType(val label: String, val permission: String, val needsDamagedReserved: Boolean = false,
                  val advanced: Boolean = false) {
    ADD("Add Stock", "STOCK_ADD"),
    REDUCE("Reduce Stock", "STOCK_REDUCE"),
    ADJUST("Correct stock", "STOCK_ADJUST", advanced = true),
    TRANSFER("Move between warehouses", "STOCK_TRANSFER", advanced = true),
    DAMAGE("Mark damaged", "STOCK_ADJUST", true, advanced = true),
    RESERVE("Reserve", "STOCK_ADJUST", true, advanced = true),
    RELEASE("Release reservation", "STOCK_ADJUST", true, advanced = true),
}

private val reasons = mapOf(
    OpType.ADD to listOf("New stock received", "Returned", "Found", "Production", "Other"),
    OpType.REDUCE to listOf("Issued / used", "Internal use", "Damaged write-off", "Expired", "Lost", "Adjustment", "Sample", "Other"),
    OpType.ADJUST to listOf("Counting error", "Data entry correction", "Damaged", "Lost", "Other"),
    OpType.TRANSFER to listOf("Transfer", "Replenish branch", "Other"),
    OpType.DAMAGE to listOf("Broken", "Defective", "Other"),
    OpType.RESERVE to listOf("Held for customer", "Held for project", "Other"),
    OpType.RELEASE to listOf("No longer needed", "Other"),
)

data class OperationForm(
    val type: OpType = OpType.ADD,
    val variantId: Long? = null,
    val warehouseId: Long? = null,
    val toWarehouseId: Long? = null,
    val quantity: String = "",
    val reason: String = "",
    val customReason: String = "",
    val notes: String = "",
    val lotNumber: String = "",
    val expiryDate: String = "",
    val batchId: Long? = null,
    val serials: String = "",
    val fromDamaged: Boolean = false,
)

data class OperationState(
    val submitting: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val reviewing: Boolean = false,
    val done: String? = null,
    val queued: Boolean = false,
)

class StockOperationViewModel(private val api: ApiService, saved: SavedStateHandle, private val offline: OfflineQueue) : ViewModel() {
    private val initialProduct = saved.get<String>("productId")?.toLongOrNull()
    private val _product = MutableStateFlow(Loadable<ProductDetail>(loading = initialProduct != null))
    val product = _product.asStateFlow()
    private val _form = MutableStateFlow(OperationForm(type = saved.get<String>("type")?.let { t -> OpType.entries.firstOrNull { it.name == t } } ?: OpType.ADD))
    val form = _form.asStateFlow()
    private val _state = MutableStateFlow(OperationState())
    val state = _state.asStateFlow()
    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()
    private val _settings = MutableStateFlow(BusinessSettings())
    val settings = _settings.asStateFlow()
    private val _batches = MutableStateFlow<List<BatchDto>>(emptyList())
    val batches = _batches.asStateFlow()
    private val _search = MutableStateFlow("")
    val search = _search.asStateFlow()
    private val _results = MutableStateFlow<List<ProductSummary>>(emptyList())
    val results = _results.asStateFlow()
    private var searchJob: Job? = null

    /** One id per submission attempt, so a retry or double tap is applied only once by the server. */
    private var requestId = java.util.UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            (apiCall { api.warehouses() } as? ApiResult.Ok)?.let { r ->
                _warehouses.value = r.value
                val default = r.value.firstOrNull { it.isDefault } ?: r.value.firstOrNull()
                _form.update { it.copy(warehouseId = it.warehouseId ?: default?.id) }
            }
            (apiCall { api.settings() } as? ApiResult.Ok)?.let { _settings.value = it.value }
        }
        if (initialProduct != null) selectProduct(initialProduct) else searchProducts("")
    }

    fun searchProducts(q: String) {
        _search.value = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            (apiCall { api.products(q = q.blankToNull(), size = 20) } as? ApiResult.Ok)?.let { _results.value = it.value.content }
        }
    }

    fun selectProduct(id: Long) {
        _product.value = Loadable(loading = true)
        viewModelScope.launch {
            when (val r = apiCall { api.product(id) }) {
                is ApiResult.Ok -> {
                    _product.value = Loadable(r.value, false)
                    val variant = r.value.variants.firstOrNull { it.isDefault && !it.archived } ?: r.value.variants.firstOrNull { !it.archived }
                    selectVariant(variant?.id)
                }
                is ApiResult.Err -> _product.value = Loadable(loading = false, error = r.message)
            }
        }
    }

    fun clearProduct() {
        _product.value = Loadable(loading = false)
        _form.update { it.copy(variantId = null) }
    }

    fun selectVariant(id: Long?) {
        _form.update { it.copy(variantId = id, batchId = null) }
        val p = _product.value.data ?: return
        if (id != null && p.trackBatches && _settings.value.trackBatches) {
            viewModelScope.launch { (apiCall { api.batches(id) } as? ApiResult.Ok)?.let { _batches.value = it.value } }
        } else {
            _batches.value = emptyList()
        }
    }

    fun update(transform: (OperationForm) -> OperationForm) {
        _form.update(transform)
        _state.update { it.copy(fieldErrors = emptyMap(), error = null) }
    }

    /** Validates and opens the review step. */
    fun review() {
        val f = _form.value
        val p = _product.value.data
        val qty = f.quantity.toDoubleOrNull()
        val batchTracked = p?.trackBatches == true && _settings.value.trackBatches
        val serialTracked = p?.trackSerials == true && _settings.value.trackSerials
        val receiving = f.type == OpType.ADD
        val errors = buildMap {
            if (p == null) put("product", "Choose a product")
            if (f.variantId == null) put("variant", "Choose a variant")
            if (f.warehouseId == null) put("warehouse", "Choose a warehouse")
            if (f.type == OpType.TRANSFER && (f.toWarehouseId == null || f.toWarehouseId == f.warehouseId)) {
                put("toWarehouse", "Choose a different destination warehouse")
            }
            if (qty == null || qty < 0 || (qty == 0.0 && f.type != OpType.ADJUST)) put("quantity", "Enter a valid quantity")
            else if (f.type in setOf(OpType.REDUCE, OpType.TRANSFER) && !f.fromDamaged && !_settings.value.allowNegativeStock) {
                val available = availableHere()
                if (qty > available) put("quantity", "Only ${Fmt.qty(available)} available here")
            }
            if (f.type == OpType.ADJUST && f.reasonText().isBlank()) put("reason", "A reason is required for adjustments")
            if (batchTracked && receiving && f.lotNumber.isBlank()) put("lotNumber", "Lot number is required")
            if (batchTracked && receiving && _settings.value.trackExpiry && f.expiryDate.isBlank() &&
                _batches.value.none { it.lotNumber.equals(f.lotNumber.trim(), true) }) put("expiryDate", "Expiry date is required for a new batch")
            if (batchTracked && !receiving && f.type != OpType.ADJUST && f.batchId == null) put("batch", "Choose a batch")
            if (serialTracked && f.type in setOf(OpType.ADD, OpType.REDUCE, OpType.TRANSFER) && qty != null &&
                f.serialList().size != qty.toInt()) put("serials", "Enter exactly ${Fmt.qty(qty)} serial numbers")
            if (serialTracked && f.type == OpType.ADJUST) put("quantity", "Use Add or Reduce with serial numbers for this product")
        }
        _state.value = OperationState(fieldErrors = errors, reviewing = errors.isEmpty(),
            error = if (errors.isNotEmpty()) "Please correct the highlighted fields" else null)
    }

    fun cancelReview() = _state.update { it.copy(reviewing = false) }

    /** Available quantity for the chosen variant, warehouse and batch. */
    fun availableHere(): Double {
        val p = _product.value.data ?: return 0.0
        val f = _form.value
        return p.balances.filter { it.warehouseId == f.warehouseId && it.variantId == f.variantId &&
            (f.batchId == null || it.batchId == f.batchId) }.sumOf { it.available }
    }

    fun submit() {
        if (_state.value.submitting) return
        val f = _form.value
        val qty = f.quantity.toDoubleOrNull() ?: return
        val variantId = f.variantId ?: return
        _state.value = OperationState(submitting = true, reviewing = false)
        viewModelScope.launch {
            val serials = f.serialList().ifEmpty { null }
            val transfer = if (f.type == OpType.TRANSFER) TransferRequest(variantId, f.warehouseId!!, f.toWarehouseId!!, qty,
                f.batchId, null, serials, f.reasonText().blankToNull(), f.notes.blankToNull(), requestId) else null
            val operation = if (transfer == null) StockOperationRequest(f.type.name, variantId, f.warehouseId, qty,
                f.reasonText().blankToNull(), f.notes.blankToNull(),
                batchId = if (f.type == OpType.ADD) null else f.batchId,
                lotNumber = if (f.type == OpType.ADD) f.lotNumber.blankToNull() else null,
                expiryDate = if (f.type == OpType.ADD) f.expiryDate.blankToNull() else null,
                serials = serials, fromDamaged = f.type == OpType.REDUCE && f.fromDamaged, clientRequestId = requestId) else null
            val result = apiCall {
                if (transfer != null) api.transfer(transfer) else api.stockOperation(operation!!)
            }
            val detail = "${f.type.name.lowercase().replaceFirstChar { it.uppercase() }} · ${_product.value.data?.name.orEmpty()} × ${Fmt.qty(qty)}"
            if (result is ApiResult.Err && result.offline &&
                (if (transfer != null) offline.transfer(transfer, detail) else offline.stockOperation(operation!!, detail))) {
                requestId = java.util.UUID.randomUUID().toString()
                _state.value = OperationState(queued = true)
                return@launch
            }
            _state.value = when (result) {
                is ApiResult.Ok -> {
                    requestId = java.util.UUID.randomUUID().toString()
                    val m = result.value.movements.firstOrNull()
                    OperationState(done = if (m != null) "Stock updated · ${m.productName} now ${Fmt.qty(m.quantityAfter)} ${m.unitCode}"
                        else "Stock updated")
                }
                is ApiResult.Err -> OperationState(error = result.message, fieldErrors = result.fieldErrors)
            }
        }
    }
}

fun OperationForm.reasonText(): String = if (reason == "Other" || reason.isBlank()) customReason.trim() else reason

fun OperationForm.serialList(): List<String> = serials.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockOperationScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val vm = appViewModel { c, s -> StockOperationViewModel(c.api, s, OfflineQueue(c)) }
    val product by vm.product.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val warehouses by vm.warehouses.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val batches by vm.batches.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val snackbar = remember { SnackbarHostState() }
    val queuedContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.queued) { if (state.queued) { showSavedOffline(queuedContext); onDone() } }
    LaunchedEffect(state.done) {
        state.done?.let {
            snackbar.showSnackbar(tr(it))
            onDone()
        }
    }
    val e = state.fieldErrors
    val types = OpType.entries.filter { me?.can(it.permission) == true && (!it.needsDamagedReserved || settings.trackDamagedReserved) }

    var showAdvanced by rememberSaveable { mutableStateOf(form.type.advanced) }
    val title = when (form.type) {
        OpType.ADD -> "Add stock"
        OpType.REDUCE -> "Reduce stock"
        else -> form.type.label
    }

    Scaffold(topBar = { AppTopBar(title, onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        FormColumn(padding) {
            ErrorBanner(state.error)
            val p = product.data
            if (p == null) {
                if (product.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Text("Choose a product", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(search, vm::searchProducts, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search name, SKU or barcode") }, isError = e["product"] != null)
                    product.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    results.forEach { r ->
                        ListItem(
                            headlineContent = { Text(r.name) },
                            supportingContent = { Text(listOfNotNull(r.sku, r.categoryName).joinToString(" · ").ifEmpty { r.unitCode }) },
                            trailingContent = { Text("${Fmt.qty(r.stock.available)} ${r.unitCode}") },
                            modifier = Modifier.clickable { vm.selectProduct(r.id) },
                        )
                    }
                    if (results.isEmpty()) Text("No products found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@FormColumn
            }

            SectionCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${Fmt.qty(p.stock.available)} ${p.baseUnit.code} available in total",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = vm::clearProduct) { Text("Change") }
                }
            }
            if (p.archived) {
                ErrorBanner("This product is archived. Restore it before changing stock.")
                return@FormColumn
            }

            Text("What do you want to do?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.filter { !it.advanced || showAdvanced || form.type == it }.forEach { t ->
                    FilterChip(selected = form.type == t, onClick = { vm.update { it.copy(type = t, reason = "", batchId = null) } },
                        label = { Text(t.label) })
                }
                if (!showAdvanced && types.any { it.advanced }) {
                    AssistChip(onClick = { showAdvanced = true }, label = { Text("More options") })
                }
            }

            val activeVariants = p.variants.filter { !it.archived }
            if (activeVariants.size > 1) {
                DropdownField("Variant", activeVariants, activeVariants.firstOrNull { it.id == form.variantId },
                    { vm.selectVariant(it.id) }, { it.name }, error = e["variant"], required = true)
            }
            DropdownField(if (form.type == OpType.TRANSFER) "From warehouse" else "Warehouse", warehouses,
                warehouses.firstOrNull { it.id == form.warehouseId }, { w -> vm.update { it.copy(warehouseId = w.id, batchId = null) } },
                { it.name }, error = e["warehouse"], required = true)
            if (form.type == OpType.TRANSFER) {
                DropdownField("To warehouse", warehouses.filter { it.id != form.warehouseId },
                    warehouses.firstOrNull { it.id == form.toWarehouseId }, { w -> vm.update { it.copy(toWarehouseId = w.id) } },
                    { it.name }, error = e["toWarehouse"], required = true)
            }

            val here = p.balances.filter { it.warehouseId == form.warehouseId && it.variantId == form.variantId }
            val onHand = here.sumOf { it.onHand }
            val available = here.sumOf { it.available }
            val reserved = here.sumOf { it.reserved }
            val damaged = here.sumOf { it.damaged }
            Text("In this warehouse: ${Fmt.qty(onHand)} on hand · ${Fmt.qty(available)} available" +
                (if (reserved > 0) " · ${Fmt.qty(reserved)} reserved" else "") +
                (if (damaged > 0) " · ${Fmt.qty(damaged)} damaged" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val batchTracked = p.trackBatches && settings.trackBatches
            if (batchTracked && form.type == OpType.ADD) {
                FormField(form.lotNumber, { v -> vm.update { it.copy(lotNumber = v) } }, "Lot number", required = true, error = e["lotNumber"],
                    supporting = batches.takeIf { it.isNotEmpty() }?.let { "Existing: " + it.joinToString { b -> b.lotNumber } })
                if (settings.trackExpiry) {
                    DateField("Expiry date", form.expiryDate, { v -> vm.update { it.copy(expiryDate = v) } }, error = e["expiryDate"])
                }
            } else if (batchTracked) {
                DropdownField("Batch", batches, batches.firstOrNull { it.id == form.batchId }, { b -> vm.update { it.copy(batchId = b.id) } },
                    { b -> "${b.lotNumber}" + (b.expiryDate?.let { " · exp ${Fmt.date(it)}" } ?: "") + " · ${Fmt.qty(b.onHand)}" },
                    error = e["batch"] ?: e["lotNumber"], required = form.type != OpType.ADJUST)
            }

            val qtyLabel = when (form.type) {
                OpType.ADJUST -> "Correct quantity on hand"
                OpType.TRANSFER -> "Quantity to transfer"
                else -> "Quantity"
            }
            FormField(form.quantity, { v -> vm.update { it.copy(quantity = v) } }, "$qtyLabel (${p.baseUnit.code})", required = true,
                keyboardType = KeyboardType.Decimal, error = e["quantity"], placeholder = "e.g. 10")
            // Show the effect right away so the user knows what will remain.
            val typedQty = form.quantity.toDoubleOrNull()
            if (typedQty != null && form.type in setOf(OpType.ADD, OpType.REDUCE, OpType.TRANSFER, OpType.ADJUST)) {
                val resulting = when (form.type) {
                    OpType.ADD -> onHand + typedQty
                    OpType.ADJUST -> typedQty
                    else -> onHand - typedQty
                }
                val tooMuch = form.type in setOf(OpType.REDUCE, OpType.TRANSFER) && !form.fromDamaged &&
                    typedQty > available && !settings.allowNegativeStock
                Text(
                    if (tooMuch) "Not enough stock: only ${Fmt.qty(available)} ${p.baseUnit.code} available here"
                    else "On hand here: ${Fmt.qty(onHand)} → ${Fmt.qty(resulting)} ${p.baseUnit.code}",
                    color = if (tooMuch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                )
            }
            if (form.type == OpType.REDUCE && settings.trackDamagedReserved && damaged > 0) {
                SwitchRow("Write off damaged stock", form.fromDamaged, { v -> vm.update { it.copy(fromDamaged = v) } },
                    "Reduce from the ${Fmt.qty(damaged)} marked as damaged")
            }
            val serialTracked = p.trackSerials && settings.trackSerials
            if (serialTracked && form.type != OpType.ADJUST) {
                FormField(form.serials, { v -> vm.update { it.copy(serials = v) } }, "Serial numbers",
                    required = form.type in setOf(OpType.ADD, OpType.REDUCE, OpType.TRANSFER), singleLine = false, minLines = 3,
                    error = e["serials"], supporting = "One per line (${form.serialList().size} entered)")
            }

            DropdownField("Reason", reasons[form.type].orEmpty(), form.reason.takeIf { it.isNotBlank() },
                { r -> vm.update { it.copy(reason = r) } }, { it }, error = e["reason"], required = form.type == OpType.ADJUST)
            if (form.reason == "Other" || (form.reason.isBlank() && form.type == OpType.ADJUST)) {
                FormField(form.customReason, { v -> vm.update { it.copy(customReason = v) } }, "Describe the reason", error = e["reason"])
            }
            FormField(form.notes, { v -> vm.update { it.copy(notes = v) } }, "Notes", singleLine = false, minLines = 2)
            PrimaryButton("Review and confirm", vm::review, loading = state.submitting)

            if (state.reviewing) {
                val qty = form.quantity.toDoubleOrNull() ?: 0.0
                val after = when (form.type) {
                    OpType.ADD -> onHand + qty
                    OpType.REDUCE, OpType.TRANSFER -> onHand - qty
                    OpType.ADJUST -> qty
                    else -> onHand
                }
                val warehouseName = warehouses.firstOrNull { it.id == form.warehouseId }?.name ?: ""
                val summary = buildString {
                    append("${form.type.label}: ${Fmt.qty(qty)} ${p.baseUnit.code} of ${p.name}")
                    activeVariants.firstOrNull { it.id == form.variantId }?.takeIf { activeVariants.size > 1 }?.let { append(" (${it.name})") }
                    append("\n")
                    if (form.type == OpType.TRANSFER) {
                        append("From $warehouseName to ${warehouses.firstOrNull { it.id == form.toWarehouseId }?.name}\n")
                    } else {
                        append("Warehouse: $warehouseName\n")
                    }
                    if (form.type in setOf(OpType.ADD, OpType.REDUCE, OpType.ADJUST, OpType.TRANSFER)) {
                        append("On hand here: ${Fmt.qty(onHand)} → ${Fmt.qty(after)}\n")
                    }
                    form.reasonText().takeIf { it.isNotBlank() }?.let { append("Reason: $it\n") }
                    append("Recorded as: ${me?.fullName}")
                }
                ConfirmDialog("Confirm stock update", summary, "Confirm", onConfirm = vm::submit, onDismiss = vm::cancelReview)
            }
        }
    }
}
