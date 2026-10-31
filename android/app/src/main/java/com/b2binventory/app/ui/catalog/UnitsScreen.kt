package com.b2binventory.app.ui.catalog

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

data class UnitsData(val units: List<UnitDto>, val categories: List<UnitCategoryDto>, val conversions: List<Conversion>)

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

    fun load() = _state.load(viewModelScope) {
        UnitsData(api.units(includeArchived = true), api.unitCategories(), api.conversions())
    }

    fun saveUnit(existing: UnitDto?, code: String, name: String, decimals: Int, category: String, baseFactor: String,
                 onDone: () -> Unit) {
        val (request, errors) = unitRequest(code, name, decimals, category, baseFactor)
        if (request == null) {
            _errors.value = errors
            return
        }
        viewModelScope.launch {
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
                // The server explains why: built in (1 KG = 1000 G) or different dimensions (KG and M).
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
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(topBar = { AppTopBar("Units", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { data ->
                val groups = remember(data, query, category) { groupUnits(data.units, data.categories, query, category) }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                            Text("Units of measure", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (canManage) TextButton(onClick = { addingUnit = true }) { Text("Add custom unit") }
                        }
                        UnitFilters(query, { query = it }, data.categories, category, { category = it },
                            Modifier.padding(horizontal = 16.dp))
                    }
                    if (groups.isEmpty()) {
                        item {
                            Text("No units match your search.", Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    unitGroups(groups) { u ->
                        UnitRow(u, data.categories, trailing = if (u.standard || !canManage) null else ({
                            Row {
                                IconButton(onClick = { editing = u }) { Icon(Icons.Default.Edit, tr("Edit")) }
                                IconButton(onClick = { vm.setArchived(u, !u.archived) }) {
                                    Icon(if (u.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        tr(if (u.archived) "Restore" else "Archive"))
                                }
                            }
                        }))
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp)) {
                            Text("Conversions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (canManage) TextButton(onClick = { addingConversion = true }) { Text("Add conversion") }
                        }
                        Text("Standard units of one kind convert automatically (1 KG = 1000 G). Add conversions for " +
                            "packs and your own units, e.g. 1 BOX = 12 PCS.", Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(data.conversions, key = { "c${it.id}" }) { c ->
                        ListItem(
                            headlineContent = { Text("1 ${c.fromUnit.code} = ${Fmt.qty(c.factor)} ${c.toUnit.code}") },
                            supportingContent = c.productName?.let { { Text("Only for $it") } },
                            trailingContent = {
                                if (canManage) IconButton(onClick = { vm.deleteConversion(c) }) { Icon(Icons.Default.Delete, tr("Delete")) }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (addingUnit || editing != null) {
        UnitDialog(editing, state.data?.categories.orEmpty(), errors,
            onSave = { code, name, dec, cat, factor ->
                vm.saveUnit(editing, code, name, dec, cat, factor) { addingUnit = false; editing = null }
            },
            onDismiss = { addingUnit = false; editing = null; vm.clearErrors() })
    }
    if (addingConversion) {
        ConversionDialog(state.data?.units.orEmpty().filter { !it.archived }, state.data?.categories.orEmpty(), errors,
            onSave = { f, t, factor -> vm.addConversion(f, t, factor) { addingConversion = false } },
            onDismiss = { addingConversion = false; vm.clearErrors() })
    }
}

@Composable
private fun ConversionDialog(units: List<UnitDto>, categories: List<UnitCategoryDto>, errors: Map<String, String>,
                             onSave: (Long?, Long?, String) -> Unit, onDismiss: () -> Unit) {
    var from by remember { mutableStateOf<Long?>(null) }
    var to by remember { mutableStateOf<Long?>(null) }
    var factor by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New conversion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitPickerField(units, categories, from, { from = it.id }, label = "1 of")
                FormField(factor, { factor = it }, "equals", keyboardType = KeyboardType.Decimal, error = errors["factor"])
                UnitPickerField(units, categories, to, { to = it.id }, label = "of")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(from, to, factor) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
