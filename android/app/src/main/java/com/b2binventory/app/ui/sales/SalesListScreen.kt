package com.b2binventory.app.ui.sales

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SalesSection(val label: String) { SALES("Sales"), PURCHASES("Purchases"), CUSTOMERS("Customers"), SUPPLIERS("Suppliers") }

data class SalesFilter(val q: String = "", val paymentStatus: String? = null, val status: String? = null, val days: Int? = null)

class SalesHubViewModel(private val api: ApiService) : ViewModel() {
    private val _filter = MutableStateFlow(SalesFilter())
    val filter = _filter.asStateFlow()
    private val _sales = MutableStateFlow(Loadable<List<SaleSummary>>())
    val sales = _sales.asStateFlow()
    private val _purchases = MutableStateFlow(Loadable<List<PurchaseSummary>>(loading = false))
    val purchases = _purchases.asStateFlow()
    private val _customers = MutableStateFlow(Loadable<List<Party>>(loading = false))
    val customers = _customers.asStateFlow()
    private val _suppliers = MutableStateFlow(Loadable<List<Party>>(loading = false))
    val suppliers = _suppliers.asStateFlow()
    private val _currency = MutableStateFlow("INR")
    val currency = _currency.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { (apiCall { api.settings() } as? ApiResult.Ok)?.let { _currency.value = it.value.currencyCode } }
    }

    fun setFilter(t: (SalesFilter) -> SalesFilter, debounce: Boolean = false) {
        _filter.update(t)
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(350)
            loadSales()
        }
    }

    fun loadSales() {
        val f = _filter.value
        val from = f.days?.let { LocalDate.now().minusDays((it - 1).toLong()).toString() }
        _sales.load(viewModelScope) {
            api.sales(q = f.q.blankToNull(), paymentStatus = f.paymentStatus, status = f.status, from = from, size = 100).content
        }
    }

    fun loadPurchases(q: String) = _purchases.load(viewModelScope) { api.purchases(q = q.blankToNull(), size = 100).content }
    fun loadCustomers(q: String) = _customers.load(viewModelScope) { api.customers(q.blankToNull()) }
    fun loadSuppliers(q: String) = _suppliers.load(viewModelScope) { api.suppliers(q.blankToNull()) }

    fun saveParty(supplier: Boolean, id: Long?, request: PartyRequest, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = apiCall {
                when {
                    supplier && id == null -> api.createSupplier(request)
                    supplier -> api.updateSupplier(id!!, request)
                    id == null -> api.createCustomer(request)
                    else -> api.updateCustomer(id, request)
                }
            }
            when (r) {
                is ApiResult.Ok -> {
                    onDone()
                    _messages.emit("Saved ${r.value.name}")
                    if (supplier) loadSuppliers("") else loadCustomers("")
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHubScreen(
    onRecordSale: () -> Unit,
    onRecordPurchase: () -> Unit,
    onOpenSale: (Long) -> Unit,
    onOpenPurchase: (Long) -> Unit,
) {
    val vm = appViewModel { c, _ -> SalesHubViewModel(c.api) }
    val me = LocalMe.current
    val sections = buildList {
        if (me?.can("SALE_RECORD") == true || me?.can("SALE_CANCEL") == true || me?.can("ANALYTICS_VIEW") == true) add(SalesSection.SALES)
        if (me?.can("PURCHASE_MANAGE") == true) add(SalesSection.PURCHASES)
        if (me?.can("SALE_RECORD") == true) add(SalesSection.CUSTOMERS)
        if (me?.can("PURCHASE_MANAGE") == true) add(SalesSection.SUPPLIERS)
    }
    var section by rememberSaveable { mutableStateOf(sections.firstOrNull() ?: SalesSection.SALES) }
    var returnHint by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    if (sections.isEmpty()) {
        Scaffold(topBar = { AppTopBar("Sales") }) { p -> ErrorState("You do not have permission to view sales.", modifier = Modifier.padding(p)) }
        return
    }

    Scaffold(topBar = { AppTopBar("Sales") }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            val actions = buildList<Triple<String, ImageVector, () -> Unit>> {
                if (me?.can("SALE_RECORD") == true) add(Triple("Record Sale", Icons.Default.PointOfSale, onRecordSale))
                if (me?.can("PURCHASE_MANAGE") == true) add(Triple("Record Purchase", Icons.Default.LocalShipping, onRecordPurchase))
                if (me?.can("SALE_CANCEL") == true) add(Triple("Record Return", Icons.AutoMirrored.Filled.AssignmentReturn) {
                    section = SalesSection.SALES
                    returnHint = true
                })
            }
            if (actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { (label, icon, action) -> ActionTile(label, icon, action, Modifier.weight(1f)) }
                }
            }
            if (sections.size > 1) {
                ScrollableTabRow(selectedTabIndex = sections.indexOf(section).coerceAtLeast(0), edgePadding = 16.dp) {
                    sections.forEach { s -> Tab(section == s, { section = s }, text = { Text(s.label) }) }
                }
            }
            if (returnHint && section == SalesSection.SALES) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Find the original sale below, open it and tap \"Record customer return\".", Modifier.weight(1f))
                        TextButton(onClick = { returnHint = false }) { Text("OK") }
                    }
                }
            }
            when (section) {
                SalesSection.SALES -> SalesList(vm, onOpenSale)
                SalesSection.PURCHASES -> PurchasesList(vm, onOpenPurchase)
                SalesSection.CUSTOMERS -> PartyList(vm, supplier = false)
                SalesSection.SUPPLIERS -> PartyList(vm, supplier = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesList(vm: SalesHubViewModel, onOpen: (Long) -> Unit) {
    val state by vm.sales.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.loadSales(); onPauseOrDispose { } }
    Column {
        OutlinedTextField(filter.q, { v -> vm.setFilter({ it.copy(q = v) }, debounce = true) }, Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Invoice no., sale id or customer") })
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf<Int?>(null, 1, 7, 30)) { d ->
                FilterChip(filter.days == d, { vm.setFilter({ it.copy(days = d) }) },
                    label = { Text(when (d) { null -> "All dates"; 1 -> "Today"; else -> "Last $d days" }) })
            }
            items(listOf("UNPAID", "PARTIALLY_PAID", "PAID")) { s ->
                FilterChip(filter.paymentStatus == s, { vm.setFilter({ it.copy(paymentStatus = if (it.paymentStatus == s) null else s) }) },
                    label = { Text(paymentStatusLabel(s)) })
            }
            item {
                FilterChip(filter.status == "CANCELLED", { vm.setFilter({ it.copy(status = if (it.status == "CANCELLED") null else "CANCELLED") }) },
                    label = { Text("Cancelled") })
            }
        }
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = vm::loadSales) {
            LoadableContent(state, vm::loadSales) { sales ->
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
                    if (sales.isEmpty()) {
                        item {
                            EmptyState(if (filter == SalesFilter()) "No sales yet" else "No sales match",
                                if (filter == SalesFilter()) "Tap Record Sale to add your first sale. Stock is reduced automatically."
                                else "Try a different search or filter.", icon = Icons.Default.PointOfSale)
                        }
                    }
                    items(sales, key = { it.id }) { s -> SaleRow(s, currency) { onOpen(s.id) } }
                }
            }
        }
    }
}

