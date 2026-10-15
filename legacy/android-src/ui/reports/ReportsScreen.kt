package com.b2binventory.app.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ReportOption(
    val title: String,
    val icon: ImageVector,
    val action: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    businessId: Long,
    onNavigateToInventory: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val accountsBookReports = listOf(
        ReportOption("Day Book", Icons.Default.Today) { },
        ReportOption("Daily Summary", Icons.Default.Description) { },
        ReportOption("Account Ledger", Icons.Default.Book) { },
        ReportOption("Sales", Icons.Default.TrendingUp) { },
        ReportOption("Payment", Icons.Default.Payment) { },
        ReportOption("Receipt", Icons.Default.Receipt) { },
        ReportOption("Purchase", Icons.Default.ShoppingCart) { },
        ReportOption("Cash", Icons.Default.AttachMoney) { },
        ReportOption("Bank", Icons.Default.AccountBalance) { },
        ReportOption("Trial Balance", Icons.Default.Balance) { }
    )
    
    val outstandingReports = listOf(
        ReportOption("Bills Receivable", Icons.Default.Receipt) { },
        ReportOption("Bills Payable", Icons.Default.Receipt) { },
        ReportOption("Amount Receivable", Icons.Default.AccountBalance) { },
        ReportOption("Amount Receivable", Icons.Default.AccountBalance) { },
        ReportOption("Ageing Receivables", Icons.Default.DateRange) { },
        ReportOption("Ageing Payables", Icons.Default.DateRange) { }
    )
    
    val inventoryReports = listOf(
        ReportOption("Stock Status", Icons.Default.Inventory) { onNavigateToInventory() },
        ReportOption("Group Stock Status", Icons.Default.Inventory2) { }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Reports") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            
            // Accounts Book
            ReportSection("Accounts Book", accountsBookReports)
            
            // Outstanding Analysis
            ReportSection("Outstanding Analysis", outstandingReports)
            
            // Inventory
            ReportSection("Inventory", inventoryReports)
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ReportSection(title: String, reports: List<ReportOption>) {
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
            modifier = Modifier.height(((reports.size / 4 + 1) * 100).dp)
        ) {
            items(reports.size) { index ->
                ReportCard(reports[index])
            }
        }
    }
}

@Composable
fun ReportCard(option: ReportOption) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { option.action() },
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
                option.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
