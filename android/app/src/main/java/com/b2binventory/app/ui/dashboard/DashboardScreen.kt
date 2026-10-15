package com.b2binventory.app.ui.dashboard

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.ApiResult
import com.b2binventory.app.data.remote.ApiService
import com.b2binventory.app.data.remote.Dashboard
import com.b2binventory.app.data.remote.Movement
import com.b2binventory.app.data.remote.ProductSummary
import com.b2binventory.app.data.remote.SalesHighlights
import com.b2binventory.app.data.remote.apiCall
import com.b2binventory.app.data.repository.AuthRepository
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.theme.Spacing
import com.b2binventory.app.ui.theme.StatusColors
import com.b2binventory.app.ui.theme.appColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val api: ApiService, private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<Dashboard>())
    val state = _state.asStateFlow()

    /** Newest products, from the product list sorted by creation date. */
    private val _recentlyAdded = MutableStateFlow<List<ProductSummary>>(emptyList())
    val recentlyAdded = _recentlyAdded.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    fun load() {
        _state.load(viewModelScope) { api.dashboard() }
        viewModelScope.launch {
            (apiCall { api.products(sort = "created", size = 5) } as? ApiResult.Ok)?.let { _recentlyAdded.value = it.value.content }
        }
    }

    fun switchBusiness(businessId: Long) {
        viewModelScope.launch {
            when (val r = auth.switchBusiness(businessId)) {
                is ApiResult.Ok -> load()
                is ApiResult.Err -> _messages.emit(r.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenProducts: (String?) -> Unit,
    onOpenProduct: (Long) -> Unit,
    onAddProduct: () -> Unit,
    onStockOperation: (String) -> Unit,
    onRecordSale: () -> Unit,
    onRecordPurchase: () -> Unit,
    onReports: () -> Unit,
    onStockActivity: () -> Unit,
    onNotifications: () -> Unit,
) {
    val vm = appViewModel { c, _ -> DashboardViewModel(c.api, c.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val wide = rememberWidthClass() != WidthClass.COMPACT
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BusinessSwitcher(onSwitch = vm::switchBusiness) },
                actions = {
                    val unread = state.data?.unreadNotifications ?: 0
                    IconButton(onClick = onNotifications) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                            Icon(Icons.Default.Notifications, tr("Notifications"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = vm::load,
            modifier = Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { d ->
                ResponsiveContent {
                    LazyColumn(
                        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        item {
                            Text("Hello, ${me?.fullName?.substringBefore(' ') ?: ""}", style = MaterialTheme.typography.headlineSmall)
                            Text("Here is how your stock looks today.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item { KpiGrid(d, wide, onOpenProducts) }
                        item {
                            QuickActions(wide, onAddProduct, onStockOperation, onRecordSale, onRecordPurchase,
                                { onOpenProducts(null) }, onReports, onStockActivity)
                        }
                        if (d.activeProducts == 0L) {
                            item { GetStarted(onAddProduct) }
                        }
                        d.sales?.let { h -> item { SalesHighlightsCard(h, wide, onReports, onOpenProduct) } }

                        val alerts = d.outOfStockItems + d.lowStockItems
                        if (alerts.isNotEmpty()) {
                            item {
                                SectionCard("Needs attention", actionLabel = "View all", onAction = { onOpenProducts("LOW_STOCK") }) {
                                    alerts.take(6).forEach { a ->
                                        ProductLine(a.productName, "${Fmt.qty(a.available)} ${a.unit} available · min ${a.minStock}",
                                            a.status) { onOpenProduct(a.productId) }
                                    }
                                }
                            }
                        }
                        if (d.categories.any { it.quantity > 0 }) {
                            item {
                                SectionCard("Stock by category") {
                                    HorizontalBarChart(
                                        d.categories.sortedByDescending { it.quantity }.take(6).map {
                                            BarItem(it.categoryName, it.quantity, "${Fmt.qty(it.quantity)} · ${it.productCount} products", it.categoryId)
                                        },
                                    )
                                }
                            }
                        }
                        if (d.mostStocked.isNotEmpty()) {
                            item {
                                SectionCard("Most-stocked products") {
                                    d.mostStocked.forEach { p ->
                                        ProductLine(p.productName, "${Fmt.qty(p.available)} ${p.unit} available", p.status) {
                                            onOpenProduct(p.productId)
                                        }
                                    }
                                }
                            }
                        }
                        if (recentlyAdded.isNotEmpty()) {
                            item {
                                SectionCard("Recently added products", actionLabel = "View all", onAction = { onOpenProducts(null) }) {
                                    recentlyAdded.forEach { p ->
                                        ProductLine(p.name, "${Fmt.qty(p.stock.available)} ${p.unitCode} available" +
                                            (p.categoryName?.let { " · $it" } ?: ""), p.status) { onOpenProduct(p.id) }
                                    }
                                }
                            }
                        }
                        if (d.recentMovements.isNotEmpty()) {
                            item {
                                SectionCard("Recent stock activity", actionLabel = "See all", onAction = onStockActivity) {
                                    d.recentMovements.take(6).forEach { m -> MovementRow(m) { onOpenProduct(m.productId) } }
                                }
                            }
                        }
                        if (d.warehouses.size > 1) {
                            item {
                                SectionCard("By warehouse") {
                                    d.warehouses.forEach { w ->
                                        InfoRow(w.warehouseName, "${w.productCount} products · ${Fmt.qty(w.quantity)}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Business name in the top bar; opens a list of the member's businesses when there is more than one. */
@Composable
private fun BusinessSwitcher(onSwitch: (Long) -> Unit) {
    val me = LocalMe.current
    var open by remember { mutableStateOf(false) }
    val others = me?.memberships.orEmpty()
    Box {
        Row(
            Modifier.clip(MaterialTheme.shapes.small)
                .then(if (others.size > 1) Modifier.clickable { open = true } else Modifier)
                .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(me?.businessName ?: "", 36.dp)
            Spacer(Modifier.width(Spacing.s))
            Column(Modifier.weight(1f, fill = false)) {
                androidx.compose.material3.Text(me?.businessName ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(Fmt.role(me?.role ?: ""), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (others.size > 1) Icon(Icons.Default.ExpandMore, tr("Switch"))
        }
        DropdownMenu(open, { open = false }) {
            Text("Your businesses", Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            others.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Column {
                            androidx.compose.material3.Text(m.businessName, fontWeight = FontWeight.Medium)
                            Text(listOfNotNull(m.businessType, Fmt.role(m.role)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { Avatar(m.businessName, 32.dp) },
                    trailingIcon = { if (m.businessId == me?.businessId) Icon(Icons.Default.Check, null) },
                    onClick = {
                        open = false
                        if (m.businessId != me?.businessId) onSwitch(m.businessId)
                    },
                )
            }
        }
    }
}

@Composable
private fun KpiGrid(d: Dashboard, wide: Boolean, onOpenProducts: (String?) -> Unit) {
    val c = MaterialTheme.appColors
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { m -> StatTile("Total products", d.activeProducts.toString(), Icons.Default.Inventory2, MaterialTheme.colorScheme.primary, m) { onOpenProducts(null) } },
        { m -> StatTile("Total stock quantity", Fmt.qty(d.totalQuantity), Icons.Default.Layers, MaterialTheme.colorScheme.tertiary, m) { onOpenProducts(null) } },
        { m -> StatTile("Low stock", d.lowStockCount.toString(), Icons.Default.WarningAmber, c.warning, m) { onOpenProducts("LOW_STOCK") } },
        { m -> StatTile("Out of stock", d.outOfStockCount.toString(), Icons.Default.RemoveShoppingCart, c.danger, m) { onOpenProducts("OUT_OF_STOCK") } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        tiles.chunked(if (wide) 4 else 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) { row.forEach { it(Modifier.weight(1f)) } }
        }
    }
}

@Composable
private fun GetStarted(onAddProduct: () -> Unit) {
    val me = LocalMe.current
    SectionCard("Get started") {
        listOf(
            Icons.Default.AddBox to "1. Add your products with their opening stock.",
            Icons.Default.SwapVert to "2. Add or reduce stock whenever goods come in or go out.",
            Icons.Default.Insights to "3. Record sales to see your best and slowest sellers.",
        ).forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.xs)) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, 32.dp)
                Spacer(Modifier.width(Spacing.m))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (me?.can("PRODUCT_CREATE") == true) {
            Spacer(Modifier.height(Spacing.xs))
            PrimaryButton("Add your first product", onAddProduct, icon = Icons.Default.Add)
        }
    }
}

/** The most common tasks, filtered by what the member is allowed to do. */
@Composable
private fun QuickActions(
    wide: Boolean,
    onAddProduct: () -> Unit,
    onStockOperation: (String) -> Unit,
    onRecordSale: () -> Unit,
    onRecordPurchase: () -> Unit,
    onInventory: () -> Unit,
    onReports: () -> Unit,
    onStockActivity: () -> Unit,
) {
    val me = LocalMe.current
    val c = MaterialTheme.appColors
    val primary = MaterialTheme.colorScheme.primary
    data class Action(val label: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)
    val actions = buildList {
        if (me?.can("PRODUCT_CREATE") == true) add(Action("Add Product", Icons.Default.AddBox, primary, onAddProduct))
        if (me?.can("STOCK_ADD") == true) add(Action("Add Stock", Icons.Default.AddCircleOutline, c.success) { onStockOperation("ADD") })
        if (me?.can("STOCK_REDUCE") == true) add(Action("Reduce Stock", Icons.Default.RemoveCircleOutline, c.warning) { onStockOperation("REDUCE") })
        add(Action("View Inventory", Icons.Default.Inventory2, primary, onInventory))
        if (me?.can("REPORT_VIEW") == true || me?.can("ANALYTICS_VIEW") == true) add(Action("View Reports", Icons.Default.Insights, primary, onReports))
        if (me?.can("SALE_RECORD") == true) add(Action("Record Sale", Icons.Default.PointOfSale, MaterialTheme.colorScheme.tertiary, onRecordSale))
        if (me?.can("PURCHASE_MANAGE") == true) add(Action("Record Purchase", Icons.Default.LocalShipping, MaterialTheme.colorScheme.tertiary, onRecordPurchase))
        add(Action("Stock Activity", Icons.Default.History, primary, onStockActivity))
    }
    val perRow = if (wide) 4 else 3
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader("Quick actions", modifier = Modifier)
        actions.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { a -> ActionTile(a.label, a.icon, a.onClick, Modifier.weight(1f), tint = a.tint) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Sales, profit and stock value for members who can view analytics. */
@Composable
private fun SalesHighlightsCard(h: SalesHighlights, wide: Boolean, onAnalytics: () -> Unit, onOpenProduct: (Long) -> Unit) {
    val cur = h.today.currencyCode
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { m -> StatTile("Today's sales", Fmt.money(h.today.revenue, cur), Icons.Default.PointOfSale, MaterialTheme.colorScheme.primary, m, onClick = onAnalytics) },
        { m -> StatTile("This month's sales", Fmt.money(h.month.revenue, cur), Icons.Default.CalendarMonth, MaterialTheme.colorScheme.primary, m, onClick = onAnalytics) },
        { m -> StatTile("Units sold this month", Fmt.qty(h.month.units), Icons.Default.ShoppingBag, MaterialTheme.colorScheme.tertiary, m, onClick = onAnalytics) },
        { m -> StatTile("Gross profit this month", h.month.grossProfit?.let { Fmt.money(it, cur) } ?: "Unavailable",
            Icons.AutoMirrored.Filled.TrendingUp, StatusColors.ok, m, onClick = onAnalytics) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SectionHeader("Sales and profit", "Analytics", onAnalytics, Modifier)
        tiles.chunked(if (wide) 4 else 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) { row.forEach { it(Modifier.weight(1f)) } }
        }
        SectionCard("This month") {
            h.monthBestSeller?.let { b ->
                Row(Modifier.fillMaxWidth().clickable { onOpenProduct(b.productId) }.padding(vertical = Spacing.xs)) {
                    Text("Best seller", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${b.name} · ${Fmt.qty(b.units)} ${b.unit}", fontWeight = FontWeight.Medium)
                }
            } ?: Text("No sales yet this month. Use Record Sale to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            h.monthLowestSeller?.let { l ->
                Row(Modifier.fillMaxWidth().clickable { onOpenProduct(l.productId) }.padding(vertical = Spacing.xs)) {
                    Text("Lowest seller", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${l.name} · ${Fmt.qty(l.units)} ${l.unit}", fontWeight = FontWeight.Medium)
                }
            }
            LabelValue("Products with no sales", h.monthProductsWithNoSales.toString())
            LabelValue("Stock value (at cost)", Fmt.money(h.inventory.valueAtCost, cur) +
                if (h.inventory.productsWithoutCost > 0) " (${h.inventory.productsWithoutCost} without cost)" else "")
            LabelValue("Profit if remaining stock sells", Fmt.money(h.inventory.potentialProfit, cur))
        }
    }
}

/** One product in a dashboard list: name, detail and status. */
@Composable
private fun ProductLine(name: String, detail: String, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(stockStatusIcon(status), statusColor(status), 36.dp)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Spacing.s))
        StatusChip(status)
    }
}

/** One stock movement: direction icon, product, what happened, who and when, and the quantity change. */
@Composable
fun MovementRow(m: Movement, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.appColors
    val (icon, tint) = when {
        m.type in setOf("ADJUST", "COUNT_ADJUST") -> Icons.Default.Tune to MaterialTheme.colorScheme.primary
        m.type in setOf("RESERVE", "RELEASE") -> Icons.Default.Bookmark to MaterialTheme.colorScheme.tertiary
        m.quantity > 0 -> Icons.Default.SouthWest to c.success
        m.quantity < 0 -> Icons.Default.NorthEast to c.danger
        else -> Icons.Default.SwapHoriz to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, 36.dp)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            val name = if (m.variantName == m.productName) m.productName else "${m.productName} · ${m.variantName}"
            androidx.compose.material3.Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val detail = buildList {
                add(tr(Fmt.movement(m.type)))
                add(m.warehouseName)
                m.lotNumber?.let { add(tr("lot $it")) }
                m.reason?.takeIf { it.isNotBlank() && it != "Opening stock" }?.let { add(tr(it)) }
            }.joinToString(" · ")
            androidx.compose.material3.Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            androidx.compose.material3.Text("${m.actorName} · ${Fmt.dateTime(m.createdAt)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Spacing.s))
        Column(horizontalAlignment = Alignment.End) {
            Pill("${Fmt.signedQty(m.quantity)} ${m.unitCode}", movementColor(m.quantity))
            Spacer(Modifier.height(2.dp))
            androidx.compose.material3.Text("${Fmt.qty(m.quantityBefore)} → ${Fmt.qty(m.quantityAfter)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
