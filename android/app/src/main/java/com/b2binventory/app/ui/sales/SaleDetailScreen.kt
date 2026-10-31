package com.b2binventory.app.ui.sales

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.data.sync.OfflineQueue
import com.b2binventory.app.ui.sync.localizeMessage
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.reports.ExportSaver
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SaleDetailViewModel(private val api: ApiService, saved: SavedStateHandle, private val offline: OfflineQueue) : ViewModel() {
    val id: Long = saved.get<Long>("id")!!
    val justRecorded: Boolean = saved.get<Boolean>("recorded") == true
    private val _state = MutableStateFlow(Loadable<Sale>())
    val state = _state.asStateFlow()
    private val _currency = MutableStateFlow("INR")
    val currency = _currency.asStateFlow()
    /** Address, phone and tax id for the invoice header; null when this member can't read the business profile. */
    private val _business = MutableStateFlow<Business?>(null)
    val business = _business.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch { (apiCall { api.settings() } as? ApiResult.Ok)?.let { _currency.value = it.value.currencyCode } }
        viewModelScope.launch { (apiCall { api.business() } as? ApiResult.Ok)?.let { _business.value = it.value } }
    }

    fun load() = _state.load(viewModelScope) { api.sale(id) }

    private fun act(success: String, block: suspend () -> Sale) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> {
                    _state.value = Loadable(r.value, false)
                    _messages.emit(success)
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
            _busy.value = false
        }
    }

    fun addPayment(amount: Double, method: String, reference: String?) {
        val requestId = UUID.randomUUID().toString()
        val request = PaymentRequest(amount, method, reference, requestId)
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val r = apiCall { api.addSalePayment(id, request) }) {
                is ApiResult.Ok -> {
                    _state.value = Loadable(r.value, false)
                    _messages.emit("Payment recorded")
                }
                is ApiResult.Err -> {
                    val invoice = _state.value.data?.invoiceNumber ?: "#$id"
                    if (r.offline && offline.payment(id, request, "$invoice · ${Fmt.qty(amount)} $method")) {
                        _messages.emit(OfflineQueue.OFFLINE_MESSAGE)
                    } else _messages.emit(r.message)
                }
            }
            _busy.value = false
        }
    }

    fun cancel(reason: String, refundMethod: String) =
        act("Sale cancelled. Stock has been put back.") { api.cancelSale(id, CancelSaleRequest(reason, refundMethod)) }
}

