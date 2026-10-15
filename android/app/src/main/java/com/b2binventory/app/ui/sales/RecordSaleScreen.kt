package com.b2binventory.app.ui.sales

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** One product row in the sale. Text fields keep exactly what the user typed. */
data class CartLine(
    val key: String = UUID.randomUUID().toString(),
    val product: ProductDetail,
    val variantId: Long,
    val quantity: String = "1",
    val unitPrice: String = "",
    val discount: String = "",
    val taxRate: String = "",
    val batchId: Long? = null,
    val serials: String = "",
    val batches: List<BatchDto> = emptyList(),
)

data class SaleHeader(
    val warehouseId: Long? = null,
    val customer: Party? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val saveCustomer: Boolean = false,
    val invoiceNumber: String = "",
    val date: String = LocalDate.now().toString(),
    val time: String = LocalTime.now().withSecond(0).withNano(0).toString(),
    val paymentMethod: String = "CASH",
    /** null = follow the total automatically. */
    val amountReceived: String? = null,
    val paymentReference: String = "",
    val notes: String = "",
)

data class RecordState(
    val submitting: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val reviewing: Boolean = false,
    val savedId: Long? = null,
    /** Saved on the phone because the server could not be reached; it syncs later. */
    val queued: Boolean = false,
)

class RecordSaleViewModel(val api: ApiService, saved: SavedStateHandle, private val offline: OfflineQueue) : ViewModel() {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines = _lines.asStateFlow()
    private val _header = MutableStateFlow(SaleHeader())
    val header = _header.asStateFlow()
    private val _state = MutableStateFlow(RecordState())
    val state = _state.asStateFlow()
    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()
    private val _settings = MutableStateFlow(BusinessSettings())
    val settings = _settings.asStateFlow()
    private val _customers = MutableStateFlow<List<Party>>(emptyList())
    val customers = _customers.asStateFlow()
    private val _adding = MutableStateFlow(false)
    val adding = _adding.asStateFlow()

