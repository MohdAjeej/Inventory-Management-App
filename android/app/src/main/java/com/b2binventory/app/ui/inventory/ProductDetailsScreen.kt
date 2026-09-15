package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: Long,
    businessId: Long,
    userId: Long,
    onNavigateToStockAdjustment: () -> Unit,
    onNavigateToStockHistory: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    fun loadProduct() {
        loading = true
        scope.launch {
            try {
                val products = ApiClient.apiService.products(businessId)
                product = products.find { it.id == productId }
            } catch (e: Exception) {
                // Handle error
            } finally {
                loading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadProduct()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (loading || product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = product!!.name,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = product!!.category,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Stock Information",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            DetailRow("Current Stock", "${product!!.quantity} ${product!!.unit}")
                            DetailRow("Minimum Stock", "${product!!.minimumStock} ${product!!.unit}")
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            when {
                                product!!.quantity == 0 -> {
                                    Text(
                                        text = "OUT OF STOCK",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                product!!.quantity <= product!!.minimumStock -> {
                                    Text(
                                        text = "LOW STOCK WARNING",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Product Details",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            product!!.brand?.let {
                                DetailRow("Brand", it)
                            }
                            product!!.sku?.let {
                                DetailRow("SKU", it)
                            }
                            product!!.barcode?.let {
                                DetailRow("Barcode", it)
                            }
                            product!!.size?.let {
                                DetailRow("Size", it)
                            }
                            product!!.thickness?.let {
                                DetailRow("Thickness", it)
                            }
                            product!!.color?.let {
                                DetailRow("Color", it)
                            }
                            product!!.model?.let {
                                DetailRow("Model", it)
                            }
                            product!!.description?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(text = it)
                            }
                        }
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onNavigateToStockAdjustment,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Adjust Stock")
                        }
                        
                        OutlinedButton(
                            onClick = onNavigateToStockHistory,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("History")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
