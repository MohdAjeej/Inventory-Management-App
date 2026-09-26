package com.b2binventory.app.ui.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class VoucherOption(
    val title: String,
    val icon: ImageVector,
    val action: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    businessId: Long,
    onNavigateBack: () -> Unit
) {
    val createOptions = listOf(
        VoucherOption("Invoice", Icons.Default.Receipt) { },
        VoucherOption("Purchase", Icons.Default.ShoppingCart) { },
        VoucherOption("Sales Return", Icons.Default.AssignmentReturn) { },
        VoucherOption("Order", Icons.Default.ShoppingBag) { },
        VoucherOption("Receipt", Icons.Default.Payment) { },
        VoucherOption("Quotation", Icons.Default.Description) { },
        VoucherOption("Payment", Icons.Default.AccountBalance) { },
        VoucherOption("Party", Icons.Default.People) { }
    )
    
    val viewOptions = listOf(
        VoucherOption("Invoice", Icons.Default.Receipt) { },
        VoucherOption("Account Ledger", Icons.Default.Book) { },
        VoucherOption("Order", Icons.Default.ShoppingBag) { },
        VoucherOption("Bills Receivable", Icons.Default.Receipt) { }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vouchers & Documents") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Create Section
            Text(
                text = "Create",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(createOptions.size) { index ->
                    VoucherCard(createOptions[index])
                }
            }
            
            // View & Share Section
            Text(
                text = "View & Share",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(120.dp)
            ) {
                items(viewOptions.size) { index ->
                    VoucherCard(viewOptions[index])
                }
            }
            
            // Daily Summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {  },
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
        }
    }
}

@Composable
fun VoucherCard(option: VoucherOption) {
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
