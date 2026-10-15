package com.b2binventory.app.ui.catalog

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UnitsData(val units: List<UnitDto>, val conversions: List<Conversion>)

class UnitsViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<UnitsData>())
    val state = _state.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = _errors.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() = _state.load(viewModelScope) { UnitsData(api.units(includeArchived = true), api.conversions()) }

    fun saveUnit(existing: UnitDto?, code: String, name: String, decimals: Int, onDone: () -> Unit) {
        val errors = buildMap {
            if (code.isBlank()) put("code", "Code is required")
            if (name.isBlank()) put("name", "Name is required")
        }
        if (errors.isNotEmpty()) {
            _errors.value = errors
            return
        }
        viewModelScope.launch {
            val request = UnitRequest(code.trim(), name.trim(), decimals)
            when (val r = apiCall { if (existing == null) api.createUnit(request) else api.updateUnit(existing.id, request) }) {
                is ApiResult.Ok -> {
                    _errors.value = emptyMap()
                    onDone()
                    load()
                }
                is ApiResult.Err -> _errors.value = r.fieldErrors.ifEmpty { mapOf("code" to r.message) }
            }
        }
    }

    fun setArchived(u: UnitDto, archived: Boolean) = act { if (archived) api.archiveUnit(u.id) else api.restoreUnit(u.id) }

    fun addConversion(from: Long?, to: Long?, factor: String, onDone: () -> Unit) {
        val f = factor.toDoubleOrNull()
        if (from == null || to == null || f == null || f <= 0) {
            _errors.value = mapOf("factor" to "Choose two units and a positive factor")
            return
        }
        viewModelScope.launch {
            when (val r = apiCall { api.createConversion(ConversionRequest(from, to, f)) }) {
                is ApiResult.Ok -> {
                    _errors.value = emptyMap()
                    onDone()
                    load()
                }
                is ApiResult.Err -> _errors.value = mapOf("factor" to r.message)
            }
        }
    }

    fun deleteConversion(c: Conversion) = act { api.deleteConversion(c.id).requireSuccess() }

    private fun act(block: suspend () -> Any) {
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> load()
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }

    fun clearErrors() {
        _errors.value = emptyMap()
    }
}

@Composable
fun UnitsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> UnitsViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val canManage = LocalMe.current?.can("CATEGORY_MANAGE") == true
    var editing by remember { mutableStateOf<UnitDto?>(null) }
    var addingUnit by remember { mutableStateOf(false) }
    var addingConversion by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(topBar = { AppTopBar("Units", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { data ->
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                            Text("Units of measure", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (canManage) TextButton(onClick = { addingUnit = true }) { Text("Add unit") }
                        }
                    }
                    items(data.units, key = { it.id }) { u ->
                        ListItem(
                            headlineContent = { Text("${u.name} (${u.code})" + if (u.archived) " · archived" else "") },
                            supportingContent = {
                                Text(if (u.decimals == 0) "Whole numbers" else "Up to ${u.decimals} decimal places")
                            },
                            trailingContent = {
                                if (canManage) Row {
                                    IconButton(onClick = { editing = u }) { Icon(Icons.Default.Edit, "Edit") }
                                    IconButton(onClick = { vm.setArchived(u, !u.archived) }) {
                                        Icon(if (u.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                                            if (u.archived) "Restore" else "Archive")
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp)) {
                            Text("Conversions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (canManage) TextButton(onClick = { addingConversion = true }) { Text("Add conversion") }
                        }
                    }
                    if (data.conversions.isEmpty()) {
                        item { Text("No conversions. Example: 1 box = 12 pieces.", Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(data.conversions, key = { "c${it.id}" }) { c ->
                        ListItem(
                            headlineContent = { Text("1 ${c.fromUnit.code} = ${Fmt.qty(c.factor)} ${c.toUnit.code}") },
                            supportingContent = c.productName?.let { { Text("Only for $it") } },
                            trailingContent = {
                                if (canManage) IconButton(onClick = { vm.deleteConversion(c) }) { Icon(Icons.Default.Delete, "Delete") }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (addingUnit || editing != null) {
        UnitDialog(editing, errors, onSave = { code, name, dec -> vm.saveUnit(editing, code, name, dec) { addingUnit = false; editing = null } },
            onDismiss = { addingUnit = false; editing = null; vm.clearErrors() })
    }
    if (addingConversion) {
        ConversionDialog(state.data?.units.orEmpty().filter { !it.archived }, errors,
            onSave = { f, t, factor -> vm.addConversion(f, t, factor) { addingConversion = false } },
            onDismiss = { addingConversion = false; vm.clearErrors() })
    }
}

@Composable
private fun UnitDialog(unit: UnitDto?, errors: Map<String, String>, onSave: (String, String, Int) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf(unit?.code ?: "") }
    var name by remember { mutableStateOf(unit?.name ?: "") }
    var decimals by remember { mutableIntStateOf(unit?.decimals ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (unit == null) "New unit" else "Edit unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(code, { code = it }, "Code", required = true, error = errors["code"], placeholder = "e.g. pkt")
                FormField(name, { name = it }, "Name", required = true, error = errors["name"], placeholder = "e.g. Packets")
                DropdownField("Decimal places", (0..4).toList(), decimals, { decimals = it },
                    { if (it == 0) "0 (whole numbers)" else it.toString() })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(code, name, decimals) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConversionDialog(units: List<UnitDto>, errors: Map<String, String>, onSave: (Long?, Long?, String) -> Unit,
                             onDismiss: () -> Unit) {
    var from by remember { mutableStateOf<UnitDto?>(null) }
    var to by remember { mutableStateOf<UnitDto?>(null) }
    var factor by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New conversion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("1 of", units, from, { from = it }, { "${it.name} (${it.code})" })
                FormField(factor, { factor = it }, "equals", keyboardType = KeyboardType.Decimal, error = errors["factor"])
                DropdownField("of", units, to, { to = it }, { "${it.name} (${it.code})" })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(from?.id, to?.id, factor) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
