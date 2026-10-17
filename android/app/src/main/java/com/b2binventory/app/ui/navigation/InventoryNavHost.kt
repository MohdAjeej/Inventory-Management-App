package com.b2binventory.app.ui.navigation

import com.b2binventory.app.ui.trade.*
import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.b2binventory.app.ui.common.WidthClass
import com.b2binventory.app.ui.common.rememberWidthClass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.b2binventory.app.ui.auth.JoinScreen
import com.b2binventory.app.ui.auth.LoginScreen
import com.b2binventory.app.ui.auth.RegisterScreen
import com.b2binventory.app.ui.catalog.AttributesScreen
import com.b2binventory.app.ui.catalog.CategoriesScreen
import com.b2binventory.app.ui.catalog.UnitsScreen
import com.b2binventory.app.ui.common.LocalMe
import com.b2binventory.app.ui.common.appContainer
import com.b2binventory.app.ui.dashboard.DashboardScreen
import com.b2binventory.app.ui.employees.EmployeeDetailScreen
import com.b2binventory.app.ui.employees.EmployeesScreen
import com.b2binventory.app.ui.notifications.NotificationsScreen
import com.b2binventory.app.ui.products.ProductDetailScreen
import com.b2binventory.app.ui.products.ProductFormScreen
import com.b2binventory.app.ui.products.ProductListScreen
import com.b2binventory.app.ui.reports.ReportsScreen
import com.b2binventory.app.ui.sales.PurchaseDetailScreen
import com.b2binventory.app.ui.sales.RecordPurchaseScreen
import com.b2binventory.app.ui.sales.RecordSaleScreen
import com.b2binventory.app.ui.sales.ReturnScreen
import com.b2binventory.app.ui.sales.SaleDetailScreen
import com.b2binventory.app.ui.sales.SalesHubScreen
import com.b2binventory.app.ui.settings.AppearanceScreen
import com.b2binventory.app.ui.settings.BackupScreen
import com.b2binventory.app.ui.sync.OfflineBanner
import com.b2binventory.app.ui.sync.PendingSyncScreen
import com.b2binventory.app.ui.settings.BusinessSettingsScreen
import com.b2binventory.app.ui.settings.MoreScreen
import com.b2binventory.app.ui.settings.ProfileScreen
import com.b2binventory.app.ui.stock.StockCountDetailScreen
import com.b2binventory.app.ui.stock.StockCountsScreen
import com.b2binventory.app.ui.stock.StockHubScreen
import com.b2binventory.app.ui.stock.StockOperationScreen
import com.b2binventory.app.ui.warehouses.WarehouseDetailScreen
import com.b2binventory.app.ui.warehouses.WarehousesScreen

/** A bottom tab is shown when the member has any of [permissions] (or always when empty). */
private data class Tab(val route: String, val navRoute: String, val label: String, val icon: ImageVector,
                       val permissions: List<String> = emptyList())

private val tabs = listOf(
    Tab(Routes.HOME, Routes.HOME, "Home", Icons.Default.Dashboard),
    Tab(Routes.PRODUCTS, Routes.products(), "Inventory", Icons.Default.Inventory2),
    Tab(Routes.SALES, Routes.SALES, "Sales", Icons.Default.PointOfSale,
        listOf("SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW", "PURCHASE_MANAGE")),
    Tab(Routes.REPORTS, Routes.REPORTS, "Analytics", Icons.Default.Insights, listOf("REPORT_VIEW", "ANALYTICS_VIEW")),
    Tab(Routes.MORE, Routes.MORE, "More", Icons.Default.Menu),
)

