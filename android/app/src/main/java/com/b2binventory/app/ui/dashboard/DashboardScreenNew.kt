package com.b2binventory.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val action: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenNew(
    businessId: Long,
    userId: Long,
    businessName: String,
    onNavigateToInventory: () -> Unit,
    onNavigateToVouchers: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToParties: () -> Unit
) {
    val currentDate = remember {
        val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        formatter.format(Date())
    }
    
    val createActions = listOf(
        QuickAction("Invoice", Icons.Default.Receipt) { },
        QuickAction("Purchase", Icons.Default.ShoppingCart) { },
        QuickAction("Sales Return", Icons.Default.AssignmentReturn) { },
        QuickAction("Order", Icons.Default.ShoppingBag) { },
        QuickAction("Receipt", Icons.Default.Payment) { },
        QuickAction("Quotation", Icons.Default.Description) { },
        QuickAction("Payment", Icons.Default.AccountBalance) { },
        QuickAction("Party", Icons.Default.People) { onNavigateToParties() }
    )
    
    val viewActions = listOf(
        QuickAction("Invoice", Icons.Default.Receipt) { },
        QuickAction("Account Ledger", Icons.Default.Book) { },
        QuickAction("Order", Icons.Default.ShoppingBag) { },
        QuickAction("Bills Receivable", Icons.Default.Receipt) { }
    )
    
    val reportsActions = listOf(
        QuickAction("Daily Summary", Icons.Default.Description) { },
        QuickAction("Account Ledger", Icons.Default.Book) { },
        QuickAction("Bills Receivable", Icons.Default.Receipt) { },
        QuickAction("Sales", Icons.Default.TrendingUp) { },
        QuickAction("Group Stock Status", Icons.Default.Inventory) { onNavigateToInventory() },
        QuickAction("Amount Receivable", Icons.Default.AccountBalance) { },
        QuickAction("Stock Status", Icons.Default.Inventory2) { onNavigateToInventory() },
        QuickAction("View All", Icons.Default.Menu) { onNavigateToReports() }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(businessName)
                        Text(
                            "$currentDate - $currentDate",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* WhatsApp */ }) {
                        Icon(Icons.Default.Message, "WhatsApp")
                    }
                    IconButton(onClick = { /* Notifications */ }) {
                        Icon(Icons.Default.Notifications, "Notifications")
                    }
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sales & Receipt Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SALES (₹)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "0.00",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "RECEIPT (₹)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "0.00",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // Create Section
            ActionSection("Create", createActions)
            
            // View & Share Section
            ActionSection("View & Share", viewActions)
            
            // Daily Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Daily Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Know your business in one click",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Reports Section
            ActionSection("Reports", reportsActions)
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ActionSection(title: String, actions: List<QuickAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height((actions.size / 4 + 1) * 100.dp)
        ) {
            items(actions.size) { index ->
                ActionCard(actions[index])
            }
        }
    }
}

@Composable
fun ActionCard(action: QuickAction) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { action.action() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                action.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
