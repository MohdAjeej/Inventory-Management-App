package com.b2binventory.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    businessId: Long,
    userId: Long,
    onNavigateToInventory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    fun loadProducts() {
        loading = true
        scope.launch {
            try {
                products = ApiClient.apiService.products(businessId)
            } catch (e: Exception) {
                // Handle error
            } finally {
                loading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadProducts()
    }
    
    val lowStockProducts = products.filter { it.quantity > 0 && it.quantity <= it.minimumStock }
    val outOfStockProducts = products.filter { it.quantity == 0 }
    val totalProducts = products.size
    val totalQuantity = products.sumOf { it.quantity }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToInventory) {
                Icon(Icons.Default.Add, contentDescription = "Add Inventory")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Inventory Overview",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            item {
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
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = totalProducts.toString(),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Total Products",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = totalQuantity.toString(),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Total Stock",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            if (outOfStockProducts.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "${outOfStockProducts.size} Products Out of Stock",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            outOfStockProducts.take(3).forEach { product ->
                                Text(
                                    text = "• ${product.name}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (outOfStockProducts.size > 3) {
                                Text(
                                    text = "... and ${outOfStockProducts.size - 3} more",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            
            if (lowStockProducts.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "${lowStockProducts.size} Products Low on Stock",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            lowStockProducts.take(3).forEach { product ->
                                Text(
                                    text = "• ${product.name} (${product.quantity} ${product.unit})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (lowStockProducts.size > 3) {
                                Text(
                                    text = "... and ${lowStockProducts.size - 3} more",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Button(
                    onClick = onNavigateToInventory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View All Inventory")
                }
            }
            
            item {
                Text(
                    text = "Recent Products",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            items(products.take(10)) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToInventory() }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${product.category} ${product.brand?.let { "• $it" } ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stock: ${product.quantity} ${product.unit}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (product.quantity == 0) {
                                Text(
                                    text = "OUT OF STOCK",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            } else if (product.quantity <= product.minimumStock) {
                                Text(
                                    text = "LOW STOCK",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
