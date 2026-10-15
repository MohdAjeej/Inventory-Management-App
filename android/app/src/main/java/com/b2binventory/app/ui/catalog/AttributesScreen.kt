package com.b2binventory.app.ui.catalog

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val fieldTypes = listOf("TEXT", "NUMBER", "DATE", "BOOLEAN", "DROPDOWN", "MULTI_SELECT")

class AttributesViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    val categoryId: Long? = saved.get<String>("categoryId")?.toLongOrNull()
    val categoryName: String? = saved.get<String>("categoryName")
    private val _state = MutableStateFlow(Loadable<List<AttributeDef>>())
    val state = _state.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = _errors.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() = _state.load(viewModelScope) {
        api.attributes(categoryId = categoryId, includeArchived = true).filter { it.categoryId == categoryId }
    }

    fun save(existing: AttributeDef?, request: AttributeRequest, onDone: () -> Unit) {
        val errors = buildMap {
            if (request.label.isBlank()) put("label", "Label is required")
            if (!Regex("[a-z][a-z0-9_]{0,59}").matches(request.key)) put("key", "Lowercase letters, digits and _ only")
            if (request.fieldType in setOf("DROPDOWN", "MULTI_SELECT") && request.options.isEmpty()) put("options", "Add at least one option")
        }
        if (errors.isNotEmpty()) {
            _errors.value = errors
            return
        }
        viewModelScope.launch {
            when (val r = apiCall { if (existing == null) api.createAttribute(request) else api.updateAttribute(existing.id, request) }) {
                is ApiResult.Ok -> {
                    _errors.value = emptyMap()
                    onDone()
                    load()
                }
                is ApiResult.Err -> _errors.value = r.fieldErrors.ifEmpty { mapOf("label" to r.message) }
            }
        }
    }

    fun setArchived(a: AttributeDef, archived: Boolean) {
        viewModelScope.launch {
            when (val r = apiCall { if (archived) api.archiveAttribute(a.id) else api.restoreAttribute(a.id) }) {
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
fun AttributesScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, s -> AttributesViewModel(c.api, s) }
    val state by vm.state.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val canManage = LocalMe.current?.can("CATEGORY_MANAGE") == true
    var editing by remember { mutableStateOf<AttributeDef?>(null) }
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(
        topBar = { AppTopBar(vm.categoryName?.let { "Fields: $it" } ?: "Fields for all products", onBack = onBack) },
        floatingActionButton = {
            if (canManage) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Field") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { list ->
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    item {
                        Text(
                            if (vm.categoryId == null) "These fields appear on every product form."
                            else "These fields appear for products in this category and its subcategories, along with fields of parent categories.",
                            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (list.isEmpty()) item { EmptyState("No custom fields", "Add fields such as Author, Strength, Size or Warranty.", Icons.Default.Tune) }
                    items(list, key = { it.id }) { a ->
                        ListItem(
                            headlineContent = { Text(a.label + if (a.required) " *" else "") },
                            supportingContent = {
                                Text(listOfNotNull(Fmt.fieldType(a.fieldType), a.key, a.options.takeIf { it.isNotEmpty() }?.joinToString(),
                                    "archived".takeIf { a.archived }).joinToString(" · "))
                            },
                            trailingContent = {
                                if (canManage) {
                                    Row {
                                        IconButton(onClick = { editing = a }) { Icon(Icons.Default.Edit, "Edit") }
                                        IconButton(onClick = { vm.setArchived(a, !a.archived) }) {
                                            Icon(if (a.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                if (a.archived) "Restore" else "Archive")
                                        }
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        AttributeDialog(editing, vm.categoryId, errors,
            onSave = { req -> vm.save(editing, req) { adding = false; editing = null } },
            onDismiss = { adding = false; editing = null; vm.clearErrors() })
    }
}

@Composable
private fun AttributeDialog(
    existing: AttributeDef?,
    categoryId: Long?,
    errors: Map<String, String>,
    onSave: (AttributeRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var key by remember { mutableStateOf(existing?.key ?: "") }
    var keyEdited by remember { mutableStateOf(existing != null) }
    var type by remember { mutableStateOf(existing?.fieldType ?: "TEXT") }
    var required by remember { mutableStateOf(existing?.required ?: false) }
    var options by remember { mutableStateOf(existing?.options?.joinToString(", ") ?: "") }
    var sortOrder by remember { mutableStateOf(existing?.sortOrder?.toString() ?: "0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New field" else "Edit field") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(label, {
                    label = it
                    if (!keyEdited) key = it.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').let { k ->
                        if (k.firstOrNull()?.isLetter() == true) k else "f_$k"
                    }.take(60)
                }, "Label", required = true, error = errors["label"])
                FormField(key, { key = it; keyEdited = true }, "Key", required = true, error = errors["key"],
                    enabled = existing == null, supporting = "Internal name; cannot be changed later")
                DropdownField("Type", fieldTypes, type, { type = it }, Fmt::fieldType)
                if (type == "DROPDOWN" || type == "MULTI_SELECT") {
                    FormField(options, { options = it }, "Options", required = true, error = errors["options"],
                        supporting = "Separate with commas")
                }
                SwitchRow("Required", required, { required = it })
                FormField(sortOrder, { sortOrder = it.filter { c -> c.isDigit() || c == '-' } }, "Display order",
                    keyboardType = KeyboardType.Number)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(AttributeRequest(key.trim(), label.trim(), type, required,
                    options.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    sortOrder.toIntOrNull() ?: 0, categoryId))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