@Composable
private fun SaleRow(s: SaleSummary, currency: String, onClick: () -> Unit) {
    val cancelled = s.status == "CANCELLED"
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${s.invoiceNumber} · ${s.customerName ?: "Walk-in"}", fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textDecoration = if (cancelled) TextDecoration.LineThrough else null)
            Text(s.products, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Fmt.dateTime(s.soldAt)} · ${Fmt.qty(s.totalQuantity)} items", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Fmt.money(s.total, currency), fontWeight = FontWeight.SemiBold,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null)
            if (cancelled) DocumentStatusChip(s.status) else PaymentStatusChip(s.paymentStatus)
            if (!cancelled && s.outstanding > 0) Text("Due ${Fmt.money(s.outstanding, currency)}", style = MaterialTheme.typography.labelSmall)
        }
    }
    HorizontalDivider()
}

@Composable
private fun PurchasesList(vm: SalesHubViewModel, onOpen: (Long) -> Unit) {
    val state by vm.purchases.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(q) { delay(300); vm.loadPurchases(q) }
    Column {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Purchase no., bill no. or supplier") })
        LoadableContent(state, { vm.loadPurchases(q) }) { list ->
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
                if (list.isEmpty()) item { EmptyState("No purchases yet", "Record goods received from suppliers to add stock and track costs.",
                    icon = Icons.Default.LocalShipping) }
                items(list, key = { it.id }) { p ->
                    val cancelled = p.status == "CANCELLED"
                    Row(Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.purchaseNumber} · ${p.supplierName ?: "No supplier"}", fontWeight = FontWeight.Medium,
                                textDecoration = if (cancelled) TextDecoration.LineThrough else null)
                            Text(p.products, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.dateTime(p.purchasedAt) + (p.referenceNumber?.let { " · bill $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.money(p.total, currency), fontWeight = FontWeight.SemiBold)
                            if (cancelled) DocumentStatusChip(p.status) else PaymentStatusChip(p.paymentStatus)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PartyList(vm: SalesHubViewModel, supplier: Boolean) {
    val state by (if (supplier) vm.suppliers else vm.customers).collectAsStateWithLifecycle()
    var q by remember(supplier) { mutableStateOf("") }
    var editing by remember { mutableStateOf<Party?>(null) }
    var adding by remember { mutableStateOf(false) }
    val label = if (supplier) "supplier" else "customer"
    LaunchedEffect(q, supplier) { delay(300); if (supplier) vm.loadSuppliers(q) else vm.loadCustomers(q) }
    Column {
        Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Name, phone or email") })
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Text("Add") }
        }
        LoadableContent(state, { if (supplier) vm.loadSuppliers(q) else vm.loadCustomers(q) }) { list ->
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
                if (list.isEmpty()) item { EmptyState("No ${label}s yet", "Add a $label, or save one while recording a ${if (supplier) "purchase" else "sale"}.",
                    icon = if (supplier) Icons.Default.Store else Icons.Default.Person) }
                items(list, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(listOfNotNull(p.phone, p.email, p.taxId?.let { "Tax id $it" }).joinToString(" · ").ifEmpty { "No contact details" }) },
                        modifier = Modifier.clickable { editing = p },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (adding || editing != null) {
        PartyDialog(editing, supplier, onSave = { req -> vm.saveParty(supplier, editing?.id, req) { adding = false; editing = null } },
            onDismiss = { adding = false; editing = null })
    }
}

@Composable
private fun PartyDialog(party: Party?, supplier: Boolean, onSave: (PartyRequest) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(party?.name ?: "") }
    var phone by remember { mutableStateOf(party?.phone ?: "") }
    var email by remember { mutableStateOf(party?.email ?: "") }
    var address by remember { mutableStateOf(party?.address ?: "") }
    var taxId by remember { mutableStateOf(party?.taxId ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (party == null) "Add " else "Edit ") + if (supplier) "supplier" else "customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it }, "Name", required = true, error = if (name.isBlank()) "Name is required" else null)
                FormField(phone, { phone = it }, "Phone", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                FormField(email, { email = it }, "Email", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                FormField(address, { address = it }, "Address", singleLine = false)
                if (supplier) FormField(taxId, { taxId = it }, "Tax / GST number")
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(PartyRequest(name.trim(), phone.blankToNull(), email.blankToNull(), address.blankToNull(),
                    if (supplier) taxId.blankToNull() else null, party?.notes))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
