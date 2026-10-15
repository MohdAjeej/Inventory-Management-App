package com.b2binventory.app.ui.dashboard

import androidx.compose.material.icons.automirrored.filled.TrendingUp
import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.ApiService
import com.b2binventory.app.data.remote.Dashboard
import com.b2binventory.app.data.remote.Movement
import com.b2binventory.app.data.remote.SalesHighlights
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<Dashboard>())
    val state = _state.asStateFlow()

    fun load() = _state.load(viewModelScope) { api.dashboard() }
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
    val vm = appViewModel { c, _ -> DashboardViewModel(c.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val me = LocalMe.current
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(me?.businessName ?: "Inventory", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Hello, ${me?.fullName?.substringBefore(' ') ?: ""}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                val unread = state.data?.unreadNotifications ?: 0
                IconButton(onClick = onNotifications) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                        Icon(Icons.Default.Notifications, "Notifications")
                    }
                }
            },
        )
    }) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = vm::load,
            modifier = Modifier.padding(padding)) {
            LoadableContent(state, vm::load) { d ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile("Total products", d.activeProducts.toString(), Icons.Default.Inventory2,
                                MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { onOpenProducts(null) }
                            StatTile("Total stock items", Fmt.qty(d.totalQuantity), Icons.Default.Layers,
                                MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) { onOpenProducts(null) }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile("Low stock", d.lowStockCount.toString(), Icons.Default.Warning, StatusColors.low,
                                Modifier.weight(1f)) { onOpenProducts("LOW_STOCK") }
                            StatTile("Out of stock", d.outOfStockCount.toString(), Icons.Default.RemoveShoppingCart,
                                StatusColors.out, Modifier.weight(1f)) { onOpenProducts("OUT_OF_STOCK") }
                        }
                    }
                    item {
                        QuickActions(onAddProduct, onStockOperation, onRecordSale, onRecordPurchase, { onOpenProducts(null) },
                            onReports, onStockActivity)
                    }
                    d.sales?.let { h -> item { SalesHighlightsCard(h, onReports, onOpenProduct) } }
                    if (d.activeProducts == 0L) {
                        item {
                            SectionCard("Get started") {
                                Text("1. Add your products with their opening stock.", style = MaterialTheme.typography.bodyMedium)
                                Text("2. Add or reduce stock whenever goods come in or go out.", style = MaterialTheme.typography.bodyMedium)
                                Text("3. Record sales to see your best and slowest sellers.", style = MaterialTheme.typography.bodyMedium)
                                if (me?.can("PRODUCT_CREATE") == true) {
                                    Button(onClick = onAddProduct, modifier = Modifier.fillMaxWidth()) { Text("Add your first product") }
                                }
                            }
                        }
                    }
                    val alerts = d.outOfStockItems + d.lowStockItems
                    if (alerts.isNotEmpty()) {
                        item {
                            SectionCard("Needs attention") {
                                alerts.take(8).forEach { a ->
                                    Row(Modifier.fillMaxWidth().clickable { onOpenProduct(a.productId) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(a.productName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${Fmt.qty(a.available)} ${a.unit} available · min ${a.minStock}",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusChip(a.status)
                                    }
                                }
                            }
                        }
                    }
                    if (d.recentlyUpdated.isNotEmpty()) {
                        item {
                            SectionCard("Recently updated products") {
                                d.recentlyUpdated.forEach { r ->
                                    Row(Modifier.fillMaxWidth().clickable { onOpenProduct(r.productId) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(r.productName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${Fmt.qty(r.available)} ${r.unit} available · ${Fmt.dateTime(r.updatedAt)}",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusChip(r.status)
                                    }
                                }
                            }
                        }
                    }
                    if (d.recentMovements.isNotEmpty()) {
                        item {
                            SectionCard("Recent stock activity") {
                                d.recentMovements.forEach { m -> MovementRow(m) { onOpenProduct(m.productId) } }
                            }
                        }
                    }
                    if (d.categories.isNotEmpty()) {
                        item {
                            SectionCard("By category") {
                                d.categories.forEach { c ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(c.categoryName, Modifier.weight(1f))
                                        Text("${c.productCount} products · ${Fmt.qty(c.quantity)}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (d.warehouses.size > 1) {
                        item {
                            SectionCard("By warehouse") {
                                d.warehouses.forEach { w ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(w.warehouseName, Modifier.weight(1f))
                                        Text("${w.productCount} products · ${Fmt.qty(w.quantity)}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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

/** The most common tasks, filtered by what the member is allowed to do. */
@Composable
private fun QuickActions(
    onAddProduct: () -> Unit,
    onStockOperation: (String) -> Unit,
    onRecordSale: () -> Unit,
    onRecordPurchase: () -> Unit,
    onInventory: () -> Unit,
    onReports: () -> Unit,
    onStockActivity: () -> Unit,
) {
    val me = LocalMe.current
    val actions = buildList<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>> {
        if (me?.can("PRODUCT_CREATE") == true) add(Triple("Add Product", Icons.Default.AddBox, onAddProduct))
        if (me?.can("STOCK_ADD") == true) add(Triple("Add Stock", Icons.Default.AddCircleOutline) { onStockOperation("ADD") })
        if (me?.can("STOCK_REDUCE") == true) add(Triple("Reduce Stock", Icons.Default.RemoveCircleOutline) { onStockOperation("REDUCE") })
        if (me?.can("PURCHASE_MANAGE") == true) add(Triple("Record Purchase", Icons.Default.LocalShipping, onRecordPurchase))
        add(Triple("View Inventory", Icons.Default.Inventory2, onInventory))
        add(Triple("Stock Activity", Icons.Default.History, onStockActivity))
        if (me?.can("REPORT_VIEW") == true || me?.can("ANALYTICS_VIEW") == true)
            add(Triple("View Analytics", Icons.Default.Insights, onReports))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        actions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, icon, action) -> ActionTile(label, icon, action, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Sales, profit and stock value for members who can view analytics. */
@Composable
private fun SalesHighlightsCard(h: SalesHighlights, onAnalytics: () -> Unit, onOpenProduct: (Long) -> Unit) {
    val cur = h.today.currencyCode
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Today's sales", Fmt.money(h.today.revenue, cur), Icons.Default.PointOfSale,
                MaterialTheme.colorScheme.primary, Modifier.weight(1f), onAnalytics)
            StatTile("This month's sales", Fmt.money(h.month.revenue, cur), Icons.Default.CalendarMonth,
                MaterialTheme.colorScheme.primary, Modifier.weight(1f), onAnalytics)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Units sold this month", Fmt.qty(h.month.units), Icons.Default.ShoppingBag,
                MaterialTheme.colorScheme.secondary, Modifier.weight(1f), onAnalytics)
            StatTile("Gross profit this month", h.month.grossProfit?.let { Fmt.money(it, cur) } ?: "Unavailable",
                Icons.AutoMirrored.Filled.TrendingUp, StatusColors.ok, Modifier.weight(1f), onAnalytics)
        }
        SectionCard("This month") {
            h.monthBestSeller?.let { b ->
                Row(Modifier.fillMaxWidth().clickable { onOpenProduct(b.productId) }.padding(vertical = 4.dp)) {
                    Text("Best seller", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${b.name} · ${Fmt.qty(b.units)} ${b.unit}", fontWeight = FontWeight.Medium)
                }
            } ?: Text("No sales yet this month. Use Record Sale to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            h.monthLowestSeller?.let { l ->
                Row(Modifier.fillMaxWidth().clickable { onOpenProduct(l.productId) }.padding(vertical = 4.dp)) {
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

@Composable
fun MovementRow(m: Movement, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val name = if (m.variantName == m.productName) m.productName else "${m.productName} · ${m.variantName}"
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val detail = buildString {
                append(Fmt.movement(m.type))
                append(" · ")
                append(m.warehouseName)
                m.lotNumber?.let { append(" · lot $it") }
                m.reason?.takeIf { it.isNotBlank() && it != "Opening stock" }?.let { append(" · $it") }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${m.actorName} · ${Fmt.dateTime(m.createdAt)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${Fmt.signedQty(m.quantity)} ${m.unitCode}", color = movementColor(m.quantity), fontWeight = FontWeight.SemiBold)
            Text("${Fmt.qty(m.quantityBefore)} → ${Fmt.qty(m.quantityAfter)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
