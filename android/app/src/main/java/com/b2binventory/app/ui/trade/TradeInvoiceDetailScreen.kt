package com.b2binventory.app.ui.trade

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.common.Text
import com.b2binventory.app.ui.reports.ExportSaver
import com.b2binventory.app.ui.sales.ProductPickerDialog
import com.b2binventory.app.ui.sales.TotalRow
import com.b2binventory.app.ui.theme.appColors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/** Where the buyer receives one line: an existing product variant, or a new product. */
data class ReceiveChoice(val variantId: Long?, val createNew: Boolean, val label: String)

class TradeInvoiceDetailViewModel(val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    private val id: Long = saved.get<Long>("id")!!
    private val _state = MutableStateFlow(Loadable<TradeInvoice>())
    val state = _state.asStateFlow()
    private val _preview = MutableStateFlow(Loadable<ReceivePreview>(loading = false))
    val preview = _preview.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    /** Field errors from the last approval attempt, e.g. lines that still need a product chosen. */
    private val _approveErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val approveErrors = _approveErrors.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _approved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val approved = _approved.asSharedFlow()

    fun load() = _state.load(viewModelScope) { api.tradeInvoice(id) }

    fun loadPreview() {
        _approveErrors.value = emptyMap()
        _preview.load(viewModelScope) { api.receivePreview(id) }
    }

    private fun act(done: String, onOk: suspend () -> Unit = {}, block: suspend () -> TradeInvoice) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> { _state.value = Loadable(r.value, false); _messages.emit(done); onOk() }
                is ApiResult.Err -> _messages.emit(r.message)
            }
            _busy.value = false
        }
    }

    fun send() = act("Invoice sent. The stock is held for the buyer.") { api.sendTradeInvoice(id) }
    fun cancel(reason: String) = act("Invoice cancelled and stock released") { api.cancelTradeInvoice(id, ReasonBody(reason)) }
    fun reject(reason: String) = act("Invoice rejected") { api.rejectTradeInvoice(id, ReasonBody(reason)) }

    fun delete(onDone: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val r = apiCall { api.deleteTradeInvoice(id).requireSuccess() }) {
                is ApiResult.Ok -> onDone()
                is ApiResult.Err -> _messages.emit(r.message)
            }
            _busy.value = false
        }
    }

    fun approve(choices: Map<Long, ReceiveChoice>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val body = ApproveBody(lines = choices.map { (lineId, c) -> LineChoiceBody(lineId, c.variantId, c.createNew) })
            when (val r = apiCall { api.approveTradeInvoice(id, body) }) {
                is ApiResult.Ok -> {
                    _state.value = Loadable(r.value, false)
                    _approveErrors.value = emptyMap()
                    _messages.emit("Approved. The products have been added to your stock.")
                    _approved.emit(Unit)
                }
                is ApiResult.Err -> {
                    _approveErrors.value = r.fieldErrors.ifEmpty { mapOf("" to r.message) }
                    _messages.emit(r.message)
                }
            }
            _busy.value = false
        }
    }

    fun requestReturn(quantities: Map<Long, Double>, reason: String) = act("Return requested. The seller has been told.") {
        api.requestTradeReturn(id, TradeReturnBody(quantities.map { (lineId, q) -> TradeReturnLineBody(lineId, q) }, reason,
            clientRequestId = UUID.randomUUID().toString()))
    }

    fun acceptReturn(returnId: Long) = act("Return accepted. The stock is back in your inventory.") {
        api.acceptTradeReturn(returnId, RespondBody())
    }

    fun rejectReturn(returnId: Long, note: String?) = act("Return rejected") { api.rejectTradeReturn(returnId, RespondBody(note)) }
    fun cancelReturn(returnId: Long) = act("Return withdrawn") { api.cancelTradeReturn(returnId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeInvoiceDetailScreen(onBack: () -> Unit, onOpenSale: (Long) -> Unit, onOpenPurchase: (Long) -> Unit,
                             onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { c, s -> TradeInvoiceDetailViewModel(c.api, s) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var rejectingReturn by remember { mutableStateOf<TradeReturn?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    LaunchedEffect(Unit) { vm.approved.collect { dialog = null } }
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }

    Scaffold(
        topBar = {
            AppTopBar(state.data?.invoiceNumber ?: "Invoice", onBack = onBack,
                subtitle = state.data?.let { if (it.isSeller) tr("To ${it.buyer.name}") else tr("From ${it.seller.name}") }) {
                state.data?.let { inv ->
                    IconButton(onClick = {
                        runCatching { ExportSaver.open(context, TradeInvoicePdf.create(context, inv), "application/pdf") }
                            .onFailure { e -> android.widget.Toast.makeText(context, tr("Could not create the PDF: ${e.message}"),
                                android.widget.Toast.LENGTH_LONG).show() }
                    }) { Icon(Icons.Default.PictureAsPdf, tr("Share or print invoice")) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = vm::load, modifier = Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { inv ->
                val seller = inv.isSeller
                ResponsiveContent {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeaderCard(inv)
                    when (inv.status) {
                        "PENDING_APPROVAL" -> MessageBanner(BannerKind.WARNING, if (seller)
                            "Waiting for ${inv.buyer.name} to approve. The stock is held for them and can't be sold elsewhere."
                            else "Check the items and prices. Approving adds them to your stock and records the purchase.")
                        "POSTED" -> MessageBanner(BannerKind.SUCCESS, if (seller)
                            "Approved by ${inv.buyer.name}. The stock was deducted and the sale recorded."
                            else "Approved. The products were added to your stock and the purchase recorded.")
                        "REJECTED" -> MessageBanner(BannerKind.ERROR, "Rejected: ${inv.rejectionReason ?: ""}. No stock was moved.")
                        "CANCELLED" -> MessageBanner(BannerKind.ERROR, "Cancelled by the seller: ${inv.cancelReason ?: ""}. No stock was moved.")
                        "DRAFT" -> MessageBanner(BannerKind.INFO, "Draft. The buyer cannot see it and no stock is held until you send it.")
                    }
                    PartyCard(if (seller) "Buyer" else "Seller", inv.counterparty)

                    SectionCard("Items") {
                        inv.lines.forEachIndexed { i, l ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LineRow(l, inv.currencyCode, onOpenProduct.takeIf { !seller && l.buyerProductId != null })
                        }
                        HorizontalDivider()
                        TotalRow("Subtotal", Fmt.money(inv.subtotal, inv.currencyCode))
                        if (inv.discountTotal > 0) TotalRow("Discount", "− " + Fmt.money(inv.discountTotal, inv.currencyCode))
                        if (inv.taxTotal > 0) TotalRow("Tax", Fmt.money(inv.taxTotal, inv.currencyCode))
                        TotalRow("Total", Fmt.money(inv.total, inv.currencyCode), bold = true)
                    }
                    if (inv.notes != null || inv.deliveryNotes != null || inv.warehouseName != null) {
                        SectionCard("Details") {
                            LabelValue("Notes", inv.notes)
                            LabelValue("Delivery", inv.deliveryNotes)
                            LabelValue(if (seller) "Stock from" else "Received into", inv.warehouseName)
                        }
                    }

                    if (inv.returns.isNotEmpty()) {
                        SectionCard("Returns") {
                            inv.returns.forEach { r ->
                                ReturnRow(r, inv.currencyCode, seller, busy,
                                    onAccept = { vm.acceptReturn(r.id) },
                                    onReject = { rejectingReturn = r; reason = "" },
                                    onWithdraw = { vm.cancelReturn(r.id) })
                            }
                        }
                    }

                    // Actions for this business, depending on its side and the invoice status.
                    if (seller && me?.can("SALE_RECORD") == true) {
                        when (inv.status) {
                            "DRAFT" -> {
                                PrimaryButton("Send to ${inv.buyer.name}", vm::send, loading = busy, icon = Icons.Default.Send)
                                DangerButton("Delete draft", { dialog = "delete" }, enabled = !busy, icon = Icons.Default.Delete)
                            }
                            "PENDING_APPROVAL" -> DangerButton("Cancel invoice", { dialog = "cancel"; reason = "" }, enabled = !busy,
                                icon = Icons.Default.Cancel)
                            "POSTED" -> inv.saleId?.let { SecondaryButton("Open sale record", { onOpenSale(it) }, icon = Icons.Default.PointOfSale) }
                        }
                    }
                    if (!seller && me?.can("PURCHASE_MANAGE") == true) {
                        when (inv.status) {
                            "PENDING_APPROVAL" -> {
                                PrimaryButton("Review and approve", { vm.loadPreview(); dialog = "approve" }, loading = busy,
                                    icon = Icons.Default.TaskAlt)
                                DangerButton("Reject", { dialog = "reject"; reason = "" }, enabled = !busy, icon = Icons.Default.Block)
                            }
                            "POSTED" -> {
                                inv.purchaseId?.let { SecondaryButton("Open purchase record", { onOpenPurchase(it) }, icon = Icons.Default.ShoppingCart) }
                                if (inv.lines.any { it.quantity - it.returnedQuantity > 0 }) {
                                    SecondaryButton("Return goods", { dialog = "return" }, enabled = !busy,
                                        icon = Icons.AutoMirrored.Filled.AssignmentReturn)
                                }
                            }
                        }
                    }

                    if (inv.events.isNotEmpty()) {
                        SectionCard("History") { inv.events.forEach { EventRow(it) } }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                }
            }
        }
    }

    val inv = state.data
    when (dialog) {
        "approve" -> if (inv != null) ApproveDialog(vm, inv, busy, onDismiss = { dialog = null })
        "return" -> if (inv != null) ReturnDialog(inv, busy, onDismiss = { dialog = null }) { q, r -> dialog = null; vm.requestReturn(q, r) }
        "delete" -> ConfirmDialog("Delete this draft?", "It has not been sent, so nothing else changes.", "Delete", destructive = true,
            onConfirm = { dialog = null; vm.delete(onBack) }, onDismiss = { dialog = null })
        "cancel", "reject" -> {
            val cancelling = dialog == "cancel"
            ConfirmDialog(if (cancelling) "Cancel this invoice?" else "Reject this invoice?",
                if (cancelling) "Cancel invoice" else "Reject", destructive = true,
                onConfirm = {
                    if (reason.isNotBlank()) {
                        dialog = null
                        if (cancelling) vm.cancel(reason.trim()) else vm.reject(reason.trim())
                    }
                }, onDismiss = { dialog = null }, icon = Icons.Default.Block) {
                Text(if (cancelling) "The buyer is told and the held stock becomes available again."
                    else "The seller is told and nothing is added to your stock.")
                FormField(reason, { reason = it }, "Reason", required = true,
                    placeholder = if (cancelling) "e.g. Wrong quantity, sending a new one" else "e.g. Rate not as agreed")
            }
        }
    }
    rejectingReturn?.let { r ->
        ConfirmDialog("Reject return ${r.returnNumber}?", "Reject", destructive = true,
            onConfirm = { rejectingReturn = null; vm.rejectReturn(r.id, reason.blankToNull()) },
            onDismiss = { rejectingReturn = null }) {
            Text("The goods stay with the buyer and their held stock is released.")
            FormField(reason, { reason = it }, "Note for the buyer", placeholder = "e.g. Not damaged on delivery")
        }
    }
}

@Composable
private fun HeaderCard(inv: TradeInvoice) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(inv.invoiceNumber, style = MaterialTheme.typography.titleLarge)
                    Text("Invoice date ${Fmt.date(inv.invoiceDate)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TradeStatusPill(inv.status, inv.isSeller)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (inv.isSeller) "Sale value" else "Amount payable", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Text(Fmt.money(inv.total, inv.currencyCode), style = com.b2binventory.app.ui.theme.MetricTextStyle)
            }
        }
    }
}

@Composable
private fun PartyCard(title: String, p: PartyDetails) {
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(p.name, style = MaterialTheme.typography.titleSmall)
                androidx.compose.material3.Text(listOfNotNull(p.businessType, p.businessCode).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LabelValue("Phone", p.phone)
        LabelValue("Email", p.email)
        LabelValue("Address", listOfNotNull(p.address, p.city, p.state, p.postalCode).joinToString(", ").ifEmpty { null })
        LabelValue("Tax id", p.taxId)
    }
}

@Composable
private fun LineRow(l: TradeLine, currency: String, onOpenProduct: ((Long) -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(l.label, style = MaterialTheme.typography.titleSmall)
                androidx.compose.material3.Text(listOfNotNull(l.sku?.let { tr("SKU $it") }, l.brand, l.categoryName).joinToString(" · ")
                    .ifEmpty { l.unitName }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Text(Fmt.money(l.lineTotal, currency), style = MaterialTheme.typography.titleSmall)
        }
        specsText(l.attributes, l.variantAttributes)?.let {
            androidx.compose.material3.Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text("${Fmt.qty(l.quantity)} ${l.unitCode} × ${Fmt.money(l.unitPrice, currency)}" +
            (if (l.discount > 0) " · − ${Fmt.money(l.discount, currency)}" else "") +
            (if (l.taxRate > 0) " · ${tr("tax ${Fmt.qty(l.taxRate)}%")}" else ""),
            style = MaterialTheme.typography.bodyMedium)
        if (l.returnedQuantity > 0) {
            Pill(tr("Returned: ${Fmt.qty(l.returnedQuantity)} ${l.unitCode}"), MaterialTheme.appColors.warning,
                Icons.AutoMirrored.Filled.AssignmentReturn)
        }
        if (onOpenProduct != null && l.buyerProductId != null) {
            TextButton(onClick = { onOpenProduct(l.buyerProductId) }, contentPadding = PaddingValues(0.dp)) {
                Text("View in my stock")
            }
        }
    }
}

@Composable
private fun ReturnRow(r: TradeReturn, currency: String, seller: Boolean, busy: Boolean, onAccept: () -> Unit, onReject: () -> Unit,
                      onWithdraw: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(r.returnNumber, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Pill(returnStatusLabel(r.status), tradeStatusColor(r.status), tradeStatusIcon(r.status))
        }
        Text("Reason: ${r.reason}", style = MaterialTheme.typography.bodySmall)
        r.lines.forEach { l ->
            InfoRow(l.productName + if (l.variantName.isNotEmpty() && l.variantName != l.productName) " · ${l.variantName}" else "",
                "${Fmt.qty(l.quantity)} ${l.unitCode} · ${Fmt.money(l.amount, currency)}")
        }
        r.responseNote?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall) }
        if (r.status == "REQUESTED") {
            if (seller && LocalMe.current?.can("SALE_CANCEL") == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Reject") }
                    Button(onClick = onAccept, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Accept return") }
                }
            } else if (!seller) {
                TextButton(onClick = onWithdraw, enabled = !busy) { Text("Withdraw return") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EventRow(e: TradeEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Circle, null, Modifier.size(10.dp).padding(top = 4.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tradeEventLabel(e.action), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.Text(listOfNotNull(e.businessName, e.actorName, Fmt.dateTime(e.at)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            e.note?.let { androidx.compose.material3.Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Buyer's review: for each line, where it will be received. Ambiguous lines must be chosen. */
@Composable
private fun ApproveDialog(vm: TradeInvoiceDetailViewModel, inv: TradeInvoice, busy: Boolean, onDismiss: () -> Unit) {
    val preview by vm.preview.collectAsStateWithLifecycle()
    val errors by vm.approveErrors.collectAsStateWithLifecycle()
    val choices = remember { mutableStateMapOf<Long, ReceiveChoice>() }
    var picking by remember { mutableStateOf<Long?>(null) }
    var pickedProduct by remember { mutableStateOf<Pair<Long, ProductDetail>?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = { AppTopBar("Review and approve", onBack = onDismiss, subtitle = inv.invoiceNumber) }) { padding ->
                LoadableContent(preview, vm::loadPreview) { p ->
                    FormColumn(padding) {
                        Text("Choose where each item goes in your stock. The quantity, size, unit and price are kept exactly as invoiced.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.warehouseName?.let { InfoRow("Received into", it) }
                        errors[""]?.let { MessageBanner(BannerKind.ERROR, it) }
                        inv.lines.forEachIndexed { i, line ->
                            val proposal = p.lines.firstOrNull { it.lineId == line.id }
                            val choice = choices[line.id]
                            val needsChoice = proposal?.mode == "REVIEW" && choice == null
                            AppCard(containerColor = if (needsChoice) MaterialTheme.appColors.warningContainer else MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    androidx.compose.material3.Text(line.label, style = MaterialTheme.typography.titleSmall)
                                    Text("${Fmt.qty(line.quantity)} ${line.unitCode} × ${Fmt.money(line.unitPrice, inv.currencyCode)}",
                                        style = MaterialTheme.typography.bodySmall)
                                    specsText(line.attributes, line.variantAttributes)?.let {
                                        androidx.compose.material3.Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                    val target = proposal?.target
                                    when {
                                        choice != null -> Destination(if (choice.createNew) Icons.Default.AddBox else Icons.Default.Inventory2,
                                            if (choice.createNew) "Create a new product" else "Add to: ${choice.label}")
                                        proposal?.mode == "NEW" -> Destination(Icons.Default.AddBox,
                                            "Not in your products yet. It will be created with the same details.")
                                        target != null -> Destination(Icons.Default.Inventory2,
                                            "Add to: ${target.label} (${Fmt.qty(target.available)} ${target.unitCode} in stock)")
                                        proposal?.mode == "REVIEW" -> Text("Several of your products could match. Choose one:",
                                            color = MaterialTheme.appColors.onWarningContainer, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (proposal?.mode == "REVIEW") {
                                        proposal.candidates.forEach { c ->
                                            Row(Modifier.fillMaxWidth().selectable(selected = choice?.variantId == c.variantId,
                                                onClick = { choices[line.id] = ReceiveChoice(c.variantId, false, c.label) }),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = choice?.variantId == c.variantId, onClick = null)
                                                androidx.compose.material3.Text("${c.label} · ${Fmt.qty(c.available)} ${c.unitCode}",
                                                    style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    errors["lines[$i]"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { picking = line.id }) { Text("Choose my product") }
                                        if (choice?.createNew != true && proposal?.mode != "NEW") {
                                            TextButton(onClick = { choices[line.id] = ReceiveChoice(null, true, "") }) { Text("Create new") }
                                        }
                                        if (choice != null) TextButton(onClick = { choices.remove(line.id) }) { Text("Undo") }
                                    }
                                }
                            }
                        }
                        val unresolved = p.lines.count { it.mode == "REVIEW" && choices[it.lineId] == null }
                        if (unresolved > 0) MessageBanner(BannerKind.WARNING, "Choose a product for the highlighted items first.")
                        PrimaryButton("Approve and add to stock", { vm.approve(choices.toMap()) }, loading = busy,
                            enabled = unresolved == 0, icon = Icons.Default.TaskAlt)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    picking?.let { lineId ->
        ProductPickerDialog(vm.api, "Receive into", onPick = { summary ->
            picking = null
            scope.launch {
                (apiCall { vm.api.product(summary.id) } as? ApiResult.Ok)?.value?.let { detail ->
                    val active = detail.variants.filter { !it.archived }
                    if (active.size == 1) choices[lineId] = ReceiveChoice(active[0].id, false, detail.name)
                    else pickedProduct = lineId to detail
                }
            }
        }, onDismiss = { picking = null })
    }
    pickedProduct?.let { (lineId, detail) ->
        AlertDialog(onDismissRequest = { pickedProduct = null }, title = { Text("Which size or variant?") },
            text = {
                Column {
                    detail.variants.filter { !it.archived }.forEach { v ->
                        TextButton(onClick = {
                            choices[lineId] = ReceiveChoice(v.id, false, if (v.isDefault) detail.name else "${detail.name} · ${v.name}")
                            pickedProduct = null
                        }) { androidx.compose.material3.Text(if (v.isDefault) detail.name else v.name) }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { pickedProduct = null }) { Text("Cancel") } })
    }
}

@Composable
private fun Destination(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReturnDialog(inv: TradeInvoice, busy: Boolean, onDismiss: () -> Unit, onConfirm: (Map<Long, Double>, String) -> Unit) {
    val quantities = remember { mutableStateMapOf<Long, String>() }
    var reason by remember { mutableStateOf("") }
    val returnable = inv.lines.filter { it.quantity - it.returnedQuantity > 0 }
    val chosen = quantities.mapNotNull { (id, t) -> t.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it } }.toMap()
    val tooMany = returnable.any { l -> (chosen[l.id] ?: 0.0) > l.quantity - l.returnedQuantity }
    ConfirmDialog("Return goods to ${inv.seller.name}", "Request return", destructive = false,
        onConfirm = { if (chosen.isNotEmpty() && reason.isNotBlank() && !tooMany && !busy) onConfirm(chosen, reason.trim()) },
        onDismiss = onDismiss, icon = Icons.AutoMirrored.Filled.AssignmentReturn) {
        Text("The quantities are held in your stock until the seller accepts. Then they leave your stock and go back to theirs.",
            style = MaterialTheme.typography.bodySmall)
        returnable.forEach { l ->
            val max = l.quantity - l.returnedQuantity
            FormField(quantities[l.id] ?: "", { quantities[l.id] = it }, "${l.label} (max ${Fmt.qty(max)} ${l.unitCode})",
                keyboardType = KeyboardType.Decimal,
                error = if ((chosen[l.id] ?: 0.0) > max) tr("Only ${Fmt.qty(max)} ${l.unitCode} can be returned") else null)
        }
        FormField(reason, { reason = it }, "Reason", required = true, placeholder = "e.g. Damaged in transit")
    }
}
