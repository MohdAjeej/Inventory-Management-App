package com.b2binventory.app.ui.trade

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

class BusinessNetworkViewModel(private val api: ApiService) : ViewModel() {
    private val _connections = MutableStateFlow(Loadable<List<Connection>>())
    val connections = _connections.asStateFlow()
    private val _me = MutableStateFlow<MyBusinessCode?>(null)
    val me = _me.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _results = MutableStateFlow(Loadable<List<BusinessSearchResult>>(data = emptyList(), loading = false))
    val results = _results.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var searchJob: Job? = null

    fun load() {
        _connections.load(viewModelScope) { api.connections() }
        viewModelScope.launch { (apiCall { api.myBusinessCode() } as? ApiResult.Ok)?.let { _me.value = it.value } }
    }

    fun search(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _results.value = Loadable(emptyList(), false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _results.load(this) { api.searchBusinesses(q.trim()) }
        }
    }

    private fun act(done: String, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> {
                    _messages.emit(done)
                    load()
                    if (_query.value.trim().length >= 2) _results.load(this) { api.searchBusinesses(_query.value.trim()) }
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
            _busy.value = false
        }
    }

    fun connect(businessId: Long) = act("Request sent") { api.requestConnection(ConnectionRequestBody(businessId)) }
    fun accept(id: Long) = act("Connected") { api.acceptConnection(id) }
    fun reject(id: Long) = act("Request declined") { api.rejectConnection(id) }
    fun remove(id: Long) = act("Connection removed") { api.removeConnection(id) }
    fun setDiscoverable(on: Boolean) = act(if (on) "Other businesses can find you" else "Hidden from search") {
        _me.value = api.setDiscoverable(DiscoverableBody(on))
    }
}

private enum class NetworkTab(val label: String) { CONNECTED("Connected"), REQUESTS("Requests"), FIND("Find business") }

@Composable
fun BusinessNetworkScreen(onBack: () -> Unit, onCreateInvoice: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> BusinessNetworkViewModel(c.api) }
    val connections by vm.connections.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canManage = LocalMe.current?.can("BUSINESS_SETTINGS") == true
    val canSell = LocalMe.current?.can("SALE_RECORD") == true
    var tab by rememberSaveable { mutableStateOf(NetworkTab.CONNECTED) }
    var removing by remember { mutableStateOf<Connection?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }

    val all = connections.data.orEmpty()
    val connected = all.filter { it.status == "ACCEPTED" }
    val incoming = all.filter { it.status == "PENDING" && it.direction == "INCOMING" }
    val outgoing = all.filter { it.status == "PENDING" && it.direction == "OUTGOING" }

    Scaffold(topBar = { AppTopBar("Business network", onBack = onBack, subtitle = "Trade with other businesses on the app") },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        ResponsiveContent(Modifier.padding(padding)) {
            Column {
                TabRow(selectedTabIndex = tab.ordinal) {
                    NetworkTab.entries.forEach { t ->
                        Tab(tab == t, { tab = t }, text = {
                            Text(if (t == NetworkTab.REQUESTS && incoming.isNotEmpty()) "${tr(t.label)} (${incoming.size})" else t.label)
                        })
                    }
                }
                when (tab) {
                    NetworkTab.CONNECTED -> LoadableContent(connections, vm::load) {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { MyCodeCard(me, canManage, vm::setDiscoverable) }
                            if (connected.isEmpty()) item {
                                EmptyState("No connected businesses yet",
                                    "Find your suppliers or customers and connect. Then you can send and receive invoices that update stock automatically.",
                                    Icons.Default.Hub, action = { Button(onClick = { tab = NetworkTab.FIND }) { Text("Find a business") } })
                            }
                            items(connected, key = { it.id }) { c ->
                                ConnectionCard(c) {
                                    if (canSell) TextButton(onClick = { onCreateInvoice(c.business.id) }) {
                                        Icon(Icons.Default.ReceiptLong, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Invoice")
                                    }
                                    if (canManage) IconButton(onClick = { removing = c }, enabled = !busy) {
                                        Icon(Icons.Default.LinkOff, tr("Remove connection"))
                                    }
                                }
                            }
                        }
                    }
                    NetworkTab.REQUESTS -> LoadableContent(connections, vm::load) {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (incoming.isEmpty() && outgoing.isEmpty()) item {
                                EmptyState("No requests", "Connection requests you send or receive appear here.", Icons.Default.MarkEmailRead)
                            }
                            if (incoming.isNotEmpty()) item { SectionHeader("Asking to connect with you") }
                            items(incoming, key = { it.id }) { c ->
                                ConnectionCard(c) {
                                    if (canManage) {
                                        OutlinedButton(onClick = { vm.reject(c.id) }, enabled = !busy) { Text("Decline") }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { vm.accept(c.id) }, enabled = !busy) { Text("Accept") }
                                    }
                                }
                            }
                            if (outgoing.isNotEmpty()) item { SectionHeader("Waiting for their answer") }
                            items(outgoing, key = { it.id }) { c ->
                                ConnectionCard(c) {
                                    if (canManage) TextButton(onClick = { vm.remove(c.id) }, enabled = !busy) { Text("Withdraw") }
                                }
                            }
                            if (!canManage && incoming.isNotEmpty()) item {
                                MessageBanner(BannerKind.INFO, "Ask an administrator to accept or decline requests.")
                            }
                        }
                    }
                    NetworkTab.FIND -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchField(query, vm::search, "Business name, code or phone number")
                        Text("Only the business name, type, city and code are shown. Contact details are shared once you trade.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (results.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        results.error?.let { MessageBanner(BannerKind.ERROR, it) }
                        val list = results.data.orEmpty()
                        if (!results.loading && query.trim().length >= 2 && list.isEmpty() && results.error == null) {
                            EmptyState("No business found", "Check the spelling, or ask them for their business code.", Icons.Default.SearchOff)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(list, key = { it.business.id }) { r ->
                                SearchResultCard(r, canManage, busy, onConnect = { vm.connect(r.business.id) },
                                    onAccept = { r.connectionId?.let(vm::accept) })
                            }
                        }
                    }
                }
            }
        }
    }
    removing?.let { c ->
        ConfirmDialog("Remove ${c.business.name}?", "You will no longer be able to send each other new invoices. Past invoices stay.",
            "Remove", destructive = true, onConfirm = { vm.remove(c.id); removing = null }, onDismiss = { removing = null })
    }
}