    /** One id per sale attempt, so a retry or double tap is recorded only once by the server. */
    private val requestId = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            (apiCall { api.warehouses() } as? ApiResult.Ok)?.let { r ->
                _warehouses.value = r.value
                val default = r.value.firstOrNull { it.isDefault } ?: r.value.firstOrNull()
                _header.update { it.copy(warehouseId = it.warehouseId ?: default?.id) }
            }
            (apiCall { api.settings() } as? ApiResult.Ok)?.let { _settings.value = it.value }
            (apiCall { api.customers() } as? ApiResult.Ok)?.let { _customers.value = it.value }
        }
        saved.get<String>("productId")?.toLongOrNull()?.let(::addProduct)
    }

    fun addProduct(productId: Long) {
        _adding.value = true
        viewModelScope.launch {
            when (val r = apiCall { api.product(productId) }) {
                is ApiResult.Ok -> {
                    val p = r.value
                    val variant = p.variants.firstOrNull { it.isDefault && !it.archived } ?: p.variants.firstOrNull { !it.archived }
                    if (p.archived || variant == null) {
                        _state.update { it.copy(error = "${p.name} is archived and cannot be sold") }
                    } else {
                        val existing = _lines.value.firstOrNull { it.variantId == variant.id }
                        if (existing != null) {
                            // Selecting the same product again adds one more instead of a duplicate row.
                            val q = (Calc.dec(existing.quantity) ?: BigDecimal.ZERO).add(BigDecimal.ONE)
                            updateLine(existing.key) { it.copy(quantity = q.stripTrailingZeros().toPlainString()) }
                        } else {
                            val line = CartLine(product = p, variantId = variant.id,
                                unitPrice = p.salePrice?.let { Fmt.qty(it) } ?: "",
                                taxRate = p.taxRate?.let { Fmt.qty(it) } ?: "")
                            _lines.update { it + line }
                            loadBatches(line)
                        }
                        _state.update { it.copy(error = null) }
                    }
                }
                is ApiResult.Err -> _state.update { it.copy(error = r.message) }
            }
            _adding.value = false
        }
    }

    private fun loadBatches(line: CartLine) {
        if (!(line.product.trackBatches && _settings.value.trackBatches)) return
        viewModelScope.launch {
            (apiCall { api.batches(line.variantId) } as? ApiResult.Ok)?.let { r -> updateLine(line.key) { it.copy(batches = r.value) } }
        }
    }

    fun updateLine(key: String, transform: (CartLine) -> CartLine) {
        _lines.update { list -> list.map { if (it.key == key) transform(it) else it } }
        _state.update { it.copy(fieldErrors = emptyMap(), error = null) }
    }

    fun setVariant(key: String, variantId: Long) {
        updateLine(key) { it.copy(variantId = variantId, batchId = null, batches = emptyList()) }
        _lines.value.firstOrNull { it.key == key }?.let(::loadBatches)
    }

    fun removeLine(key: String) = _lines.update { list -> list.filterNot { it.key == key } }

    fun updateHeader(transform: (SaleHeader) -> SaleHeader) {
        _header.update(transform)
        _state.update { it.copy(fieldErrors = emptyMap(), error = null) }
    }

    /** Stock available for the line in the chosen warehouse (and batch). */
    fun available(line: CartLine): BigDecimal {
        val wid = _header.value.warehouseId
        return line.product.balances.filter { it.warehouseId == wid && it.variantId == line.variantId &&
            (line.batchId == null || it.batchId == line.batchId) }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(BigDecimal.valueOf(b.available)) }
    }

    fun figures(line: CartLine): Calc.Line? {
        val q = Calc.dec(line.quantity) ?: return null
        val p = Calc.dec(line.unitPrice) ?: return null
        return Calc.line(q, p, Calc.dec(line.discount) ?: BigDecimal.ZERO, Calc.dec(line.taxRate) ?: BigDecimal.ZERO)
    }

    fun total(): BigDecimal = _lines.value.mapNotNull(::figures).fold(BigDecimal.ZERO) { a, l -> a.add(l.total) }

    fun amountReceived(): BigDecimal {
        val h = _header.value
        if (h.paymentMethod == "CREDIT") return BigDecimal.ZERO
        return h.amountReceived?.let { Calc.dec(it) } ?: total()
    }

    fun review() {
        val lines = _lines.value
        val s = _settings.value
        val errors = buildMap {
            if (lines.isEmpty()) put("items", "Add at least one product")
            lines.forEach { l ->
                val q = Calc.dec(l.quantity)
                val price = Calc.dec(l.unitPrice)
                val f = figures(l)
                if (q == null || q.signum() <= 0) put("${l.key}.quantity", "Enter how many")
                else if (q > available(l) && !s.allowNegativeStock) put("${l.key}.quantity", "Only ${Fmt.qty(available(l).toDouble())} in stock")
                else if (q.stripTrailingZeros().scale() > l.product.baseUnit.decimals) {
                    put("${l.key}.quantity", if (l.product.baseUnit.decimals == 0) "Use whole numbers" else "Too many decimals")
                }
                if (price == null || price.signum() < 0) put("${l.key}.unitPrice", "Enter the selling price")
                val d = Calc.dec(l.discount)
                if (l.discount.isNotBlank() && (d == null || d.signum() < 0)) put("${l.key}.discount", "Enter a valid discount")
                else if (f != null && f.discount > f.gross) put("${l.key}.discount", "More than the line amount")
                val t = Calc.dec(l.taxRate)
                if (l.taxRate.isNotBlank() && (t == null || t.signum() < 0 || t > BigDecimal(100))) put("${l.key}.taxRate", "0 to 100")
                if (l.product.trackBatches && s.trackBatches && l.batchId == null) put("${l.key}.batch", "Choose a batch")
                if (l.product.trackSerials && s.trackSerials && q != null && serialList(l.serials).size != q.toInt()) {
                    put("${l.key}.serials", "Enter ${Fmt.qty(q.toDouble())} serial numbers")
                }
            }
            val received = amountReceived()
            if (_header.value.amountReceived != null && Calc.dec(_header.value.amountReceived!!) == null) put("amountReceived", "Enter an amount")
            else if (received.signum() < 0) put("amountReceived", "Cannot be negative")
            else if (received > total()) put("amountReceived", "More than the total")
            if (dateTime() == null) put("date", "Choose a valid date and time")
            else if (dateTime()!!.isAfter(LocalDateTime.now().plusMinutes(5))) put("date", "Cannot be in the future")
        }
        _state.value = RecordState(fieldErrors = errors, reviewing = errors.isEmpty(),
            error = if (errors.isNotEmpty()) errors["items"] ?: "Please correct the highlighted fields" else null)
    }

    fun cancelReview() = _state.update { it.copy(reviewing = false) }

    private fun dateTime(): LocalDateTime? = runCatching {
        LocalDateTime.of(LocalDate.parse(_header.value.date), LocalTime.parse(_header.value.time))
    }.getOrNull()

    fun submit() {
        if (_state.value.submitting) return
        val h = _header.value
        val lines = _lines.value
        _state.value = RecordState(submitting = true)
        viewModelScope.launch {
            val received = amountReceived()
            val request = SaleRequest(
                items = lines.map { l ->
                    SaleLineRequest(l.variantId, h.warehouseId, l.quantity.trim().toDouble(), l.unitPrice.trim().toDouble(),
                        Calc.dec(l.discount)?.toDouble(), Calc.dec(l.taxRate)?.toDouble() ?: 0.0, l.batchId,
                        serialList(l.serials).ifEmpty { null })
                },
                customerId = h.customer?.id,
                customerName = (h.customer?.name ?: h.customerName).blankToNull(),
                customerPhone = (h.customer?.phone ?: h.customerPhone).blankToNull(),
                saveCustomer = h.customer == null && h.saveCustomer,
                invoiceNumber = h.invoiceNumber.blankToNull(),
                soldAt = dateTime()?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
                payment = if (received.signum() > 0) PaymentInput(h.paymentMethod, received.toDouble(), h.paymentReference.blankToNull()) else null,
                notes = h.notes.blankToNull(),
                clientRequestId = requestId,
            )
            _state.value = when (val r = apiCall { api.recordSale(request) }) {
                is ApiResult.Ok -> RecordState(savedId = r.value.id)
                is ApiResult.Err -> if (r.offline && offline.sale(request, lines.joinToString { "${it.product.name} × ${it.quantity.trim()}" })) {
                    RecordState(queued = true)
                } else RecordState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }
}

fun serialList(text: String): List<String> = text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

@Composable
fun RecordSaleScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel { c, s -> RecordSaleViewModel(c.api, s, OfflineQueue(c)) }
    val lines by vm.lines.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val warehouses by vm.warehouses.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val showCost = me?.can("ANALYTICS_VIEW") == true || me?.can("PRODUCT_EDIT") == true || me?.can("PURCHASE_MANAGE") == true
    var picking by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    val queuedContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.queued) { if (state.queued) { showSavedOffline(queuedContext); onBack() } }
    val e = state.fieldErrors
    val cur = settings.currencyCode
    val back = { if (lines.isNotEmpty()) discardConfirm = true else onBack() }

    Scaffold(topBar = { AppTopBar("Record sale", onBack = back) }) { padding ->
        FormColumn(padding) {
            ErrorBanner(state.error)

            // Step 1: products
            Text("1. Products sold", style = MaterialTheme.typography.titleMedium)
            if (warehouses.size > 1) {
                DropdownField("Sell from warehouse", warehouses, warehouses.firstOrNull { it.id == header.warehouseId },
                    { w -> vm.updateHeader { it.copy(warehouseId = w.id) } }, { it.name })
            }
            if (lines.isEmpty() && !adding) {
                EmptyState("No products added", "Tap \"Add product\" to start this sale.", icon = Icons.Default.ShoppingCart)
            }
            lines.forEach { line ->
                LineCard(vm, line, settings, e, cur, showCost)
            }
            if (adding) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (lines.isEmpty()) "Add product" else "Add another product")
            }

            // Step 2: customer and payment
            Text("2. Customer and payment", style = MaterialTheme.typography.titleMedium)
            PartyField("Customer (optional)", customers, header.customer, header.customerName,
                { c -> vm.updateHeader { it.copy(customer = c) } }, { v -> vm.updateHeader { it.copy(customerName = v) } })
            if (header.customer == null && header.customerName.isNotBlank()) {
                FormField(header.customerPhone, { v -> vm.updateHeader { it.copy(customerPhone = v) } }, "Customer phone (optional)",
                    keyboardType = KeyboardType.Phone)
                SwitchRow("Save this customer", header.saveCustomer, { v -> vm.updateHeader { it.copy(saveCustomer = v) } },
                    "Pick them quickly next time")
            }
            DropdownField("Payment method", paymentMethods, header.paymentMethod,
                { m -> vm.updateHeader { it.copy(paymentMethod = m, amountReceived = if (m == "CREDIT") null else it.amountReceived) } },
                ::paymentMethodLabel)
            if (header.paymentMethod != "CREDIT") {
                FormField(header.amountReceived ?: Calc.amount(vm.total()).toPlainString(),
                    { v -> vm.updateHeader { it.copy(amountReceived = v) } }, "Amount received ($cur)",
                    keyboardType = KeyboardType.Decimal, error = e["amountReceived"] ?: e["payment.amount"],
                    supporting = "Change it if the customer paid only part now")
                if (header.paymentMethod != "CASH") {
                    FormField(header.paymentReference, { v -> vm.updateHeader { it.copy(paymentReference = v) } },
                        "Payment reference (optional)", placeholder = "e.g. UPI transaction id")
                }
            } else {
                Text("The full amount will be recorded as unpaid. You can add payments later.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Optional details, out of the way.
            var more by remember { mutableStateOf(false) }
            TextButton(onClick = { more = !more }) {
                Text(if (more) "Hide date, invoice number and notes" else "Date, invoice number and notes")
                Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (more) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Sale date", header.date, { v -> vm.updateHeader { it.copy(date = v.ifBlank { LocalDate.now().toString() }) } },
                        Modifier.weight(1f), error = e["date"] ?: e["soldAt"])
                    TimeField("Time", header.time, { v -> vm.updateHeader { it.copy(time = v) } }, Modifier.weight(1f))
                }
                FormField(header.invoiceNumber, { v -> vm.updateHeader { it.copy(invoiceNumber = v) } }, "Invoice number (optional)",
                    error = e["invoiceNumber"], supporting = "Leave empty to number it automatically")
                FormField(header.notes, { v -> vm.updateHeader { it.copy(notes = v) } }, "Notes (optional)", singleLine = false, minLines = 2)
            }

            // Step 3: summary
            if (lines.isNotEmpty()) {
                Text("3. Summary", style = MaterialTheme.typography.titleMedium)
                SaleSummaryCard(vm, lines, cur, showCost)
            }
            PrimaryButton("Review and confirm sale", vm::review, loading = state.submitting, enabled = lines.isNotEmpty())
            OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth(), enabled = !state.submitting) { Text("Cancel") }
        }
    }

    if (picking) {
        ProductPickerDialog(vm.api, "Add product to sale", onPick = { picking = false; vm.addProduct(it.id) }, onDismiss = { picking = false })
    }
    if (state.reviewing) {
        val total = vm.total()
        val received = vm.amountReceived()
        val text = buildString {
            lines.forEach { l ->
                val f = vm.figures(l)
                append("${l.quantity} ${l.product.baseUnit.code} × ${l.product.name}")
                f?.let { append(" = ${Calc.money(it.total, cur)}") }
                append("\n   Stock ${Fmt.qty(vm.available(l).toDouble())} → ${Fmt.qty(vm.available(l).subtract(Calc.dec(l.quantity) ?: BigDecimal.ZERO).toDouble())}\n")
            }
            append("\nTotal: ${Calc.money(total, cur)}")
            append("\nReceived now: ${Calc.money(received, cur)} (${paymentMethodLabel(header.paymentMethod)})")
            if (received < total) append("\nStill to pay: ${Calc.money(total.subtract(received), cur)}")
            (header.customer?.name ?: header.customerName.blankToNull())?.let { append("\nCustomer: $it") }
        }
        ConfirmDialog("Confirm sale", text, "Record sale", onConfirm = vm::submit, onDismiss = vm::cancelReview)
    }
    if (discardConfirm) {
        ConfirmDialog("Discard this sale?", "The products you added will be removed. Nothing has been saved yet.", "Discard", true,
            onConfirm = { discardConfirm = false; onBack() }, onDismiss = { discardConfirm = false })
    }
}

