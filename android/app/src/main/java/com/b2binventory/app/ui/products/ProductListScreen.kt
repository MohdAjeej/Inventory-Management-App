package com.b2binventory.app.ui.products

import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.Sort
import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProductListState(
    val items: List<ProductSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val totalPages: Int = 0,
    val total: Long = 0,
)

data class ProductQuery(val q: String = "", val categoryId: Long? = null, val status: String? = null, val archived: Boolean = false,
                        val sort: String = "name")

private val sortOptions = listOf("name" to "Name (A–Z)", "updated" to "Recently updated", "created" to "Newest first")

class ProductListViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    private val _query = MutableStateFlow(ProductQuery(status = saved.get<String>("status")))
    val query = _query.asStateFlow()
    private val _state = MutableStateFlow(ProductListState())
    val state = _state.asStateFlow()
    private val _categories = MutableStateFlow<List<CategoryNode>>(emptyList())
    val categories = _categories.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { (apiCall { api.categories() } as? ApiResult.Ok)?.let { _categories.value = it.value.flatten() } }
    }

    fun setQuery(transform: (ProductQuery) -> ProductQuery) {
        val before = _query.value
        _query.update(transform)
        if (before != _query.value) refresh(debounce = before.q != _query.value.q)
    }

    fun refresh(debounce: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(350)
            _state.update { it.copy(loading = true, error = null) }
            fetch(0)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.page + 1 >= s.totalPages) return
        _state.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch { fetch(s.page + 1) }
    }

    private suspend fun fetch(page: Int) {
        val q = _query.value
        when (val r = apiCall {
            api.products(q = q.q.blankToNull(), categoryId = q.categoryId, status = q.status, archived = q.archived,
                sort = q.sort, page = page)
        }) {
            is ApiResult.Ok -> _state.update {
                ProductListState(items = if (page == 0) r.value.content else it.items + r.value.content, loading = false,
                    page = r.value.page, totalPages = r.value.totalPages, total = r.value.totalElements)
            }
            is ApiResult.Err -> _state.update { it.copy(loading = false, loadingMore = false, error = r.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ProductListScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onStockOperation: (Long, String) -> Unit,
    onRecordSale: () -> Unit,
    onStockActivity: () -> Unit,
) {
    val vm = appViewModel { c, s -> ProductListViewModel(c.api, s) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val container = appContainer()
    val listState = rememberLazyListState()
    var detailed by rememberSaveable { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.items.size - 5) vm.loadMore() }
    }

    Scaffold(
        topBar = {
            AppTopBar("Inventory") {
                if (me?.can("SALE_RECORD") == true) {
                    FilledTonalButton(onClick = onRecordSale, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Default.PointOfSale, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Record sale")
                    }
                }
                IconButton(onClick = onStockActivity) { Icon(Icons.Default.History, "Stock activity") }
                IconButton(onClick = { detailed = !detailed }) {
                    Icon(if (detailed) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                        if (detailed) "Show compact list" else "Show detailed list")
                }
            }
        },
        floatingActionButton = {
            if (me?.can("PRODUCT_CREATE") == true) {
                ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add product") })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query.q, onValueChange = { v -> vm.setQuery { it.copy(q = v) } },
                placeholder = { Text("Search name, brand, SKU or barcode") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.q.isNotEmpty()) IconButton(onClick = { vm.setQuery { it.copy(q = "") } }) { Icon(Icons.Default.Clear, "Clear") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Stock status as one-tap chips with icon and text (not colour alone).
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf<String?>(null, "IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK")) { s ->
                    FilterChip(
                        selected = query.status == s,
                        onClick = { vm.setQuery { it.copy(status = s) } },
                        label = { Text(s?.let(Fmt::status) ?: "All") },
                        leadingIcon = s?.let { st -> { Icon(statusIcon(st), null, Modifier.size(18.dp), tint = statusColor(st)) } },
                    )
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CategoryFilter(categories, query.categoryId) { id -> vm.setQuery { it.copy(categoryId = id) } } }
                item { SortFilter(query.sort) { s -> vm.setQuery { it.copy(sort = s) } } }
                item {
                    FilterChip(selected = query.archived, onClick = { vm.setQuery { it.copy(archived = !it.archived) } },
                        label = { Text("Archived") })
                }
            }
            PullToRefreshBox(isRefreshing = state.loading && state.items.isNotEmpty(), onRefresh = { vm.refresh() }) {
                when {
                    state.loading && state.items.isEmpty() -> LoadingBox()
                    state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, { vm.refresh() })
                    state.items.isEmpty() && query.copy(sort = "name") == ProductQuery() -> EmptyState(
                        "No products yet", "Add your first product to start tracking stock.",
                        action = if (me?.can("PRODUCT_CREATE") == true) { { Button(onClick = onAdd) { Text("Add Product") } } } else null,
                    )
                    state.items.isEmpty() -> EmptyState(
                        "No matching products", "Try a different search or filter.", icon = Icons.Default.SearchOff,
                        action = { OutlinedButton(onClick = { vm.setQuery { ProductQuery(sort = it.sort) } }) { Text("Clear search and filters") } },
                    )
                    else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                        item {
                            Text("${state.total} products", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(state.items, key = { it.id }) { p ->
                            ProductRow(p, container.fileUrl(p.imageUrl), detailed, onClick = { onOpen(p.id) },
                                onStock = if (p.archived) null else { type -> onStockOperation(p.id, type) })
                        }
                        if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
            }
        }
    }
}

private fun statusIcon(status: String) = when (status) {
    "OUT_OF_STOCK" -> Icons.Default.RemoveCircleOutline
    "LOW_STOCK" -> Icons.Default.WarningAmber
    else -> Icons.Default.CheckCircleOutline
}

@Composable
private fun SortFilter(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected != "name", onClick = { open = true },
            label = { Text("Sort: " + (sortOptions.firstOrNull { it.first == selected }?.second ?: selected)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
        DropdownMenu(open, { open = false }) {
            sortOptions.forEach { (key, label) -> DropdownMenuItem({ Text(label) }, { onSelect(key); open = false }) }
        }
    }
}

@Composable
private fun CategoryFilter(categories: List<CategoryNode>, selected: Long?, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected != null, onClick = { open = true },
            label = { Text(categories.firstOrNull { it.id == selected }?.name ?: "Category", maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("All categories") }, { onSelect(null); open = false })
            categories.forEach { c ->
                DropdownMenuItem({ Text("  ".repeat(c.depth) + c.name) }, { onSelect(c.id); open = false })
            }
        }
    }
}

@Composable
private fun ProductRow(p: ProductSummary, imageUrl: String?, detailed: Boolean, onClick: () -> Unit,
                       onStock: ((String) -> Unit)?) {
    val me = LocalMe.current
    if (!detailed) {
        // Compact: one line per product with the essentials.
        Row(Modifier.fillMaxWidth().clickableRow(onClick).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${Fmt.qty(p.stock.available)} ${p.unitCode}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            StatusChip(p.status)
        }
        HorizontalDivider()
        return
    }
    ListItem(
        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                Text(listOfNotNull(p.sku?.let { "SKU $it" }, p.categoryName, p.brand).joinToString(" · ").ifEmpty { "No SKU or category" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onStock != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (me?.can("STOCK_ADD") == true) {
                            TextButton(onClick = { onStock("ADD") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("Add")
                            }
                        }
                        if (me?.can("STOCK_REDUCE") == true) {
                            TextButton(onClick = { onStock("REDUCE") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.Remove, null, Modifier.size(18.dp)); Text("Reduce")
                            }
                        }
                        if (me?.can("SALE_RECORD") == true) {
                            TextButton(onClick = { onStock("SALE") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.PointOfSale, null, Modifier.size(18.dp)); Text("Sell")
                            }
                        }
                    }
                }
            }
        },
        leadingContent = {
            if (imageUrl != null) {
                AsyncImage(imageUrl, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else {
                Surface(Modifier.size(48.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, null) }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text("${Fmt.qty(p.stock.available)} ${p.unitCode}", fontWeight = FontWeight.SemiBold)
                StatusChip(p.status)
            }
        },
        modifier = Modifier.then(Modifier.clickableRow(onClick)),
    )
    HorizontalDivider()
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