@Composable
private fun MyCodeCard(me: MyBusinessCode?, canManage: Boolean, onDiscoverable: (Boolean) -> Unit) {
    val context = LocalContext.current
    AppCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your business code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(me?.businessCode ?: "…", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                        tr("Connect with ${me?.name ?: ""} on the Inventory app. Our business code: ${me?.businessCode ?: ""}"))
                    context.startActivity(Intent.createChooser(share, null))
                }, enabled = me?.businessCode != null) { Icon(Icons.Default.Share, tr("Share code")) }
            }
            Text("Share it with suppliers and customers so they can find you.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canManage && me != null) {
                SwitchRow("Let other businesses find us", me.discoverable, onDiscoverable,
                    "When off, you can still connect by sending requests yourself.")
            }
        }
    }
}

@Composable
private fun ConnectionCard(c: Connection, actions: @Composable RowScope.() -> Unit) {
    AppCard {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.business.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            BusinessText(c.business, Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
private fun SearchResultCard(r: BusinessSearchResult, canManage: Boolean, busy: Boolean, onConnect: () -> Unit, onAccept: () -> Unit) {
    AppCard {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.business.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            BusinessText(r.business, Modifier.weight(1f))
            when {
                r.connectionStatus == "ACCEPTED" -> Pill("Connected", MaterialTheme.appColors.success, Icons.Default.Link)
                r.connectionStatus == "PENDING" && r.direction == "OUTGOING" -> Pill("Requested", MaterialTheme.appColors.warning, Icons.Default.HourglassTop)
                r.connectionStatus == "PENDING" && canManage -> Button(onClick = onAccept, enabled = !busy) { Text("Accept") }
                canManage -> FilledTonalButton(onClick = onConnect, enabled = !busy) {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Connect")
                }
            }
        }
    }
}

@Composable
fun BusinessText(b: BusinessCard, modifier: Modifier = Modifier) {
    Column(modifier) {
        androidx.compose.material3.Text(b.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        androidx.compose.material3.Text(listOfNotNull(b.businessType, b.city, b.businessCode).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
