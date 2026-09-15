package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductScreen(
    productId: Long,
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var category by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var thickness by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var minimumStock by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    val categories = listOf("Ply", "Mica", "Hardware", "Door", "Sanitary", "Paint", "Other")
    
    fun loadProduct() {
        scope.launch {
            try {
                val products = ApiClient.apiService.products(businessId)
                product = products.find { it.id == productId }
                product?.let { prod ->
                    category = prod.category
                    name = prod.name
                    brand = prod.brand ?: ""
                    sku = prod.sku ?: ""
                    barcode = prod.barcode ?: ""
                    unit = prod.unit
                    size = prod.size ?: ""
                    thickness = prod.thickness ?: ""
                    color = prod.color ?: ""
                    model = prod.model ?: ""
                    description = prod.description ?: ""
                    minimumStock = prod.minimumStock.toString()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadProduct()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Product") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Note: Stock quantity cannot be edited here. Use Stock Adjustment instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            item {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }
            
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Product Name *") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Brand") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SKU") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Barcode") },
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Unit *") },
                    singleLine = true
                )
            }
            
            if (category == "Ply" || category == "Mica") {
                item {
                    OutlinedTextField(
                        value = thickness,
                        onValueChange = { thickness = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Thickness") },
                        singleLine = true
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = size,
                        onValueChange = { size = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Size") },
                        singleLine = true
                    )
                }
            }
            
            if (category == "Mica" || category == "Paint" || category == "Door") {
                item {
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Color") },
                        singleLine = true
                    )
                }
            }
            
            if (category == "Hardware" || category == "Door") {
                item {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model") },
                        singleLine = true
                    )
                }
            }
            
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 3
                )
            }
            
            item {
                OutlinedTextField(
                    value = minimumStock,
                    onValueChange = { minimumStock = it.filter { char -> char.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Minimum Stock Alert") },
                    singleLine = true
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (name.isBlank() || unit.isBlank()) {
                            message = "Please fill Product name and Unit."
                            return@Button
                        }
                        
                        loading = true
                        message = ""
                        scope.launch {
                            try {
                                // Note: This requires an update endpoint in the API
                                // For now, we'll show a message
                                message = "Product update feature coming soon"
                                // TODO: Implement product update API call
                            } catch (e: Exception) {
                                message = e.message ?: "Unable to update product."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (loading) "Saving..." else "Update Product")
                }
            }
            
            if (message.isNotBlank()) {
                item {
                    Text(
                        text = message,
                        color = if (message.contains("success", ignoreCase = true))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