private val cancelReasons = listOf("Entered by mistake", "Customer cancelled the order", "Wrong price or quantity", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(onBack: () -> Unit, onReturn: (Long) -> Unit, onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { c, s -> SaleDetailViewModel(c.api, s, OfflineQueue(c)) }
    val state by vm.state.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val business by vm.business.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var paying by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(localizeMessage(context, it)) } }
    LaunchedEffect(Unit) { if (vm.justRecorded) snackbar.showSnackbar(tr("Sale recorded and stock updated")) }
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            AppTopBar(state.data?.invoiceNumber ?: "Sale", onBack = onBack) {
                state.data?.let { sale ->
                    IconButton(onClick = {
                        runCatching {
                            val uri = ReceiptPdf.create(context, sale, business, me?.businessName ?: "", currency)
                            ExportSaver.open(context, uri, "application/pdf")
                        }.onFailure { e ->
                            android.widget.Toast.makeText(context, tr("Could not create the receipt: ${e.message}"),
                                android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Icon(Icons.Default.PictureAsPdf, "Share or print receipt") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = vm::load, modifier = Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { s ->
                val cancelled = s.status == "CANCELLED"
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.justRecorded && !cancelled) {
                        Surface(color = StatusColors.ok.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = StatusColors.ok)
                                Spacer(Modifier.width(8.dp))
                                Text("Sale saved. Stock was reduced automatically.", color = StatusColors.ok)
                            }
                        }
                    }
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.invoiceNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(Fmt.dateTime(s.soldAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (cancelled) DocumentStatusChip(s.status) else PaymentStatusChip(s.paymentStatus)
                            }
                        }
                        LabelValue("Customer", listOfNotNull(s.customerName, s.customerPhone).joinToString(" · ").ifEmpty { "Walk-in customer" })
                        LabelValue("Recorded by", s.soldBy)
                        LabelValue("Notes", s.notes)
                        if (cancelled) {
                            ErrorBanner("Cancelled by ${s.cancelledBy ?: ""} on ${Fmt.dateTime(s.cancelledAt)}: ${s.cancelReason ?: ""}")
                        }
                    }

                    SectionCard("Items") {
                        s.items.forEach { it ->
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(it.productName, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${Fmt.qty(it.quantity)} ${it.unitCode} × ${Fmt.money(it.unitPrice, currency)}" +
                                        (if (it.discount > 0) " − ${Fmt.money(it.discount, currency)}" else "") +
                                        (if (it.taxAmount > 0) " + tax ${Fmt.qty(it.taxRate)}%" else ""),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (it.returnedQuantity > 0) {
                                        Text("Returned ${Fmt.qty(it.returnedQuantity)} ${it.unitCode}", style = MaterialTheme.typography.bodySmall,
                                            color = StatusColors.low)
                                    }
                                    it.grossProfit?.let { gp ->
                                        Text("Profit ${Fmt.money(gp, currency)} (cost ${Fmt.money(it.unitCost ?: 0.0, currency)}/${it.unitCode})",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(Fmt.money(it.lineTotal, currency), fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider()
                        }
                        TotalRow("Subtotal", Fmt.money(s.subtotal, currency))
                        if (s.discountTotal > 0) TotalRow("Discount", "− " + Fmt.money(s.discountTotal, currency))
                        if (s.taxTotal > 0) TotalRow("Tax", Fmt.money(s.taxTotal, currency))
                        TotalRow("Total", Fmt.money(s.total, currency), bold = true)
                        if (s.returnedTotal > 0) TotalRow("Returned", "− " + Fmt.money(s.returnedTotal, currency))
                        TotalRow("Paid", Fmt.money(s.amountPaid, currency))
                        if (!cancelled) TotalRow("Balance due", Fmt.money(s.outstanding, currency), bold = true,
                            color = if (s.outstanding > 0) StatusColors.low else StatusColors.ok)
                    }

                    if (!cancelled && (s.grossProfit != null || s.costTotal != null)) {
                        SectionCard("Profit") {
                            TotalRow("Net sales (excl. tax)", Fmt.money(s.netSales, currency))
                            s.grossProfit?.let { TotalRow("Gross profit", Fmt.money(it, currency), bold = true,
                                color = if (it >= 0) StatusColors.ok else StatusColors.out) }
                                ?: Text("Profit unavailable: some items had no cost.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (s.stock.isNotEmpty() && !cancelled) {
                        SectionCard("Stock now") {
                            s.stock.forEach { st ->
                                Row(Modifier.fillMaxWidth().clickable { onOpenProduct(st.productId) }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(st.productName, Modifier.weight(1f))
                                    Text("${Fmt.qty(st.available)} ${st.unitCode}  ")
                                    StatusChip(st.status)
                                }
                            }
                        }
                    }

                    if (s.payments.isNotEmpty()) {
                        SectionCard("Payments") {
                            s.payments.forEach { p ->
                                TotalRow("${if (p.kind == "REFUND") "Refund" else "Payment"} · ${paymentMethodLabel(p.method)} · ${Fmt.dateTime(p.paidAt)}",
                                    (if (p.kind == "REFUND") "− " else "") + Fmt.money(p.amount, currency))
                            }
                        }
                    }
                    if (s.returns.isNotEmpty()) {
                        SectionCard("Returns") {
                            s.returns.forEach { r ->
                                Text("${r.returnNumber} · ${Fmt.dateTime(r.returnedAt)}", fontWeight = FontWeight.Medium)
                                Text(r.items.joinToString { "${Fmt.qty(it.quantity)} × ${it.productName}" + if (it.resalable) "" else " (damaged)" } +
                                    " · ${r.reason}", style = MaterialTheme.typography.bodySmall)
                                Text("Credit ${Fmt.money(r.creditAmount, currency)} · refunded ${Fmt.money(r.refundAmount, currency)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (!cancelled) {
                        if (me?.can("SALE_RECORD") == true && s.outstanding > 0) {
                            Button(onClick = { paying = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                Icon(Icons.Default.Payments, null); Spacer(Modifier.width(8.dp)); Text("Record payment")
                            }
                        }
                        if (me?.can("SALE_CANCEL") == true) {
                            if (s.items.any { it.quantity > it.returnedQuantity }) {
                                OutlinedButton(onClick = { onReturn(s.id) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                    Icon(Icons.AutoMirrored.Filled.AssignmentReturn, null); Spacer(Modifier.width(8.dp)); Text("Record customer return")
                                }
                            }
                            OutlinedButton(onClick = { cancelling = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Icon(Icons.Default.Cancel, null); Spacer(Modifier.width(8.dp)); Text("Cancel sale")
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    val sale = state.data
    if (paying && sale != null) {
        AddPaymentDialog(sale.outstanding, currency, busy, onConfirm = { a, m, r -> paying = false; vm.addPayment(a, m, r) },
            onDismiss = { paying = false })
    }
    if (cancelling && sale != null) {
        var reason by remember { mutableStateOf(cancelReasons.first()) }
        var other by remember { mutableStateOf("") }
        var refundMethod by remember { mutableStateOf("CASH") }
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = { Text("Cancel this sale?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All items not yet returned go back into stock. The sale stays in history marked as cancelled and no longer counts in sales or profit.")
                    if (sale.amountPaid > 0) Text("${Fmt.money(sale.amountPaid, currency)} received will be recorded as refunded.",
                        fontWeight = FontWeight.Medium)
                    DropdownField("Reason", cancelReasons, reason, { reason = it }, { it }, required = true)
                    if (reason == "Other") FormField(other, { other = it }, "Describe the reason", required = true)
                    if (sale.amountPaid > 0) {
                        DropdownField("Refund method", paymentMethods.filter { it != "CREDIT" }, refundMethod, { refundMethod = it }, ::paymentMethodLabel)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && (reason != "Other" || other.isNotBlank()), onClick = {
                    cancelling = false
                    vm.cancel(if (reason == "Other") other.trim() else reason, refundMethod)
                }) { Text("Cancel sale", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cancelling = false }) { Text("Keep sale") } },
        )
    }
}
