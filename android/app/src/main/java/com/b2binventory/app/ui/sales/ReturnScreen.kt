package com.b2binventory.app.ui.sales

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class ReturnLine(val quantity: String = "", val resalable: Boolean = true)

class ReturnViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    private val saleId: Long = saved.get<Long>("id")!!
    private val _sale = MutableStateFlow(Loadable<Sale>())
    val sale = _sale.asStateFlow()
    private val _lines = MutableStateFlow<Map<Long, ReturnLine>>(emptyMap())
    val lines = _lines.asStateFlow()
    private val _reason = MutableStateFlow(returnReasons.first())
    val reason = _reason.asStateFlow()
    private val _otherReason = MutableStateFlow("")
    val otherReason = _otherReason.asStateFlow()
    /** null = follow the suggested refund. */
    private val _refund = MutableStateFlow<String?>(null)
    val refund = _refund.asStateFlow()
    private val _refundMethod = MutableStateFlow("CASH")
    val refundMethod = _refundMethod.asStateFlow()
    private val _state = MutableStateFlow(RecordState())
    val state = _state.asStateFlow()
    private val _currency = MutableStateFlow("INR")
    val currency = _currency.asStateFlow()
    private val requestId = UUID.randomUUID().toString()

    init {
        _sale.load(viewModelScope) { api.sale(saleId) }
        viewModelScope.launch { (apiCall { api.settings() } as? ApiResult.Ok)?.let { _currency.value = it.value.currencyCode } }
    }

    fun setLine(itemId: Long, transform: (ReturnLine) -> ReturnLine) {
        _lines.update { it + (itemId to transform(it[itemId] ?: ReturnLine())) }
        _state.update { it.copy(fieldErrors = emptyMap(), error = null) }
    }

    fun setReason(r: String) { _reason.value = r }
    fun setOtherReason(r: String) { _otherReason.value = r }
    fun setRefund(v: String) { _refund.value = v }
    fun setRefundMethod(v: String) { _refundMethod.value = v }

    /**
     * Value of the returned goods (net + tax), proportional to each line. Rounded down so the suggested refund never
     * exceeds the credit the server calculates (it rounds half-up and gives the last return of a line the remainder).
     */
    fun credit(): BigDecimal {
        val s = _sale.value.data ?: return BigDecimal.ZERO
        return s.items.fold(BigDecimal.ZERO) { acc, it ->
            val q = Calc.dec(_lines.value[it.id]?.quantity ?: "") ?: return@fold acc
            if (q.signum() <= 0) return@fold acc
            val qty = BigDecimal.valueOf(it.quantity)
            val net = BigDecimal.valueOf(it.netAmount).multiply(q).divide(qty, 2, RoundingMode.DOWN)
            val tax = BigDecimal.valueOf(it.taxAmount).multiply(q).divide(qty, 2, RoundingMode.DOWN)
            acc.add(net).add(tax)
        }
    }

    fun suggestedRefund(): BigDecimal = credit().min(BigDecimal.valueOf(_sale.value.data?.amountPaid ?: 0.0))

    fun refundAmount(): BigDecimal = _refund.value?.let { Calc.dec(it) } ?: suggestedRefund()

    fun submit() {
        if (_state.value.submitting) return
        val s = _sale.value.data ?: return
        val errors = mutableMapOf<String, String>()
        val items = s.items.mapNotNull { item ->
            val line = _lines.value[item.id] ?: return@mapNotNull null
            if (line.quantity.isBlank()) return@mapNotNull null
            val q = Calc.dec(line.quantity)
            val max = item.quantity - item.returnedQuantity
            when {
                q == null || q.signum() <= 0 -> errors["${item.id}"] = "Enter a quantity"
                q.toDouble() > max + 1e-9 -> errors["${item.id}"] = "Only ${Fmt.qty(max)} can be returned"
            }
            q?.let { ReturnLineRequest(item.id, it.toDouble(), line.resalable) }
        }
        val reasonText = if (_reason.value == "Other") _otherReason.value.trim() else _reason.value
        if (items.isEmpty()) errors["items"] = "Enter the quantity returned for at least one item"
        if (reasonText.isBlank()) errors["reason"] = "Give a reason"
        val refund = refundAmount()
        if (refund.signum() < 0 || refund > suggestedRefund().max(BigDecimal.ZERO)) {
            errors["refundAmount"] = "Refund can be at most ${Fmt.money(suggestedRefund().toDouble(), _currency.value)}"
        }
        if (errors.isNotEmpty()) {
            _state.value = RecordState(fieldErrors = errors, error = errors["items"] ?: "Please correct the highlighted fields")
            return
        }
        _state.value = RecordState(submitting = true)
        viewModelScope.launch {
            val request = ReturnRequest(items, reasonText, refund.toDouble(),
                if (refund.signum() > 0) _refundMethod.value else null, null, requestId)
            _state.value = when (val r = apiCall { api.recordReturn(s.id, request) }) {
                is ApiResult.Ok -> RecordState(savedId = r.value.id)
                is ApiResult.Err -> RecordState(error = r.message, fieldErrors = r.fieldErrors)
            }
        }
    }
}

