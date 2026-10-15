package com.b2binventory.app.ui.analytics

import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import com.b2binventory.app.ui.common.Text
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.reports.ExportSaver
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

enum class RangePreset(val label: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), LAST_7("Last 7 days"), LAST_30("Last 30 days"),
    THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_QUARTER("This quarter"), THIS_YEAR("This year"),
    CUSTOM("Custom");

    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        TODAY -> today to today
        YESTERDAY -> today.minusDays(1) to today.minusDays(1)
        LAST_7 -> today.minusDays(6) to today
        LAST_30, CUSTOM -> today.minusDays(29) to today
        THIS_MONTH -> today.withDayOfMonth(1) to today
        LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1) to today.withDayOfMonth(1).minusDays(1)
        THIS_QUARTER -> today.with(IsoFields.DAY_OF_QUARTER, 1) to today
        THIS_YEAR -> today.with(TemporalAdjusters.firstDayOfYear()) to today
    }
}

enum class AnalyticsTab(val label: String) {
    OVERVIEW("Overview"), PRODUCTS("Product performance"), TRENDS("Sales trends"), CATEGORIES("Category analysis"),
    INSIGHTS("Insights")
}

data class AnalyticsRange(val preset: RangePreset = RangePreset.LAST_30, val from: LocalDate, val to: LocalDate)

data class ProductQuery(val q: String = "", val category: CategoryNode? = null, val sort: String = "units")

class AnalyticsViewModel(private val api: ApiService) : ViewModel() {
    private val _range = MutableStateFlow(RangePreset.LAST_30.range(LocalDate.now()).let { AnalyticsRange(from = it.first, to = it.second) })
    val range = _range.asStateFlow()
    private val _tab = MutableStateFlow(AnalyticsTab.OVERVIEW)
    val tab = _tab.asStateFlow()
    private val _query = MutableStateFlow(ProductQuery())
    val query = _query.asStateFlow()
    private val _granularity = MutableStateFlow("day")
    val granularity = _granularity.asStateFlow()

