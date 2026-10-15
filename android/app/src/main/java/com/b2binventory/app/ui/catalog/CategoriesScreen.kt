package com.b2binventory.app.ui.catalog

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CategoriesViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<CategoryNode>>())
    val state = _state.asStateFlow()
    private val _showArchived = MutableStateFlow(false)
    val showArchived = _showArchived.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = _errors.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() = _state.load(viewModelScope) { api.categories(_showArchived.value).flatten() }

    fun toggleArchived() {
        _showArchived.update { !it }
        load()
    }

    fun save(id: Long?, name: String, description: String, parentId: Long?, onDone: () -> Unit) {
        if (name.isBlank()) {
            _errors.value = mapOf("name" to "Name is required")
            return
        }
        viewModelScope.launch {
            val request = CategoryRequest(name.trim(), description.blankToNull(), parentId)
            when (val r = apiCall { if (id == null) api.createCategory(request) else api.updateCategory(id, request) }) {
                is ApiResult.Ok -> {
                    _errors.value = emptyMap()
                    onDone()
                    load()
                }
                is ApiResult.Err -> _errors.value = r.fieldErrors.ifEmpty { mapOf("name" to r.message) }
            }
        }
    }

    fun setArchived(c: CategoryNode, archived: Boolean) {
        viewModelScope.launch {
            when (val r = apiCall { if (archived) api.archiveCategory(c.id) else api.restoreCategory(c.id) }) {
                is ApiResult.Ok -> load()
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }

    fun clearErrors() {
        _errors.value = emptyMap()
    }
}

private data class Editing(val category: CategoryNode?, val parentId: Long?)

@Composable
fun CategoriesScreen(onBack: () -> Unit, onAttributes: (Long?, String?) -> Unit) {
    val vm = appViewModel { c, _ -> CategoriesViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val showArchived by vm.showArchived.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val canManage = LocalMe.current?.can("CATEGORY_MANAGE") == true
    var editing by remember { mutableStateOf<Editing?>(null) }
    var archiveTarget by remember { mutableStateOf<CategoryNode?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(
        topBar = {
            AppTopBar("Categories & fields", onBack = onBack) {
                TextButton(onClick = vm::toggleArchived) { Text(if (showArchived) "Hide archived" else "Show archived") }
            }
        },
        floatingActionButton = {
            if (canManage) ExtendedFloatingActionButton(onClick = { editing = Editing(null, null) },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Category") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { list ->
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("Fields for all products") },
                            supportingContent = { Text("Custom fields that apply whatever the category") },
                            leadingContent = { Icon(Icons.Default.Tune, null) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                            modifier = Modifier.clickableRow { onAttributes(null, null) },
                        )
                        HorizontalDivider()
                    }
                    if (list.isEmpty()) {
                        item {
                            EmptyState("No categories yet", "Create categories that fit your business, such as Medicines, Books or Spare parts.",
                                Icons.Default.Category)
                        }
                    }
                    items(list, key = { it.id }) { c ->
                        var menu by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(c.name + if (c.archived) " (archived)" else "") },
                            supportingContent = { Text("${c.productCount} products" + (c.description?.let { " · $it" } ?: "")) },
                            leadingContent = {
                                Row {
                                    Spacer(Modifier.width((c.depth * 20).dp))
                                    Icon(if (c.depth == 0) Icons.Default.Folder else Icons.Default.SubdirectoryArrowRight, null)
                                }
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem({ Text("Custom fields") }, { menu = false; onAttributes(c.id, c.name) })
                                        if (canManage && !c.archived) {
                                            DropdownMenuItem({ Text("Add subcategory") }, { menu = false; editing = Editing(null, c.id) })
                                            DropdownMenuItem({ Text("Edit") }, { menu = false; editing = Editing(c, c.parentId) })
                                            DropdownMenuItem({ Text("Archive") }, { menu = false; archiveTarget = c })
                                        }
                                        if (canManage && c.archived) {
                                            DropdownMenuItem({ Text("Restore") }, { menu = false; vm.setArchived(c, false) })
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickableRow { onAttributes(c.id, c.name) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    editing?.let { ed ->
        val parents = state.data.orEmpty().filter { !it.archived && it.id != ed.category?.id }
        CategoryDialog(ed.category, ed.parentId, parents, errors,
            onSave = { name, desc, parent -> vm.save(ed.category?.id, name, desc, parent) { editing = null } },
            onDismiss = { editing = null; vm.clearErrors() })
    }
    archiveTarget?.let { c ->
        ConfirmDialog("Archive ${c.name}?", "It will no longer be offered for new products. Existing products keep it.", "Archive", true,
            onConfirm = { vm.setArchived(c, true); archiveTarget = null }, onDismiss = { archiveTarget = null })
    }
}

@Composable
private fun CategoryDialog(
    category: CategoryNode?,
    parentId: Long?,
    parents: List<CategoryNode>,
    errors: Map<String, String>,
    onSave: (String, String, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var description by remember { mutableStateOf(category?.description ?: "") }
    var parent by remember { mutableStateOf(parentId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it }, "Name", required = true, error = errors["name"])
                FormField(description, { description = it }, "Description", singleLine = false)
                DropdownField("Parent category", parents, parents.firstOrNull { it.id == parent }, { parent = it.id },
                    { it.path }, noneLabel = "None (top level)", onNone = { parent = null }, error = errors["parentId"])
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, description, parent) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