private val returnReasons = listOf("Wrong size or variant", "Damaged or defective", "Customer changed mind", "Wrong item delivered", "Other")

@Composable
fun ReturnScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val vm = appViewModel { c, s -> ReturnViewModel(c.api, s) }
    val sale by vm.sale.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val reason by vm.reason.collectAsStateWithLifecycle()
    val otherReason by vm.otherReason.collectAsStateWithLifecycle()
    val refund by vm.refund.collectAsStateWithLifecycle()
    val refundMethod by vm.refundMethod.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(state.savedId) { if (state.savedId != null) onDone() }
    val e = state.fieldErrors

    Scaffold(topBar = { AppTopBar("Customer return", onBack = onBack) }) { padding ->
        LoadableContent(sale, {}) { s ->
            FormColumn(padding) {
                ErrorBanner(state.error)
                Text("Sale ${s.invoiceNumber} · ${Fmt.dateTime(s.soldAt)}", style = MaterialTheme.typography.titleMedium)
                Text("Enter how many of each item came back. Items in good condition go back into stock; damaged items are recorded but not restocked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.items.forEach { item ->
                    val max = item.quantity - item.returnedQuantity
                    val line = lines[item.id] ?: ReturnLine()
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.productName, fontWeight = FontWeight.SemiBold)
                            Text("Sold ${Fmt.qty(item.quantity)} · already returned ${Fmt.qty(item.returnedQuantity)} · can return ${Fmt.qty(max)} ${item.unitCode}", style = MaterialTheme.typography.bodySmall)
                            if (max > 0) {
                                FormField(line.quantity, { v -> vm.setLine(item.id) { it.copy(quantity = v) } },
                                    "Quantity returned (${item.unitCode})", keyboardType = KeyboardType.Decimal,
                                    error = e["${item.id}"], placeholder = "0")
                                SwitchRow("Good condition – put back in stock", line.resalable,
                                    { v -> vm.setLine(item.id) { it.copy(resalable = v) } },
                                    if (line.resalable) "Will be added back to stock" else "Recorded as damaged, not restocked")
                            }
                        }
                    }
                }
                DropdownField("Reason", returnReasons, reason, vm::setReason, { it }, required = true, error = e["reason"])
                if (reason == "Other") FormField(otherReason, vm::setOtherReason, "Describe the reason", required = true)

                val credit = vm.credit()
                SectionCard("Refund") {
                    TotalRow("Value of returned items", Calc.money(credit, currency))
                    TotalRow("Customer has paid", Fmt.money(s.amountPaid, currency))
                    FormField(refund ?: Calc.amount(vm.suggestedRefund()).toPlainString(), vm::setRefund, "Refund now ($currency)",
                        keyboardType = KeyboardType.Decimal, error = e["refundAmount"],
                        supporting = "Anything not refunded is taken off what the customer owes")
                    if ((Calc.dec(refund ?: "") ?: vm.suggestedRefund()).signum() > 0) {
                        DropdownField("Refund method", paymentMethods.filter { it != "CREDIT" }, refundMethod, vm::setRefundMethod, ::paymentMethodLabel)
                    }
                }
                PrimaryButton("Review return", { confirm = true }, loading = state.submitting, enabled = credit.signum() > 0)
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
    if (confirm) {
        val s = sale.data
        val summary = buildString {
            s?.items?.forEach { item ->
                val l = lines[item.id] ?: return@forEach
                if (l.quantity.isBlank()) return@forEach
                append("${l.quantity} ${item.unitCode} × ${item.productName} — ${if (l.resalable) "back to stock" else "damaged"}\n")
            }
            append("\nRefund now: ${Calc.money(vm.refundAmount(), currency)}")
        }
        ConfirmDialog("Confirm return", summary, "Record return", onConfirm = { confirm = false; vm.submit() },
            onDismiss = { confirm = false })
    }
}