    private val _overview = MutableStateFlow(Loadable<AnalyticsOverview>())
    val overview = _overview.asStateFlow()
    private val _products = MutableStateFlow(Loadable<ProductAnalytics>())
    val products = _products.asStateFlow()
    private val _trend = MutableStateFlow(Loadable<SalesTrend>())
    val trend = _trend.asStateFlow()
    private val _categories = MutableStateFlow(Loadable<CategoryAnalytics>())
    val categories = _categories.asStateFlow()
    private val _insights = MutableStateFlow(Loadable<Insights>())
    val insights = _insights.asStateFlow()
    private val _categoryOptions = MutableStateFlow<List<CategoryNode>>(emptyList())
    val categoryOptions = _categoryOptions.asStateFlow()
    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            (apiCall { api.categories() } as? ApiResult.Ok)?.let { _categoryOptions.value = it.value.flatten() }
        }
        loadAll()
    }

    private val from get() = _range.value.from.toString()
    private val to get() = _range.value.to.toString()

    fun setPreset(p: RangePreset) {
        val (f, t) = p.range(LocalDate.now())
        setRange(AnalyticsRange(p, f, t))
    }

    fun setCustom(f: LocalDate, t: LocalDate) = setRange(AnalyticsRange(RangePreset.CUSTOM, minOf(f, t), maxOf(f, t)))

    private fun setRange(r: AnalyticsRange) {
        _range.value = r
        loadAll()
    }

    fun setTab(t: AnalyticsTab) {
        _tab.value = t
    }

    fun setQuery(transform: (ProductQuery) -> ProductQuery, debounce: Boolean = false) {
        _query.update(transform)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(350)
            loadProducts()
        }
    }

    fun setGranularity(g: String) {
        _granularity.value = g
        loadTrend()
    }

    /** Refreshes every section so all cards, charts and tables show the same period. */
    fun loadAll() {
        _overview.load(viewModelScope) { api.analyticsOverview(from, to) }
        loadProducts()
        loadTrend()
        _categories.load(viewModelScope) { api.analyticsCategories(from, to) }
        _insights.load(viewModelScope) { api.analyticsInsights(from, to) }
    }

    private fun loadProducts() {
        val q = _query.value
        _products.load(viewModelScope) { api.analyticsProducts(from, to, q.category?.id, q.q.blankToNull(), q.sort) }
    }

    private fun loadTrend() = _trend.load(viewModelScope) { api.analyticsTrend(from, to, _granularity.value) }

    fun export(context: Context, format: String, onSaved: (Uri, String) -> Unit) {
        val q = _query.value
        _exporting.value = true
        viewModelScope.launch {
            val result = apiCall {
                val response = api.exportAnalytics(format, from, to, q.category?.id, q.sort)
                if (!response.isSuccessful) throw HttpException(response)
                val body = response.body() ?: error("Empty file")
                val name = response.headers()["Content-Disposition"]?.substringAfter("filename=", "")?.trim('"')
                    ?.takeIf { it.isNotBlank() } ?: "product-analytics.$format"
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

private val rangeFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    header: @Composable () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onAddStock: (Long) -> Unit,
    onRecordSale: () -> Unit,
) {
    val vm = appViewModel { c, _ -> AnalyticsViewModel(c.api) }
    val range by vm.range.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var exportMenu by remember { mutableStateOf(false) }
    var pickCustom by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }

    Scaffold(
        topBar = {
            AppTopBar("Analytics") {
                if (tab == AnalyticsTab.PRODUCTS && me?.can("REPORT_EXPORT") == true) {
                    Box {
                        if (exporting) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                        else IconButton(onClick = { exportMenu = true }) { Icon(Icons.Default.Download, "Export product ranking") }
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
                IconButton(onClick = vm::loadAll) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            header()
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RangePreset.entries) { p ->
                    FilterChip(
                        selected = range.preset == p,
                        onClick = { if (p == RangePreset.CUSTOM) pickCustom = true else vm.setPreset(p) },
                        label = { Text(p.label) },
                        leadingIcon = if (p == RangePreset.CUSTOM) { { Icon(Icons.Default.DateRange, null, Modifier.size(18.dp)) } } else null,
                    )
                }
            }
            val days = overview.data?.period?.days
            Text(
                "${range.from.format(rangeFmt)} – ${range.to.format(rangeFmt)}" +
                    (days?.let { " · compared with the previous $it ${if (it == 1) "day" else "days"}" } ?: ""),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp) {
                AnalyticsTab.entries.forEach { t -> Tab(tab == t, { vm.setTab(t) }, text = { Text(t.label) }) }
            }
            when (tab) {
                AnalyticsTab.OVERVIEW -> OverviewTab(vm, onOpenProduct, onRecordSale)
                AnalyticsTab.PRODUCTS -> ProductsTab(vm, onOpenProduct)
                AnalyticsTab.TRENDS -> TrendsTab(vm)
                AnalyticsTab.CATEGORIES -> CategoriesTab(vm)
                AnalyticsTab.INSIGHTS -> InsightsTab(vm, onOpenProduct, onAddStock)
            }
        }
    }

    if (pickCustom) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = range.from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = range.to.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickCustom = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis!!.toDate()
                        val end = state.selectedEndDateMillis?.toDate() ?: start
                        vm.setCustom(start, end)
                        pickCustom = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickCustom = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, Modifier.weight(1f), title = { Text("Choose dates", Modifier.padding(16.dp)) })
        }
    }
}

private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

// ------------------------------------------------------------------ Overview

