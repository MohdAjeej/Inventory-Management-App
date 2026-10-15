package com.b2binventory.app.ui.reports

import com.b2binventory.app.ui.common.Text
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.dashboard.MovementRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

enum class ReportTab(val label: String, val key: String) {
    INVENTORY("Inventory", "inventory"),
    LOW("Low stock", "low-stock"),
    MOVEMENTS("Movements", "movements"),
}

data class ReportFilters(
    val warehouse: Warehouse? = null,
    val category: CategoryNode? = null,
    val from: String = "",
    val to: String = "",
    val type: String? = null,
)

data class ReportData(val rows: List<InventoryRow> = emptyList(), val movements: List<Movement> = emptyList(), val total: Long = 0)

class ReportsViewModel(private val api: ApiService) : ViewModel() {
    private val _tab = MutableStateFlow(ReportTab.INVENTORY)
    val tab = _tab.asStateFlow()
    private val _filters = MutableStateFlow(ReportFilters())
    val filters = _filters.asStateFlow()
    private val _state = MutableStateFlow(Loadable<ReportData>())
    val state = _state.asStateFlow()
    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()
    private val _categories = MutableStateFlow<List<CategoryNode>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            (apiCall { api.warehouses(includeArchived = true) } as? ApiResult.Ok)?.let { _warehouses.value = it.value }
            (apiCall { api.categories() } as? ApiResult.Ok)?.let { _categories.value = it.value.flatten() }
        }
        load()
    }

    fun setTab(t: ReportTab) {
        _tab.value = t
        load()
    }

    fun setFilters(transform: (ReportFilters) -> ReportFilters) {
        _filters.update(transform)
        load()
    }

    fun load() {
        val f = _filters.value
        _state.load(viewModelScope) {
            when (_tab.value) {
                ReportTab.INVENTORY -> api.inventoryReport(f.warehouse?.id, f.category?.id, size = 500).let { ReportData(rows = it.content, total = it.totalElements) }
                ReportTab.LOW -> api.lowStockReport(f.warehouse?.id, f.category?.id, size = 500).let { ReportData(rows = it.content, total = it.totalElements) }
                ReportTab.MOVEMENTS -> api.movementReport(f.warehouse?.id, f.type?.let { listOf(it) }, f.from.blankToNull(),
                    f.to.blankToNull(), size = 200).let { ReportData(movements = it.content, total = it.totalElements) }
            }
        }
    }

    fun export(context: Context, format: String, onSaved: (Uri, String) -> Unit) {
        val f = _filters.value
        val tab = _tab.value
        _exporting.value = true
        viewModelScope.launch {
            val result = apiCall {
                val response = api.export(tab.key, format, f.warehouse?.id,
                    if (tab != ReportTab.MOVEMENTS) f.category?.id else null,
                    if (tab == ReportTab.MOVEMENTS) f.type?.let { listOf(it) } else null,
                    if (tab == ReportTab.MOVEMENTS) f.from.blankToNull() else null,
                    if (tab == ReportTab.MOVEMENTS) f.to.blankToNull() else null)
                if (!response.isSuccessful) throw HttpException(response)
                val body = response.body() ?: error("Empty file")
                val name = response.headers()["Content-Disposition"]?.substringAfter("filename=", "")?.trim('"')
                    ?.takeIf { it.isNotBlank() } ?: "${tab.key}-report.$format"
                val mime = if (format == "xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv"
                val uri = withContext(Dispatchers.IO) { ExportSaver.save(context, body, name, mime) }
                Triple(uri, mime, name)
            }
            _exporting.value = false
            when (result) {
                is ApiResult.Ok -> {
                    _messages.emit("Saved ${result.value.third} to Downloads")
                    onSaved(result.value.first, result.value.second)
                }
                is ApiResult.Err -> _messages.emit(result.message)
            }
        }
    }
}

private enum class ReportSection(val label: String) { ANALYTICS("Sales analytics"), STOCK("Stock reports") }

