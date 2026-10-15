package com.b2binventory.app.ui.stock

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StockCountsViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<StockCountSummary>>())
    val state = _state.asStateFlow()
    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()
    private val _categories = MutableStateFlow<List<CategoryNode>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _created = MutableStateFlow<Long?>(null)
    val created = _created.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            (apiCall { api.warehouses() } as? ApiResult.Ok)?.let { _warehouses.value = it.value }
            (apiCall { api.categories() } as? ApiResult.Ok)?.let { _categories.value = it.value.flatten() }
        }
    }

    fun load() = _state.load(viewModelScope) { api.stockCounts().content }

    fun create(warehouseId: Long?, categoryId: Long?, notes: String) {
        if (warehouseId == null) {
            _error.value = "Choose a warehouse"
            return
        }
        viewModelScope.launch {
            when (val r = apiCall { api.createStockCount(StockCountRequest(warehouseId, categoryId, notes.blankToNull())) }) {
                is ApiResult.Ok -> {
                    _error.value = null
                    _created.value = r.value.summary.id
                }
                is ApiResult.Err -> _error.value = r.message
            }
        }
    }

    fun consumeCreated() {
        _created.value = null
    }
}

@Composable
fun StockCountsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> StockCountsViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val warehouses by vm.warehouses.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val created by vm.created.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(created) {
        created?.let {
            creating = false
            vm.consumeCreated()
            onOpen(it)
        }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { AppTopBar("Stock counts", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New count") })
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { list ->
                if (list.isEmpty()) {
                    EmptyState("No stock counts", "Count what is physically on the shelves and reconcile differences.", Icons.AutoMirrored.Filled.FactCheck)
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(list, key = { it.id }) { c ->
                            ListItem(
                                headlineContent = { Text("${c.warehouseName} · ${Fmt.dateTime(c.createdAt)}") },
                                supportingContent = { Text("${c.countedLines} of ${c.lineCount} counted · by ${c.createdBy}") },
                                trailingContent = { AssistChip(onClick = { onOpen(c.id) }, label = { Text(c.status.lowercase().replaceFirstChar { it.uppercase() }) }) },
                                modifier = Modifier.clickable { onOpen(c.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        var warehouse by remember { mutableStateOf(warehouses.firstOrNull { it.isDefault }) }
        var category by remember { mutableStateOf<CategoryNode?>(null) }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New stock count") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    DropdownField("Warehouse", warehouses, warehouse, { warehouse = it }, { it.name }, required = true)
                    DropdownField("Category", categories, category, { category = it }, { it.path }, noneLabel = "All products",
                        onNone = { category = null })
                    FormField(notes, { notes = it }, "Notes")
                }
            },
            confirmButton = { TextButton(onClick = { vm.create(warehouse?.id, category?.id, notes) }) { Text("Start count") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

class StockCountDetailViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    private val id: Long = checkNotNull(saved["id"])
    private val _state = MutableStateFlow(Loadable<StockCountDetail>())
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() = _state.load(viewModelScope) { api.stockCount(id) }

    fun save(entries: Map<Long, String>, then: (() -> Unit)? = null) {
        val invalid = entries.filterValues { it.isNotBlank() && it.toDoubleOrNull()?.let { v -> v >= 0 } != true }
        if (invalid.isNotEmpty()) {
            viewModelScope.launch { _messages.emit("Some counted quantities are not valid numbers") }
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val lines = entries.map { (lineId, v) -> CountLineUpdate(lineId, v.toDoubleOrNull()) }
            when (val r = apiCall { api.updateCountLines(id, CountLinesRequest(lines)) }) {
                is ApiResult.Ok -> {
                    _state.value = Loadable(r.value, false)
                    if (then == null) _messages.emit("Counts saved")
                    _busy.value = false
                    then?.invoke()
                }
                is ApiResult.Err -> {
                    _busy.value = false
                    _messages.emit(r.message)
                }
            }
        }
    }

    fun reconcile(entries: Map<Long, String>) = save(entries) {
        _busy.value = true
        viewModelScope.launch {
            when (val r = apiCall { api.reconcile(id) }) {
                is ApiResult.Ok -> {
                    _messages.emit("Reconciled: ${r.value.adjustedLines} products adjusted")
                    load()
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
            _busy.value = false
        }
    }

    fun cancel() {
        viewModelScope.launch {
            when (val r = apiCall { api.cancelCount(id) }) {
                is ApiResult.Ok -> load()
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }
}

@Composable
fun StockCountDetailScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, s -> StockCountDetailViewModel(c.api, s) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val snackbar = remember { SnackbarHostState() }
    val entries: SnapshotStateMap<Long, String> = remember { mutableStateMapOf() }
    var confirmReconcile by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    LaunchedEffect(state.data) {
        state.data?.lines?.forEach { l -> if (!entries.containsKey(l.id)) entries[l.id] = l.countedQty?.let(Fmt::qty) ?: "" }
    }

    Scaffold(topBar = { AppTopBar("Stock count", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LoadableContent(state, vm::load) { detail ->
            val open = detail.summary.status == "OPEN"
            Column(Modifier.padding(padding)) {
                ListItem(
                    headlineContent = { Text(detail.summary.warehouseName, fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text("Started ${Fmt.dateTime(detail.summary.createdAt)} by ${detail.summary.createdBy} · " +
                            detail.summary.status.lowercase())
                    },
                )
                if (open) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.save(entries.toMap()) }, enabled = !busy) { Text("Save") }
                        if (me?.can("STOCK_ADJUST") == true) {
                            Button(onClick = { confirmReconcile = true }, enabled = !busy) { Text("Reconcile") }
                        }
                        TextButton(onClick = { confirmCancel = true }, enabled = !busy) { Text("Cancel count") }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(detail.lines, key = { it.id }) { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (line.variantName == line.productName) line.productName else "${line.productName} · ${line.variantName}",
                                    fontWeight = FontWeight.Medium)
                                Text(listOfNotNull(line.sku, line.lotNumber?.let { "lot $it" },
                                    "expected ${Fmt.qty(line.expectedQty)} ${line.unitCode}").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall)
                                line.difference?.takeIf { it != 0.0 }?.let {
                                    Text("Difference ${Fmt.signedQty(it)}", color = movementColor(it), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (open) {
                                OutlinedTextField(entries[line.id] ?: "", { entries[line.id] = it }, Modifier.width(110.dp),
                                    singleLine = true, label = { Text("Counted") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            } else {
                                Text(line.countedQty?.let(Fmt::qty) ?: "—")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (confirmReconcile) {
        ConfirmDialog("Reconcile count?", "On-hand quantities will be set to the counted values. Lines left empty are skipped. This creates count-correction movements and cannot be undone.",
            "Reconcile", onConfirm = { confirmReconcile = false; vm.reconcile(entries.toMap()) }, onDismiss = { confirmReconcile = false })
    }
    if (confirmCancel) {
        ConfirmDialog("Cancel this count?", "No stock will be changed.", "Cancel count", true,
            onConfirm = { confirmCancel = false; vm.cancel() }, onDismiss = { confirmCancel = false })
    }
}
