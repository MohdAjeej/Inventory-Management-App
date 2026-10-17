package com.b2binventory.app.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.common.Text
import com.b2binventory.app.ui.theme.appColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TradeInvoicesViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    /** SALES: invoices this business sent; PURCHASES: invoices it received. */
    val direction: String = saved.get<String>("direction") ?: "SALES"
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _state = MutableStateFlow(Loadable<Page<TradeInvoiceSummary>>())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun load(debounce: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(350)
            _state.load(this) { api.tradeInvoices(direction, _status.value, _query.value.blankToNull()) }
        }
    }

    fun setStatus(s: String?) {
        _status.value = s
        load()
    }

    fun setQuery(q: String) {
        _query.value = q
        load(debounce = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeInvoicesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val vm = appViewModel { c, s -> TradeInvoicesViewModel(c.api, s) }
    val state by vm.state.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selling = vm.direction == "SALES"
    val canSell = LocalMe.current?.can("SALE_RECORD") == true
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }

    val statuses = if (selling) listOf(null, "PENDING_APPROVAL", "POSTED", "DRAFT", "REJECTED", "CANCELLED")
        else listOf(null, "PENDING_APPROVAL", "POSTED", "REJECTED", "CANCELLED")

    Scaffold(
        topBar = {
            AppTopBar(if (selling) "Invoices to businesses" else "Invoices from suppliers", onBack = onBack,
                subtitle = if (selling) "Sales sent to connected buyers" else "Purchases sent to you by connected sellers")
        },
        floatingActionButton = {
            if (selling && canSell) ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New invoice") })
        },
    ) { padding ->
        ResponsiveContent(Modifier.padding(padding)) {
            Column {
                SearchField(query, vm::setQuery, if (selling) "Invoice number or buyer" else "Invoice number or supplier",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(statuses) { s ->
                        FilterChip(selected = status == s, onClick = { vm.setStatus(s) },
                            label = { Text(s?.let { tradeStatusLabel(it, selling) } ?: "All") },
                            leadingIcon = s?.let { st -> { Icon(tradeStatusIcon(st), null, Modifier.size(18.dp), tint = tradeStatusColor(st)) } })
                    }
                }
                PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = { vm.load() }) {
                    LoadableContent(state, { vm.load() }) { page ->
                        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (page.content.isEmpty()) item {
                                if (selling) EmptyState("No invoices yet",
                                    "Send an invoice to a connected business. When they approve it, your stock goes down and theirs goes up automatically.",
                                    Icons.Default.ReceiptLong,
                                    action = if (canSell) { { Button(onClick = onCreate) { Text("New invoice") } } } else null)
                                else EmptyState("No invoices received",
                                    "When a connected supplier sends you an invoice, it appears here for you to approve.",
                                    Icons.Default.Inbox)
                            }
                            items(page.content, key = { it.id }) { inv -> InvoiceCard(inv, selling) { onOpen(inv.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceCard(inv: TradeInvoiceSummary, selling: Boolean, onClick: () -> Unit) {
    val tone = tradeStatusColor(inv.status)
    AppCard(onClick = onClick) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(tone))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(inv.counterparty.name, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(inv.counterparty.name, style = MaterialTheme.typography.titleSmall)
                        androidx.compose.material3.Text("${inv.invoiceNumber} · ${Fmt.date(inv.invoiceDate)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        androidx.compose.material3.Text(Fmt.money(inv.total, inv.currencyCode), style = MaterialTheme.typography.titleSmall)
                        Text("${inv.lineCount} items", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TradeStatusPill(inv.status, selling)
                    if (inv.returnPending) Pill("Return requested", MaterialTheme.appColors.warning, Icons.Default.AssignmentReturn)
                }
            }
        }
    }
}