@Composable
private fun LineCard(vm: RecordSaleViewModel, line: CartLine, settings: BusinessSettings, e: Map<String, String>, cur: String,
                     showCost: Boolean) {
    val p = line.product
    val available = vm.available(line)
    val qty = Calc.dec(line.quantity) ?: BigDecimal.ZERO
    val remaining = available.subtract(qty)
    val f = vm.figures(line)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                    Text("${Fmt.qty(available.toDouble())} ${p.baseUnit.code} in stock" +
                        (p.salePrice?.let { " · price ${Fmt.money(it, cur)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.removeLine(line.key) }) { Icon(Icons.Default.Delete, "Remove ${p.name}") }
            }
            val variants = p.variants.filter { !it.archived }
            if (variants.size > 1) {
                DropdownField("Variant", variants, variants.firstOrNull { it.id == line.variantId }, { vm.setVariant(line.key, it.id) }, { it.name })
            }
            if (p.trackBatches && settings.trackBatches) {
                DropdownField("Batch", line.batches, line.batches.firstOrNull { it.id == line.batchId },
                    { b -> vm.updateLine(line.key) { it.copy(batchId = b.id) } },
                    { b -> b.lotNumber + (b.expiryDate?.let { " · exp ${Fmt.date(it)}" } ?: "") + " · ${Fmt.qty(b.onHand)}" },
                    error = e["${line.key}.batch"], required = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Quantity stepper plus a typed value for larger or decimal quantities.
                FilledTonalIconButton(onClick = {
                    val q = (qty - BigDecimal.ONE).max(BigDecimal.ONE)
                    vm.updateLine(line.key) { it.copy(quantity = q.stripTrailingZeros().toPlainString()) }
                }) { Icon(Icons.Default.Remove, "Decrease quantity") }
                FormField(line.quantity, { v -> vm.updateLine(line.key) { it.copy(quantity = v) } }, "Qty (${p.baseUnit.code})",
                    Modifier.weight(1f), keyboardType = KeyboardType.Decimal, error = e["${line.key}.quantity"], required = true)
                FilledTonalIconButton(onClick = {
                    vm.updateLine(line.key) { it.copy(quantity = qty.add(BigDecimal.ONE).stripTrailingZeros().toPlainString()) }
                }) { Icon(Icons.Default.Add, "Increase quantity") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(line.unitPrice, { v -> vm.updateLine(line.key) { it.copy(unitPrice = v) } }, "Price / ${p.baseUnit.code}",
                    Modifier.weight(1f), keyboardType = KeyboardType.Decimal, error = e["${line.key}.unitPrice"], required = true)
                FormField(line.discount, { v -> vm.updateLine(line.key) { it.copy(discount = v) } }, "Discount",
                    Modifier.weight(1f), keyboardType = KeyboardType.Decimal, error = e["${line.key}.discount"], placeholder = "0")
                FormField(line.taxRate, { v -> vm.updateLine(line.key) { it.copy(taxRate = v) } }, "Tax %",
                    Modifier.weight(0.8f), keyboardType = KeyboardType.Decimal, error = e["${line.key}.taxRate"], placeholder = "0")
            }
            if (p.trackSerials && settings.trackSerials) {
                FormField(line.serials, { v -> vm.updateLine(line.key) { it.copy(serials = v) } }, "Serial numbers", required = true,
                    singleLine = false, minLines = 2, error = e["${line.key}.serials"], supporting = "One per line")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val low = remaining.signum() < 0
                Text("After sale: ${Fmt.qty(remaining.toDouble())} ${p.baseUnit.code} left",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                f?.let { Text(Calc.money(it.total, cur), fontWeight = FontWeight.SemiBold) }
            }
            val price = Calc.dec(line.unitPrice)
            val cost = Calc.dec(p.pricing?.unitCost)
            if (showCost && price != null && cost != null && price < cost) {
                Text("Selling below cost (${Fmt.money(cost.toDouble(), cur)})", color = StatusColors.low,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SaleSummaryCard(vm: RecordSaleViewModel, lines: List<CartLine>, cur: String, showCost: Boolean) {
    var gross = BigDecimal.ZERO
    var discount = BigDecimal.ZERO
    var tax = BigDecimal.ZERO
    var total = BigDecimal.ZERO
    var net = BigDecimal.ZERO
    var cost = BigDecimal.ZERO
    var costKnown = true
    lines.forEach { l ->
        val f = vm.figures(l) ?: return@forEach
        gross += f.gross
        discount += f.discount
        tax += f.tax
        total += f.total
        net += f.net
        val c = Calc.dec(l.product.pricing?.unitCost)
        if (c == null) costKnown = false else cost += Calc.amount(c.multiply(Calc.dec(l.quantity) ?: BigDecimal.ZERO))
    }
    SectionCard {
        lines.forEach { l ->
            val q = Calc.dec(l.quantity) ?: BigDecimal.ZERO
            val a = vm.available(l)
            Text("${l.product.name}: ${Fmt.qty(a.toDouble())} → ${Fmt.qty(a.subtract(q).toDouble())} ${l.product.baseUnit.code}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        TotalRow("Subtotal", Calc.money(gross, cur))
        if (discount.signum() > 0) TotalRow("Discount", "− " + Calc.money(discount, cur))
        if (tax.signum() > 0) TotalRow("Tax", Calc.money(tax, cur))
        TotalRow("Sale total", Calc.money(total, cur), bold = true)
        if (showCost) {
            if (costKnown) {
                TotalRow("Cost of goods", Calc.money(cost, cur))
                TotalRow("Estimated gross profit", Calc.money(net.subtract(cost), cur),
                    color = if (net >= cost) StatusColors.ok else StatusColors.out)
            } else {
                Text("Profit unavailable: some products have no purchase price.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val received = vm.amountReceived()
        TotalRow("Received now", Calc.money(received, cur))
        if (received < total) TotalRow("Still to pay", Calc.money(total.subtract(received), cur), color = StatusColors.low)
    }
}

/** Time picker field; value is "HH:mm". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(value = value.take(5), onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.Schedule, "Pick time") } },
        modifier = modifier.fillMaxWidth())
    if (open) {
        val t = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.now())
        val state = rememberTimePickerState(t.hour, t.minute)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(LocalTime.of(state.hour, state.minute).format(DateTimeFormatter.ofPattern("HH:mm")))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