/**
 * Reports tab: sales analytics and stock reports, each shown only when the member may view it.
 * A switch at the top moves between them when both are available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onOpenProduct: (Long) -> Unit,
    onAddStock: (Long) -> Unit,
    onRecordSale: () -> Unit,
) {
    val me = LocalMe.current
    val sections = buildList {
        if (me?.can("ANALYTICS_VIEW") == true) add(ReportSection.ANALYTICS)
        if (me?.can("REPORT_VIEW") == true) add(ReportSection.STOCK)
    }
    if (sections.isEmpty()) {
        Scaffold(topBar = { AppTopBar("Analytics") }) { p ->
            ErrorState("You do not have permission to view reports.", modifier = Modifier.padding(p))
        }
        return
    }
    var selected by rememberSaveable { mutableStateOf(sections.first()) }
    if (selected !in sections) selected = sections.first()
    val header: @Composable () -> Unit = {
        if (sections.size > 1) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                sections.forEachIndexed { i, s ->
                    SegmentedButton(selected = selected == s, onClick = { selected = s },
                        shape = SegmentedButtonDefaults.itemShape(i, sections.size)) { Text(s.label) }
                }
            }
        }
    }
    when (selected) {
        ReportSection.ANALYTICS -> com.b2binventory.app.ui.analytics.AnalyticsScreen(header, onOpenProduct, onAddStock, onRecordSale)
        ReportSection.STOCK -> StockReportsScreen(header, onOpenProduct)
    }
}

@Composable
private fun StockReportsScreen(header: @Composable () -> Unit, onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> ReportsViewModel(c.api) }
    val tab by vm.tab.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val warehouses by vm.warehouses.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var exportMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(
        topBar = {
            AppTopBar("Reports") {
                if (me?.can("REPORT_EXPORT") == true) {
                    Box {
                        if (exporting) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                        else IconButton(onClick = { exportMenu = true }) { Icon(Icons.Default.Download, "Export") }
                        DropdownMenu(exportMenu, { exportMenu = false }) {
                            DropdownMenuItem({ Text("Export CSV") }, {
                                exportMenu = false
                                vm.export(context, "csv") { uri, mime -> ExportSaver.open(context, uri, mime) }
                            })
                            DropdownMenuItem({ Text("Export Excel (.xlsx)") }, {
                                exportMenu = false
                                vm.export(context, "xlsx") { uri, mime -> ExportSaver.open(context, uri, mime) }
                            })
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            header()
            TabRow(selectedTabIndex = tab.ordinal) {
                ReportTab.entries.forEach { t -> Tab(tab == t, { vm.setTab(t) }, text = { Text(t.label) }) }
            }
            LazyRow(contentPadding = PaddingValues(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ChipMenu(filters.warehouse?.name ?: "All warehouses", filters.warehouse != null,
                        listOf<Warehouse?>(null) + warehouses, { it?.name ?: "All warehouses" }) { w -> vm.setFilters { it.copy(warehouse = w) } }
                }
                if (tab != ReportTab.MOVEMENTS) {
                    item {
                        ChipMenu(filters.category?.name ?: "All categories", filters.category != null,
                            listOf<CategoryNode?>(null) + categories, { it?.path ?: "All categories" }) { c -> vm.setFilters { it.copy(category = c) } }
                    }
                } else {
                    item {
                        ChipMenu(filters.type?.let(Fmt::movement) ?: "All types", filters.type != null,
                            listOf<String?>(null, "OPENING", "ADD", "REDUCE", "ADJUST", "TRANSFER_OUT", "TRANSFER_IN", "COUNT_ADJUST",
                                "DAMAGE", "RESERVE", "RELEASE", "SALE", "SALE_CANCEL", "RETURN_IN", "PURCHASE", "PURCHASE_CANCEL"),
                            { it?.let(Fmt::movement) ?: "All types" }) { t -> vm.setFilters { it.copy(type = t) } }
                    }
                }
            }
            if (tab == ReportTab.MOVEMENTS) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("From", filters.from, { v -> vm.setFilters { it.copy(from = v) } }, Modifier.weight(1f))
                    DateField("To", filters.to, { v -> vm.setFilters { it.copy(to = v) } }, Modifier.weight(1f))
                }
            }
            LoadableContent(state, vm::load) { data ->
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item {
                        Text("${data.total} rows", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (tab == ReportTab.MOVEMENTS) {
                        if (data.movements.isEmpty()) item { EmptyState("No movements in this period", icon = Icons.Default.History) }
                        items(data.movements, key = { it.id }) { m ->
                            MovementRow(m) { onOpenProduct(m.productId) }
                            HorizontalDivider()
                        }
                    } else {
                        if (data.rows.isEmpty()) item {
                            EmptyState(if (tab == ReportTab.LOW) "Nothing is running low" else "No products", icon = Icons.Default.Inventory2)
                        }
                        items(data.rows, key = { it.variantId }) { r -> InventoryRowItem(r) { onOpenProduct(r.productId) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryRowItem(r: InventoryRow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (r.variantName == r.productName) r.productName else "${r.productName} · ${r.variantName}",
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(r.sku, r.category, "min ${r.minStock}").joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.reserved != 0.0 || r.damaged != 0.0) {
                Text("On hand ${Fmt.qty(r.onHand)} · reserved ${Fmt.qty(r.reserved)} · damaged ${Fmt.qty(r.damaged)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${Fmt.qty(r.available)} ${r.unit}", fontWeight = FontWeight.SemiBold)
            StatusChip(r.status)
        }
    }
    HorizontalDivider()
}

@Composable
private fun <T> ChipMenu(label: String, selected: Boolean, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(optionLabel(o)) }, { onSelect(o); open = false }) }
        }
    }
}
