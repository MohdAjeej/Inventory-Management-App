package com.b2binventory.app.ui.settings

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@Composable
fun MoreScreen(onNavigate: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { c, _ -> MoreViewModel(c.authRepository) }
    val me = LocalMe.current
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshMe() }

    val items = listOf(
        MenuItem("Stock activity", "Add, reduce, correct or move stock, and see every change", Icons.Default.SwapVert, Routes.STOCK),
        MenuItem("Profile", "Your details, password and businesses", Icons.Default.Person, Routes.PROFILE),
        MenuItem("Business settings", "Business profile and inventory preferences", Icons.Default.Store, Routes.BUSINESS),
        MenuItem("Categories & fields", "Categories, subcategories and custom product fields", Icons.Default.Category, Routes.CATEGORIES),
        MenuItem("Units", "Units of measure and conversions", Icons.Default.Straighten, Routes.UNITS),
        MenuItem("Warehouses & branches", "Locations where stock is held", Icons.Default.Warehouse, Routes.WAREHOUSES),
        MenuItem("Stock counts", "Count stock and reconcile differences", Icons.AutoMirrored.Filled.FactCheck, Routes.COUNTS, "STOCK_COUNT"),
        MenuItem("Employees", "Team members, roles and permissions", Icons.Default.Groups, Routes.EMPLOYEES, "EMPLOYEE_MANAGE"),
        MenuItem("Notifications", "Stock alerts and team events", Icons.Default.Notifications, Routes.NOTIFICATIONS),
        MenuItem("Appearance & language", "Dark mode and app language", Icons.Default.Palette, Routes.APPEARANCE),
        MenuItem("Backup & restore", "Save all business data to a file, or restore one", Icons.Default.Backup, Routes.BACKUP),
        MenuItem("Pending sync", "Changes saved on this phone while offline", Icons.Default.CloudSync, Routes.PENDING_SYNC),
    ).filter { it.permission == null || me?.can(it.permission) == true }

    Scaffold(topBar = { AppTopBar("More") }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(me?.fullName ?: "") },
                    supportingContent = { Text("${Fmt.role(me?.role ?: "")} · ${me?.businessName ?: ""}") },
                    leadingContent = { Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp)) },
                )
                HorizontalDivider()
            }
            items(items) { item ->
                ListItem(
                    headlineContent = { Text(item.label) },
                    supportingContent = { Text(item.description) },
                    leadingContent = { Icon(item.icon, null) },
                    modifier = Modifier.clickableItem { onNavigate(item.route) },
                )
            }
            item {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickableItem { confirmLogout = true },
                )
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
