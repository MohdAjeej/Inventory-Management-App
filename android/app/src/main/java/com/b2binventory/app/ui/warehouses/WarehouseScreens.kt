package com.b2binventory.app.ui.warehouses

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WarehousesViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<Warehouse>>())
    val state = _state.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = _errors.asStateFlow()

    init {
        load()
    }

    fun load() = _state.load(viewModelScope) { api.warehouses(includeArchived = true) }

    fun create(request: WarehouseRequest, onDone: () -> Unit) {
        val errors = buildMap {
            if (request.name.isBlank()) put("name", "Name is required")
            if (request.code.isBlank()) put("code", "Code is required")
        }
        if (errors.isNotEmpty()) {
            _errors.value = errors
            return
        }
        viewModelScope.launch {
            when (val r = apiCall { api.createWarehouse(request) }) {
                is ApiResult.Ok -> {
                    _errors.value = emptyMap()
                    onDone()
                    load()
                }
                is ApiResult.Err -> _errors.value = r.fieldErrors.ifEmpty { mapOf("code" to r.message) }
            }
        }
    }

    fun clearErrors() {
        _errors.value = emptyMap()
    }
}

@Composable
fun WarehousesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> WarehousesViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val canManage = LocalMe.current?.can("WAREHOUSE_MANAGE") == true
    var creating by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { AppTopBar("Warehouses & branches", onBack = onBack) },
        floatingActionButton = {
            if (canManage) ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Warehouse") })
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { list ->
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(list, key = { it.id }) { w ->
                        ListItem(
                            headlineContent = {
                                Text(w.name + listOfNotNull(" · default".takeIf { w.isDefault }, " · archived".takeIf { w.archived })
                                    .joinToString(""))
                            },
                            supportingContent = {
                                Text("${w.code}${if (w.branch) " · branch" else ""} · ${w.productCount} products · ${Fmt.qty(w.totalQuantity)} units")
                            },
                            leadingContent = { Icon(if (w.branch) Icons.Default.Storefront else Icons.Default.Warehouse, null) },
                            modifier = Modifier.clickable { onOpen(w.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (creating) {
        WarehouseDialog(null, errors, onSave = { vm.create(it) { creating = false } },
            onDismiss = { creating = false; vm.clearErrors() })
    }
}

@Composable
fun WarehouseDialog(existing: Warehouse?, errors: Map<String, String>, onSave: (WarehouseRequest) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var branch by remember { mutableStateOf(existing?.branch ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New warehouse" else "Edit warehouse") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it }, "Name", required = true, error = errors["name"])
                FormField(code, { code = it.uppercase() }, "Short code", required = true, error = errors["code"], placeholder = "e.g. NORTH")
                FormField(address, { address = it }, "Address", singleLine = false)
                SwitchRow("This is a branch or store", branch, { branch = it })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(WarehouseRequest(name.trim(), code.trim(), address.blankToNull(), branch)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

data class WarehouseData(val warehouse: Warehouse, val locations: List<LocationDto>, val stock: List<InventoryRow>?)

class WarehouseDetailViewModel(private val api: ApiService, saved: SavedStateHandle, private val canViewReports: Boolean) : ViewModel() {
    private val id: Long = checkNotNull(saved["id"])
    private val _state = MutableStateFlow(Loadable<WarehouseData>())
    val state = _state.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = _errors.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() = _state.load(viewModelScope) {
        val warehouse = api.warehouses(includeArchived = true).first { it.id == id }
        val stock = if (canViewReports) api.inventoryReport(warehouseId = id, size = 500).content.filter { it.onHand != 0.0 } else null
        WarehouseData(warehouse, api.locations(id), stock)
    }

    private fun act(message: String, block: suspend () -> Any) {
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> {
                    _messages.emit(message)
                    load()
                }
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }

    fun update(request: WarehouseRequest, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = apiCall { api.updateWarehouse(id, request) }) {
                is ApiResult.Ok -> {
                    _errors.value = emptyMap()
                    onDone()
                    load()
                }
                is ApiResult.Err -> _errors.value = r.fieldErrors.ifEmpty { mapOf("code" to r.message) }
            }
        }
    }

    fun makeDefault() = act("Default warehouse changed") { api.makeDefaultWarehouse(id) }
    fun archive() = act("Warehouse archived") { api.archiveWarehouse(id) }
    fun restore() = act("Warehouse restored") { api.restoreWarehouse(id) }

    fun saveLocation(existing: LocationDto?, code: String, description: String, onDone: () -> Unit) {
        if (code.isBlank()) {
            _errors.value = mapOf("location" to "Code is required")
            return
        }
        viewModelScope.launch {
            val request = LocationRequest(code.trim(), description.blankToNull())
            when (val r = apiCall { if (existing == null) api.createLocation(id, request) else api.updateLocation(existing.id, request) }) {
                is ApiResult.Ok -> {
                    _errors.value = emptyMap()
                    onDone()
                    load()
                }
                is ApiResult.Err -> _errors.value = mapOf("location" to r.message)
            }
        }
    }

    fun setLocationArchived(l: LocationDto) = act(if (l.archived) "Location restored" else "Location archived") {
        if (l.archived) api.restoreLocation(l.id) else api.archiveLocation(l.id)
    }

    fun clearErrors() {
        _errors.value = emptyMap()
    }
}

@Composable
fun WarehouseDetailScreen(onBack: () -> Unit) {
    val me = LocalMe.current
    val vm = appViewModel { c, s -> WarehouseDetailViewModel(c.api, s, me?.can("REPORT_VIEW") == true) }
    val state by vm.state.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val canManage = me?.can("WAREHOUSE_MANAGE") == true
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf(false) }
    var editingLocation by remember { mutableStateOf<LocationDto?>(null) }
    var addingLocation by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(topBar = {
        AppTopBar(state.data?.warehouse?.name ?: "Warehouse", onBack = onBack) {
            if (canManage && state.data != null) IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit") }
        }
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LoadableContent(state, vm::load) { d ->
            val w = d.warehouse
            FormColumn(padding) {
                SectionCard {
                    Text(w.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LabelValue("Code", w.code)
                    LabelValue("Type", if (w.branch) "Branch / store" else "Warehouse")
                    LabelValue("Address", w.address)
                    LabelValue("Products in stock", w.productCount.toString())
                    LabelValue("Total quantity", Fmt.qty(w.totalQuantity))
                    if (w.isDefault) Text("Default warehouse for new stock", color = MaterialTheme.colorScheme.primary)
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!w.isDefault && !w.archived) OutlinedButton(onClick = vm::makeDefault) { Text("Make default") }
                            if (w.archived) OutlinedButton(onClick = vm::restore) { Text("Restore") }
                            else if (!w.isDefault) OutlinedButton(onClick = { confirmArchive = true }) { Text("Archive") }
                        }
                    }
                }
                SectionCard("Storage locations") {
                    Text("Bins, shelves or rooms inside this warehouse.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (d.locations.isEmpty()) Text("No locations defined")
                    d.locations.forEach { l ->
                        ListItem(
                            headlineContent = { Text(l.code + if (l.archived) " (archived)" else "") },
                            supportingContent = l.description?.let { { Text(it) } },
                            trailingContent = {
                                if (canManage) Row {
                                    IconButton(onClick = { editingLocation = l }) { Icon(Icons.Default.Edit, "Edit") }
                                    IconButton(onClick = { vm.setLocationArchived(l) }) {
                                        Icon(if (l.archived) Icons.Default.Unarchive else Icons.Default.Archive, null)
                                    }
                                }
                            },
                        )
                    }
                    if (canManage) TextButton(onClick = { addingLocation = true }) { Text("Add location") }
                }
                d.stock?.let { rows ->
                    SectionCard("Stock in this warehouse") {
                        if (rows.isEmpty()) Text("No stock here yet")
                        rows.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(if (r.variantName == r.productName) r.productName else "${r.productName} · ${r.variantName}",
                                    Modifier.weight(1f))
                                Text("${Fmt.qty(r.onHand)} ${r.unit}")
                            }
                        }
                    }
                }
            }
            if (editing) {
                WarehouseDialog(w, errors, onSave = { vm.update(it) { editing = false } }, onDismiss = { editing = false; vm.clearErrors() })
            }
        }
    }
    if (addingLocation || editingLocation != null) {
        var code by remember { mutableStateOf(editingLocation?.code ?: "") }
        var description by remember { mutableStateOf(editingLocation?.description ?: "") }
        AlertDialog(
            onDismissRequest = { addingLocation = false; editingLocation = null; vm.clearErrors() },
            title = { Text(if (editingLocation == null) "New location" else "Edit location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(code, { code = it }, "Code", required = true, error = errors["location"], placeholder = "e.g. A-01")
                    FormField(description, { description = it }, "Description")
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveLocation(editingLocation, code, description) { addingLocation = false; editingLocation = null } }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { addingLocation = false; editingLocation = null; vm.clearErrors() }) { Text("Cancel") } },
        )
    }
    if (confirmArchive) {
        ConfirmDialog("Archive warehouse?", "Only empty warehouses can be archived. It will no longer be offered for stock operations.",
            "Archive", true, onConfirm = { confirmArchive = false; vm.archive() }, onDismiss = { confirmArchive = false })
    }
}
