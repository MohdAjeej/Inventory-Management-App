package com.b2binventory.app.ui.settings

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.repository.AuthRepository
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.sync.rememberPendingOps
import com.b2binventory.app.R
import androidx.compose.ui.res.pluralStringResource
import com.b2binventory.app.ui.navigation.Routes
import kotlinx.coroutines.launch

class MoreViewModel(private val auth: AuthRepository) : ViewModel() {
    fun refreshMe() {
        viewModelScope.launch { auth.refreshMe() }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.logout()
            onDone()
        }
    }
}

private data class MenuItem(val label: String, val description: String, val icon: ImageVector, val route: String,
                            val permission: String? = null)

private data class MenuGroup(val title: String, val items: List<MenuItem>)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { c, _ -> MoreViewModel(c.authRepository) }
    val me = LocalMe.current
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshMe() }

    val groups = listOf(
        MenuGroup("Inventory", listOf(
            MenuItem("Stock activity", "Add, reduce, correct or move stock, and see every change", Icons.Default.SwapVert, Routes.STOCK),
            MenuItem("Categories & fields", "Categories, subcategories and custom product fields", Icons.Default.Category, Routes.CATEGORIES),
            MenuItem("Units", "Units of measure and conversions", Icons.Default.Straighten, Routes.UNITS),
            MenuItem("Warehouses & branches", "Locations where stock is held", Icons.Default.Warehouse, Routes.WAREHOUSES),
            MenuItem("Stock counts", "Count stock and reconcile differences", Icons.AutoMirrored.Filled.FactCheck, Routes.COUNTS, "STOCK_COUNT"),
        )),
        MenuGroup("Business network", listOf(
            MenuItem("Connected businesses", "Find suppliers and customers on the app and connect", Icons.Default.Hub, Routes.NETWORK),
            MenuItem("Invoices to businesses", "Send invoices that update the buyer's stock when approved", Icons.Default.Outbox,
                Routes.tradeInvoices("SALES"), "SALE_RECORD"),
            MenuItem("Invoices from suppliers", "Approve purchase invoices to add the goods to your stock", Icons.Default.MoveToInbox,
                Routes.tradeInvoices("PURCHASES"), "PURCHASE_MANAGE"),
            MenuItem("Product links", "Which supplier products go into which of your products", Icons.Default.Link,
                Routes.PRODUCT_LINKS, "PURCHASE_MANAGE"),
        )),
        MenuGroup("Business", listOf(
            MenuItem("Business settings", "Business profile and inventory preferences", Icons.Default.Store, Routes.BUSINESS),
            MenuItem("Employees", "Team members, roles and permissions", Icons.Default.Groups, Routes.EMPLOYEES, "EMPLOYEE_MANAGE"),
            MenuItem("Backup & restore", "Save all business data to a file, or restore one", Icons.Default.Backup, Routes.BACKUP),
        )),
        MenuGroup("App", listOf(
            MenuItem("Appearance & language", "Dark mode and app language", Icons.Default.Palette, Routes.APPEARANCE),
            MenuItem("Notifications", "Stock alerts and team events", Icons.Default.Notifications, Routes.NOTIFICATIONS),
            MenuItem("Pending sync", "Changes saved on this phone while offline", Icons.Default.CloudSync, Routes.PENDING_SYNC),
        )),
        MenuGroup("Account", listOf(
            MenuItem("Profile", "Your details, password and businesses", Icons.Default.Person, Routes.PROFILE),
        )),
    ).map { g -> g.copy(items = g.items.filter { it.permission == null || me?.can(it.permission) == true }) }
        .filter { it.items.isNotEmpty() }

    Scaffold(topBar = { AppTopBar("More") }) { padding ->
        ResponsiveContent(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    AppCard(onClick = { onNavigate(Routes.PROFILE) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(me?.fullName ?: "", 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                androidx.compose.material3.Text(me?.fullName ?: "", style = MaterialTheme.typography.titleMedium)
                                androidx.compose.material3.Text(me?.businessName ?: "", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                me?.role?.let { RoleBadge(it) }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                items(groups) { g ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(g.title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        AppCard {
                            g.items.forEachIndexed { i, item ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                MenuRow(item.label, item.description, item.icon) { onNavigate(item.route) }
                            }
                        }
                    }
                }
                item {
                    AppCard {
                        MenuRow("Sign out", null, Icons.AutoMirrored.Filled.Logout, MaterialTheme.colorScheme.error) {
                            confirmLogout = true
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    val unsynced = rememberPendingOps().size
    if (confirmLogout) {
        val message = tr("You will need to sign in again to use the app.") +
            if (unsynced > 0) "\n\n" + pluralStringResource(R.plurals.logout_pending_warning, unsynced, unsynced) else ""
        ConfirmDialog("Sign out?", message, "Sign out", destructive = unsynced > 0,
            onConfirm = { confirmLogout = false; vm.logout(onSignedOut) }, onDismiss = { confirmLogout = false })
    }
}

fun Modifier.clickableItem(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun MenuRow(label: String, description: String?, icon: ImageVector,
                    tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (tint == MaterialTheme.colorScheme.error) tint else Color.Unspecified)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (description != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