@Composable
fun InventoryNavHost() {
    val container = appContainer()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val authRoutes = setOf(Routes.SPLASH, Routes.LOGIN, Routes.REGISTER, Routes.JOIN)

    // Session ended (logout, revoked or expired refresh token): return to sign-in.
    LaunchedEffect(session == null, currentRoute) {
        if (session == null && currentRoute != null && currentRoute !in authRoutes) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    CompositionLocalProvider(LocalMe provides session?.me) {
        // Phones use a bottom bar on the main tabs; tablets keep a navigation rail beside every signed-in screen.
        val wide = rememberWidthClass() != WidthClass.COMPACT
        val signedIn = session != null && currentRoute != null && currentRoute !in authRoutes
        Scaffold(
            bottomBar = {
                val bottomTabs = !wide && currentRoute in Routes.topLevel
                Column {
                    if (signedIn && currentRoute != Routes.PENDING_SYNC) {
                        OfflineBanner(onOpenPending = { nav.navigate(Routes.PENDING_SYNC) }, padNavigationBar = !bottomTabs)
                    }
                    if (bottomTabs) BottomBar(nav, currentRoute)
                }
            },
        ) { padding ->
          Row(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            if (wide && signedIn) SideRail(nav, currentRoute)
            NavHost(nav, startDestination = Routes.SPLASH, modifier = Modifier.weight(1f)) {
                composable(Routes.SPLASH) {
                    LaunchedEffect(Unit) {
                        val target = if (container.sessionStore.current != null) Routes.HOME else Routes.LOGIN
                        nav.navigate(target) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    }
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onSignedIn = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                        onRegister = { nav.navigate(Routes.REGISTER) },
                        onJoin = { nav.navigate(Routes.JOIN) },
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        onRegistered = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.JOIN) {
                    JoinScreen(
                        onJoined = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                        onBack = { nav.popBackStack() },
                    )
                }

                composable(Routes.HOME) {
                    DashboardScreen(
                        onOpenProducts = { status -> nav.navigateTab(Routes.products(status)) },
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                        onAddProduct = { nav.navigate(Routes.PRODUCT_NEW) },
                        onStockOperation = { type -> nav.navigate(Routes.stockOperation(type = type)) },
                        onRecordSale = { nav.navigate(Routes.saleNew()) },
                        onRecordPurchase = { nav.navigate(Routes.purchaseNew()) },
                        onReports = { nav.navigateTab(Routes.REPORTS) },
                        onStockActivity = { nav.navigate(Routes.STOCK) },
                        onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                        onTrade = { nav.navigate(it) },
                    )
                }
                composable(
                    Routes.PRODUCTS,
                    arguments = listOf(navArgument("status") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) {
                    ProductListScreen(
                        onOpen = { nav.navigate(Routes.product(it)) },
                        onAdd = { nav.navigate(Routes.PRODUCT_NEW) },
                        onStockOperation = { id, type ->
                            nav.navigate(if (type == "SALE") Routes.saleNew(id) else Routes.stockOperation(id, type))
                        },
                        onRecordSale = { nav.navigate(Routes.saleNew()) },
                        onStockActivity = { nav.navigate(Routes.STOCK) },
                    )
                }
                composable(Routes.STOCK) {
                    StockHubScreen(
                        onBack = { nav.popBackStack() },
                        onNewOperation = { type -> nav.navigate(Routes.stockOperation(type = type)) },
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                        onStockCounts = { nav.navigate(Routes.COUNTS) },
                        onRecordSale = { nav.navigate(Routes.saleNew()) },
                        onSales = { nav.navigateTab(Routes.SALES) },
                    )
                }
                composable(Routes.SALES) {
                    SalesHubScreen(
                        onRecordSale = { nav.navigate(Routes.saleNew()) },
                        onRecordPurchase = { nav.navigate(Routes.purchaseNew()) },
                        onOpenSale = { nav.navigate(Routes.sale(it)) },
                        onOpenPurchase = { nav.navigate(Routes.purchase(it)) },
                    )
                }
                composable(Routes.REPORTS) {
                    ReportsScreen(
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                        onAddStock = { nav.navigate(Routes.stockOperation(it, "ADD")) },
                        onRecordSale = { nav.navigate(Routes.saleNew()) },
                    )
                }
                composable(Routes.MORE) {
                    MoreScreen(
                        onNavigate = { nav.navigate(it) },
                        onSignedOut = { nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
                    )
                }

                composable(Routes.PRODUCT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ProductDetailScreen(
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(Routes.productEdit(it)) },
                        onStockOperation = { id, type ->
                            nav.navigate(when (type) {
                                "SALE" -> Routes.saleNew(id)
                                "PURCHASE" -> Routes.purchaseNew(id)
                                else -> Routes.stockOperation(id, type)
                            })
                        },
                    )
                }
                composable(Routes.PRODUCT_NEW) {
                    ProductFormScreen(
                        onBack = { nav.popBackStack() },
                        onSaved = { id -> nav.navigate(Routes.product(id)) { popUpTo(Routes.PRODUCT_NEW) { inclusive = true } } },
                    )
                }
                composable(Routes.PRODUCT_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ProductFormScreen(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
                }
                composable(
                    Routes.STOCK_OPERATION,
                    arguments = listOf(
                        navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {
                    StockOperationScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
                }
                composable(
                    Routes.SALE_NEW,
                    arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) {
                    // After saving, show the sale (receipt) instead of the form.
                    RecordSaleScreen(onBack = { nav.popBackStack() }, onSaved = { id ->
                        nav.navigate(Routes.sale(id, recorded = true)) { popUpTo(Routes.SALE_NEW) { inclusive = true } }
                    })
                }
                composable(
                    Routes.SALE,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("recorded") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) {
                    SaleDetailScreen(
                        onBack = { nav.popBackStack() },
                        onReturn = { nav.navigate(Routes.saleReturn(it)) },
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                    )
                }
                composable(Routes.SALE_RETURN, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ReturnScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
                }
                composable(
                    Routes.PURCHASE_NEW,
                    arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) {
                    RecordPurchaseScreen(onBack = { nav.popBackStack() }, onSaved = { id ->
                        nav.navigate(Routes.purchase(id)) { popUpTo(Routes.PURCHASE_NEW) { inclusive = true } }
                    })
                }
                composable(Routes.PURCHASE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    PurchaseDetailScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.COUNTS) {
                    StockCountsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.count(it)) })
                }
                composable(Routes.COUNT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    StockCountDetailScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.CATEGORIES) {
                    CategoriesScreen(
                        onBack = { nav.popBackStack() },
                        onAttributes = { id, name -> nav.navigate(Routes.attributes(id, name)) },
                    )
                }
                composable(
                    Routes.ATTRIBUTES,
                    arguments = listOf(
                        navArgument("categoryId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("categoryName") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { AttributesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.UNITS) { UnitsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.WAREHOUSES) {
                    WarehousesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.warehouse(it)) })
                }
                composable(Routes.WAREHOUSE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    WarehouseDetailScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.EMPLOYEES) {
                    EmployeesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.employee(it)) })
                }
                composable(Routes.EMPLOYEE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    EmployeeDetailScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(onBack = { nav.popBackStack() }, onOpenProduct = { nav.navigate(Routes.product(it)) },
                        onOpenTradeInvoice = { nav.navigate(Routes.tradeInvoice(it)) }, onOpenNetwork = { nav.navigate(Routes.NETWORK) })
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onBack = { nav.popBackStack() },
                        onSwitched = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                        onSignedOut = { nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
                    )
                }
                composable(Routes.BUSINESS) { BusinessSettingsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.NETWORK) {
                    BusinessNetworkScreen(onBack = { nav.popBackStack() }, onCreateInvoice = { nav.navigate(Routes.tradeNew(it)) })
                }
                composable(Routes.TRADE_INVOICES, arguments = listOf(navArgument("direction") { type = NavType.StringType })) { entry ->
                    TradeInvoicesScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(Routes.tradeInvoice(it)) },
                        onCreate = { nav.navigate(Routes.tradeNew()) },
                    )
                }
                composable(
                    Routes.TRADE_NEW,
                    arguments = listOf(navArgument("buyerId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) {
                    CreateTradeInvoiceScreen(
                        onBack = { nav.popBackStack() },
                        onCreated = { id -> nav.navigate(Routes.tradeInvoice(id)) { popUpTo(Routes.TRADE_NEW) { inclusive = true } } },
                        onFindBusiness = { nav.navigate(Routes.NETWORK) },
                    )
                }
                composable(Routes.TRADE_INVOICE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    TradeInvoiceDetailScreen(
                        onBack = { nav.popBackStack() },
                        onOpenSale = { nav.navigate(Routes.sale(it)) },
                        onOpenPurchase = { nav.navigate(Routes.purchase(it)) },
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                    )
                }
                composable(Routes.PRODUCT_LINKS) {
                    ProductLinksScreen(onBack = { nav.popBackStack() }, onOpenProduct = { nav.navigate(Routes.product(it)) })
                }
                composable(Routes.APPEARANCE) { AppearanceScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.PENDING_SYNC) { PendingSyncScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.BACKUP) {
                    BackupScreen(
                        onBack = { nav.popBackStack() },
                        onRestored = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                    )
                }
            }
          }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openTab(tab: Tab, currentRoute: String?) {
    if (currentRoute != tab.route) {
        navigate(tab.navRoute) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
private fun visibleTabs(): List<Tab> {
    val me = LocalMe.current
    return tabs.filter { t -> t.permissions.isEmpty() || t.permissions.any { me?.can(it) == true } }
}

@Composable
private fun SideRail(nav: NavHostController, currentRoute: String?) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(12.dp))
        visibleTabs().forEach { tab ->
            NavigationRailItem(
                selected = currentRoute == tab.route,
                onClick = { nav.openTab(tab, currentRoute) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, currentRoute: String?) {
    val me = LocalMe.current
    NavigationBar {
        tabs.filter { t -> t.permissions.isEmpty() || t.permissions.any { me?.can(it) == true } }.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        nav.navigate(tab.navRoute) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