@Composable
private fun OverviewTab(vm: AnalyticsViewModel, onOpenProduct: (Long) -> Unit, onRecordSale: () -> Unit) {
    val state by vm.overview.collectAsStateWithLifecycle()
    val me = LocalMe.current
    LoadableContent(state, vm::loadAll) { o ->
        val cur = o.period.currencyCode
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (o.sales.value == 0.0) {
                item {
                    EmptyState("No sales recorded in this period",
                        "Sales you record from the Stock tab appear here. Stock reductions are not counted as sales.",
                        icon = Icons.Default.PointOfSale,
                        action = if (me?.can("SALE_RECORD") == true) { { Button(onClick = onRecordSale) { Text("Record a sale") } } } else null)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Net sales", Fmt.money(o.revenue.value, cur), o.revenue.growthPercent, Icons.Default.Payments, Modifier.weight(1f))
                    KpiCard("Units sold", Fmt.qty(o.units.value), o.units.growthPercent, Icons.Default.ShoppingBag, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val gp = o.grossProfit
                    KpiCard("Gross profit", gp?.let { Fmt.money(it.value, cur) } ?: "Unavailable", gp?.growthPercent,
                        Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f),
                        note = if (gp == null) "Some sold products have no purchase price" else null)
                    KpiCard("Number of sales", Fmt.qty(o.sales.value), o.sales.growthPercent, Icons.AutoMirrored.Filled.ReceiptLong, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Average per day", "${Fmt.qty(o.averageDailyUnits)} units",
                        null, Icons.Default.CalendarToday, Modifier.weight(1f),
                        note = Fmt.money(o.averageDailyRevenue, cur) + " a day")
                    KpiCard("Stock value (cost)", Fmt.money(o.inventory.valueAtCost, cur), null, Icons.Default.Inventory2,
                        Modifier.weight(1f), note = "Potential profit " + Fmt.money(o.inventory.potentialProfit, cur))
                }
            }
            item {
                val f = o.financials
                SectionCard("Sales and profit") {
                    com.b2binventory.app.ui.sales.TotalRow("Gross sales", Fmt.money(f.grossSales, cur))
                    com.b2binventory.app.ui.sales.TotalRow("Discounts", "− " + Fmt.money(f.discounts, cur))
                    com.b2binventory.app.ui.sales.TotalRow("Returns", "− " + Fmt.money(f.returns, cur))
                    com.b2binventory.app.ui.sales.TotalRow("Net sales revenue", Fmt.money(f.netSales, cur), bold = true)
                    com.b2binventory.app.ui.sales.TotalRow("Cost of goods sold", f.costOfGoodsSold?.let { "− " + Fmt.money(it, cur) } ?: "Unavailable")
                    com.b2binventory.app.ui.sales.TotalRow("Gross profit", f.grossProfit?.let { Fmt.money(it, cur) } ?: "Unavailable", bold = true,
                        color = if ((f.grossProfit ?: 0.0) >= 0) StatusColors.ok else StatusColors.out)
                    f.marginPercent?.let { com.b2binventory.app.ui.sales.TotalRow("Gross margin", "${Fmt.qty(it)}%") }
                    if (f.taxCollected > 0) com.b2binventory.app.ui.sales.TotalRow("Tax collected (not revenue)", Fmt.money(f.taxCollected, cur))
                    if (!f.profitAvailable) {
                        Text("Profit is unavailable because ${f.linesWithoutCost} sold item(s) had no purchase price. Add purchase prices or record purchases to see profit.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SectionCard("Purchases") {
                    com.b2binventory.app.ui.sales.TotalRow("Purchases received", o.purchases.purchases.toString())
                    com.b2binventory.app.ui.sales.TotalRow("Units received", Fmt.qty(o.purchases.unitsReceived))
                    com.b2binventory.app.ui.sales.TotalRow("Total spent", Fmt.money(o.purchases.totalSpent, cur), bold = true)
                    com.b2binventory.app.ui.sales.TotalRow("Previous period", Fmt.money(o.purchases.previousTotalSpent, cur))
                }
            }
            item {
                SectionCard("Products") {
                    ProductRefRow("Best-selling product", o.bestSeller, Icons.Default.EmojiEvents, StatusColors.ok, onOpenProduct)
                    ProductRefRow("Lowest-selling product", o.lowestSeller, Icons.AutoMirrored.Filled.TrendingDown, StatusColors.low, onOpenProduct)
                    LabelValue("Products sold", "${o.productsSold} of ${o.activeProducts}")
                    LabelValue("Products with no sales", o.productsWithNoSales.toString())
                }
            }
            item {
                SectionCard("Sales growth") {
                    ComparisonBars("Net sales", o.revenue.value, o.revenue.previous ?: 0.0, { Fmt.money(it, cur) })
                    o.grossProfit?.let { gp -> ComparisonBars("Gross profit", gp.value, gp.previous ?: 0.0, { Fmt.money(it, cur) }) }
                    ComparisonBars("Units sold", o.units.value, o.units.previous ?: 0.0, { Fmt.qty(it) })
                    o.period.previousFrom?.let {
                        Text("Previous period: ${Fmt.date(it)} – ${Fmt.date(o.period.previousTo)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("Only recorded sales count. Cancelled sales and returned items are excluded, tax is not revenue, and stock reductions are never treated as sales. Profit uses the cost saved with each sale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, growth: Double?, icon: ImageVector, modifier: Modifier = Modifier,
                    note: String? = null) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                GrowthText(growth)
            }
        }
    }
}

@Composable
private fun GrowthText(growth: Double?) {
    val (icon, color) = when {
        growth == null -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
        growth > 0 -> Icons.AutoMirrored.Filled.TrendingUp to StatusColors.ok
        growth < 0 -> Icons.AutoMirrored.Filled.TrendingDown to StatusColors.out
        else -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(if (growth == null) "Insufficient data" else Fmt.growth(growth), style = MaterialTheme.typography.labelSmall,
            color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProductRefRow(label: String, p: ProductRef?, icon: ImageVector, color: Color, onOpen: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(p?.name ?: "—", fontWeight = FontWeight.Medium) },
        overlineContent = { Text(label) },
        supportingContent = p?.let { { Text("${Fmt.qty(it.units)} ${it.unit} sold") } },
        leadingContent = { Icon(icon, null, tint = color) },
        modifier = if (p != null) Modifier.clickableRow { onOpen(p.productId) } else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ------------------------------------------------------------------ Products

@Composable
private fun ProductsTab(vm: AnalyticsViewModel, onOpenProduct: (Long) -> Unit) {
    val state by vm.products.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val categories by vm.categoryOptions.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val cur = overview.data?.period?.currencyCode ?: "INR"
    Column {
        SearchField(query.q, { v -> vm.setQuery({ it.copy(q = v) }, debounce = true) }, "Search product or SKU",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ChipMenu(query.category?.name ?: "All categories", query.category != null,
                    listOf<CategoryNode?>(null) + categories, { it?.path ?: "All categories" }) { c -> vm.setQuery({ it.copy(category = c) }) }
            }
            items(listOf("units" to "Units sold", "revenue" to "Revenue", "profit" to "Profit", "growth" to "Growth", "name" to "Name")) { (key, label) ->
                FilterChip(query.sort == key, { vm.setQuery({ it.copy(sort = key) }) }, label = { Text(label) },
                    leadingIcon = if (query.sort == key) { { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) } } else null)
            }
        }
        LoadableContent(state, { vm.setQuery({ it }) }) { data ->
            val list = data.products
            val sellers = list.filter { it.unitsSold > 0 }.sortedByDescending { it.unitsSold }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (list.isEmpty()) {
                    item { EmptyState("No products match", "Try a different search or category.", icon = Icons.Default.SearchOff) }
                    return@LazyColumn
                }
                item {
                    SectionCard("Top 10 best-selling products") {
                        HorizontalBarChart(sellers.take(10).map { BarItem(it.name, it.unitsSold, "${Fmt.qty(it.unitsSold)} ${it.unit}", it.productId) },
                            emptyText = "No sales in this period", onClick = { b -> b.key?.let(onOpenProduct) })
                    }
                }
                if (sellers.size > 1) {
                    item {
                        SectionCard("Lowest-selling products") {
                            HorizontalBarChart(sellers.takeLast(minOf(5, sellers.size)).reversed()
                                .map { BarItem(it.name, it.unitsSold, "${Fmt.qty(it.unitsSold)} ${it.unit}", it.productId) },
                                color = StatusColors.low, onClick = { b -> b.key?.let(onOpenProduct) })
                            Text("Products with no sales are listed in the table below.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    SectionCard("Revenue by product") {
                        HorizontalBarChart(list.filter { it.revenue > 0 }.sortedByDescending { it.revenue }.take(10)
                            .map { BarItem(it.name, it.revenue, Fmt.money(it.revenue, cur), it.productId) },
                            color = MaterialTheme.colorScheme.secondary, onClick = { b -> b.key?.let(onOpenProduct) })
                    }
                }
                val withProfit = list.filter { (it.grossProfit ?: 0.0) != 0.0 }.sortedByDescending { it.grossProfit }
                if (withProfit.isNotEmpty()) {
                    item {
                        SectionCard("Gross profit by product") {
                            HorizontalBarChart(withProfit.take(10).map { BarItem(it.name, (it.grossProfit ?: 0.0).coerceAtLeast(0.0),
                                Fmt.money(it.grossProfit ?: 0.0, cur), it.productId) }, color = StatusColors.ok,
                                onClick = { b -> b.key?.let(onOpenProduct) })
                        }
                    }
                }
                if (sellers.isNotEmpty()) {
                    item {
                        SectionCard("Stock vs sales") {
                            Text("How long current stock lasts at the recent selling rate.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            sellers.take(8).forEach { p -> StockVsSalesRow(p) { onOpenProduct(p.productId) } }
                        }
                    }
                }
                item {
                    Text("${list.size} products · ranked by units sold", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list, key = { it.productId }) { p -> RankingRow(p, cur) { onOpenProduct(p.productId) } }
            }
        }
    }
}

@Composable
private fun StockVsSalesRow(p: ProductPerformance, onClick: () -> Unit) {
    val cover = if (p.averageDailySales > 0) p.currentStock.coerceAtLeast(0.0) / p.averageDailySales else null
    Row(Modifier.fillMaxWidth().clickableRow(onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("Stock ${Fmt.qty(p.currentStock)} ${p.unit} · sells ${Fmt.qty(p.averageDailySales)}/day",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val text = when {
            p.currentStock <= 0 -> "Out of stock"
            cover == null -> "—"
            cover < 1 -> "Less than a day"
            else -> "~${cover.toInt()} days left"
        }
        TagChip(text, if (cover != null && cover < 7) StatusColors.out else StatusColors.ok)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankingRow(p: ProductPerformance, currency: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${p.rank}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(p.sku, p.categoryName).joinToString(" · ").ifEmpty { "No SKU" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(p.stockStatus)
            }
            Row {
                Metric("Units sold", "${Fmt.qty(p.unitsSold)} ${p.unit}", Modifier.weight(1f))
                Metric("Revenue", Fmt.money(p.revenue, currency), Modifier.weight(1f))
                Metric("Stock", "${Fmt.qty(p.currentStock)} ${p.unit}", Modifier.weight(1f))
            }
            Row {
                Metric("Avg per day", Fmt.qty(p.averageDailySales), Modifier.weight(1f))
                Metric("Gross profit", p.grossProfit?.let { Fmt.money(it, currency) + (p.marginPercent?.let { m -> " (${Fmt.qty(m)}%)" } ?: "") }
                    ?: if (p.unitsSold > 0) "Unavailable" else "—", Modifier.weight(2f))
            }
            Row {
                Metric("Performance", performanceLabel(p), Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.labels.forEach { l ->
                    when (l) {
                        "TOP_SELLER" -> TagChip("Top seller", StatusColors.ok)
                        "FAST_MOVING" -> TagChip("Fast moving", MaterialTheme.colorScheme.primary)
                        "SLOW_MOVING" -> TagChip("Slow moving", StatusColors.low)
                        "NO_SALES" -> TagChip("No sales", MaterialTheme.colorScheme.outline)
                    }
                }
                if (p.archived) TagChip("Archived", MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun performanceLabel(p: ProductPerformance): String = when (p.performance) {
    "GROWING" -> "Growing (${Fmt.qty(p.growthPercent ?: 0.0)}%)"
    "DECLINING" -> "Declining (${Fmt.qty(p.growthPercent ?: 0.0)}%)"
    "STABLE" -> "Stable"
    "NEW" -> "New sales this period"
    else -> "No sales data"
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ Trends

@Composable
private fun TrendsTab(vm: AnalyticsViewModel) {
    val state by vm.trend.collectAsStateWithLifecycle()
    val granularity by vm.granularity.collectAsStateWithLifecycle()
    Column {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("day" to "Daily", "week" to "Weekly", "month" to "Monthly").forEach { (key, label) ->
                FilterChip(granularity == key, { vm.setGranularity(key) }, label = { Text(label) })
            }
        }
        LoadableContent(state, { vm.setGranularity(granularity) }) { t ->
            val cur = t.period.currencyCode
            val label: (TrendPoint) -> String = { p ->
                when (t.granularity) {
                    "month" -> runCatching { LocalDate.parse(p.start).format(DateTimeFormatter.ofPattern("MMM yyyy")) }.getOrDefault(p.start)
                    "week" -> "Week of " + Fmt.shortDate(p.start)
                    else -> Fmt.shortDate(p.start)
                }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionCard("Units sold over time") {
                        LineChart(t.points.map { label(it) to it.units }, { "${Fmt.qty(it)} units" })
                    }
                }
                item {
                    SectionCard("Revenue over time") {
                        LineChart(t.points.map { label(it) to it.revenue }, { Fmt.money(it, cur) },
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
                item {
                    SectionCard("Gross profit over time") {
                        if (t.points.any { it.grossProfit == null }) {
                            Text("Unavailable for some periods: items were sold without a purchase price.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LineChart(t.points.map { label(it) to (it.grossProfit ?: 0.0) }, { Fmt.money(it, cur) }, color = StatusColors.ok)
                        Text("Tap the chart to see a value.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val withSales = t.points.filter { it.sales > 0 }.reversed()
                if (withSales.isNotEmpty()) {
                    item { Text("Periods with sales", style = MaterialTheme.typography.titleSmall) }
                    items(withSales, key = { it.start }) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(label(p), Modifier.weight(1f))
                            Text("${p.sales} sales · ${Fmt.qty(p.units)} units · ${Fmt.money(p.revenue, cur)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Categories

@Composable
private fun CategoriesTab(vm: AnalyticsViewModel) {
    val state by vm.categories.collectAsStateWithLifecycle()
    LoadableContent(state, vm::loadAll) { c ->
        val cur = c.period.currencyCode
        val current = c.categories.filter { it.units > 0 }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current.isEmpty()) {
                item { EmptyState("No category sales in this period", icon = Icons.Default.Category) }
                return@LazyColumn
            }
            item {
                SectionCard("Units sold by category") {
                    HorizontalBarChart(current.map { BarItem(it.categoryName, it.units, Fmt.qty(it.units)) })
                }
            }
            item {
                SectionCard("Revenue by category") {
                    HorizontalBarChart(current.sortedByDescending { it.revenue }.map { BarItem(it.categoryName, it.revenue, Fmt.money(it.revenue, cur)) },
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            items(c.categories, key = { it.categoryId ?: -1L }) { cat ->
                ListItem(
                    headlineContent = { Text(cat.categoryName, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text("${Fmt.qty(cat.units)} units · ${Fmt.money(cat.revenue, cur)}" +
                            (cat.grossProfit?.let { " · profit ${Fmt.money(it, cur)}" } ?: "") + " · ${cat.productsSold} products sold")
                    },
                    trailingContent = { GrowthText(cat.growthPercent) },
                )
                HorizontalDivider()
            }
        }
    }
}

// ------------------------------------------------------------------ Insights

@Composable
private fun InsightsTab(vm: AnalyticsViewModel, onOpenProduct: (Long) -> Unit, onAddStock: (Long) -> Unit) {
    val state by vm.insights.collectAsStateWithLifecycle()
    val me = LocalMe.current
    LoadableContent(state, vm::loadAll) { data ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(data.insights) { i -> InsightCard(i, me?.can("STOCK_ADD") == true, onOpenProduct, onAddStock) }
            item {
                Text("Insights are based only on recorded sales, purchases and current stock. Slow sales do not by themselves mean a product is unprofitable.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InsightCard(i: Insight, canAddStock: Boolean, onOpenProduct: (Long) -> Unit, onAddStock: (Long) -> Unit) {
    val (icon, color) = when (i.severity) {
        "GOOD" -> Icons.Default.ThumbUp to StatusColors.ok
        "WARNING" -> Icons.Default.WarningAmber to StatusColors.low
        else -> Icons.Default.Lightbulb to MaterialTheme.colorScheme.primary
    }
    AppCard {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(i.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(i.message, style = MaterialTheme.typography.bodyMedium)
                val pid = i.productId
                if (pid != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (i.action == "ADD_STOCK" && canAddStock) {
                            FilledTonalButton(onClick = { onAddStock(pid) }) { Text("Add stock") }
                        }
                        OutlinedButton(onClick = { onOpenProduct(pid) }) { Text("View product") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ shared

@Composable
fun <T> ChipMenu(label: String, selected: Boolean, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(optionLabel(o)) }, { onSelect(o); open = false }) }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
