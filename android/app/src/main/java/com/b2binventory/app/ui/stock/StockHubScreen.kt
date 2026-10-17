package com.b2binventory.app.ui.stock

import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.dashboard.MovementRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val movementTypes = listOf("OPENING", "ADD", "REDUCE", "ADJUST", "TRANSFER_OUT", "TRANSFER_IN", "COUNT_ADJUST",
    "DAMAGE", "RESERVE", "RELEASE", "SALE", "SALE_CANCEL", "RETURN_IN", "PURCHASE", "PURCHASE_CANCEL", "PURCHASE_RETURN")

data class HistoryState(
    val items: List<Movement> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val page: Int = 0,
    val totalPages: Int = 0,
)

class StockHubViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()
    private val _type = MutableStateFlow<String?>(null)
    val type = _type.asStateFlow()
    private val _warehouse = MutableStateFlow<Warehouse?>(null)
    val warehouse = _warehouse.asStateFlow()
    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { (apiCall { api.warehouses() } as? ApiResult.Ok)?.let { _warehouses.value = it.value } }
    }

    fun setType(t: String?) {
        _type.value = t
        refresh()
    }

    fun setWarehouse(w: Warehouse?) {
        _warehouse.value = w
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch { fetch(0) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.page + 1 >= s.totalPages) return
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch { fetch(s.page + 1) }
    }

    private suspend fun fetch(page: Int) {
        when (val r = apiCall {
            api.movements(warehouseId = _warehouse.value?.id, types = _type.value?.let { listOf(it) }, page = page)
        }) {
            is ApiResult.Ok -> _state.update {
                HistoryState(if (page == 0) r.value.content else it.items + r.value.content, false, null, r.value.page, r.value.totalPages)
            }
            is ApiResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockHubScreen(
    onBack: () -> Unit,
    onNewOperation: (String?) -> Unit,
    onOpenProduct: (Long) -> Unit,
    onStockCounts: () -> Unit,
    onRecordSale: () -> Unit,
    onSales: () -> Unit,
) {
    val vm = appViewModel { c, _ -> StockHubViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val warehouse by vm.warehouse.collectAsStateWithLifecycle()
    val warehouses by vm.warehouses.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val listState = rememberLazyListState()
    var moreMenu by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { if (it >= state.items.size - 5) vm.loadMore() }
    }
    val canAdvanced = me != null && (me.can("STOCK_ADJUST") || me.can("STOCK_TRANSFER"))
    val canSales = me != null && listOf("SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW").any { me.can(it) }

    Scaffold(
        topBar = {
            AppTopBar("Stock activity", onBack = onBack) {
                if (me?.can("STOCK_COUNT") == true) {
                    IconButton(onClick = onStockCounts) { Icon(Icons.AutoMirrored.Filled.FactCheck, "Stock counts") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // The most common jobs, one tap each. Only actions the member is allowed to do are shown.
            val actions = buildList<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>> {
                if (me?.can("STOCK_ADD") == true) add(Triple("Add Stock", Icons.Default.AddCircleOutline) { onNewOperation("ADD") })
                if (me?.can("STOCK_REDUCE") == true) add(Triple("Reduce Stock", Icons.Default.RemoveCircleOutline) { onNewOperation("REDUCE") })
                if (me?.can("SALE_RECORD") == true) add(Triple("Record Sale", Icons.Default.PointOfSale, onRecordSale))
                if (canSales) add(Triple("Sales History", Icons.AutoMirrored.Filled.ReceiptLong, onSales))
            }
            val tiles = actions + if (canAdvanced) listOf(Triple("More options", Icons.Default.MoreHoriz) { moreMenu = true }) else emptyList()
            // Three per row keeps labels readable on small phones.
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, icon, action) -> ActionTile(label, icon, action, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (canAdvanced) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    DropdownMenu(moreMenu, { moreMenu = false }) {
                        if (me!!.can("STOCK_ADJUST")) DropdownMenuItem({ Text("Correct stock (adjust)") }, { moreMenu = false; onNewOperation("ADJUST") })
                        if (me.can("STOCK_TRANSFER")) DropdownMenuItem({ Text("Move between warehouses") }, { moreMenu = false; onNewOperation("TRANSFER") })
                        if (me.can("STOCK_COUNT")) DropdownMenuItem({ Text("Stock count") }, { moreMenu = false; onStockCounts() })
                    }
                }
            }
            Text("Stock history", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(selected = warehouse != null, onClick = { open = true },
                            label = { Text(warehouse?.name ?: "All warehouses") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                        DropdownMenu(open, { open = false }) {
                            DropdownMenuItem({ Text("All warehouses") }, { vm.setWarehouse(null); open = false })
                            warehouses.forEach { w -> DropdownMenuItem({ Text(w.name) }, { vm.setWarehouse(w); open = false }) }
                        }
                    }
                }
                item {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(selected = type != null, onClick = { open = true },
                            label = { Text(type?.let(Fmt::movement) ?: "All movement types") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                        DropdownMenu(open, { open = false }) {
                            DropdownMenuItem({ Text("All movement types") }, { vm.setType(null); open = false })
                            movementTypes.forEach { t -> DropdownMenuItem({ Text(Fmt.movement(t)) }, { vm.setType(t); open = false }) }
                        }
                    }
                }
            }
            PullToRefreshBox(isRefreshing = state.loading && state.items.isNotEmpty(), onRefresh = vm::refresh) {
                when {
                    state.loading && state.items.isEmpty() -> LoadingBox()
                    state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, vm::refresh)
                    state.items.isEmpty() -> EmptyState("No stock changes yet", "Every stock change you make is recorded here with who made it and when.", Icons.Default.History)
                    else -> LazyColumn(state = listState, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)) {
                        items(state.items, key = { it.id }) { m ->
                            MovementRow(m) { onOpenProduct(m.productId) }
                            HorizontalDivider()
                        }
                        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
            }
        }
    }
}
