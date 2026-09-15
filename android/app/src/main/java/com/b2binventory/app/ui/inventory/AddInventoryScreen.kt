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
fun AddInventoryScreen(
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit
) {
    var category by remember { mutableStateOf("Electronics") }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Piece") }
    var size by remember { mutableStateOf("") }
    var thickness by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var minimumStock by remember { mutableStateOf("0") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Universal categories for ANY business type
    val categories = listOf(
        "Electronics", "Clothing", "Groceries", "Hardware", 
        "Furniture", "Stationery", "Medical", "Cosmetics",
        "Toys", "Books", "Ply", "Mica", "Door", 
        "Sanitary", "Paint", "Services", "Other"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Inventory") },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { char -> char.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Quantity *") },
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Unit *") },
                        singleLine = true
                    )
                }
            }
            
            if (category == "Ply" || category == "Mica") {
                item {
                    OutlinedTextField(
                        value = thickness,
                        onValueChange = { thickness = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Thickness") },
                        placeholder = { Text("e.g., 6mm, 12mm") },
                        singleLine = true
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = size,
                        onValueChange = { size = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Size") },
                        placeholder = { Text("e.g., 8x4 ft") },
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
                        val qty = quantity.toIntOrNull() ?: 0
                        if (name.isBlank() || qty < 0 || unit.isBlank()) {
                            message = "Please fill Product name, Quantity and Unit."
                            return@Button
                        }
                        
                        loading = true
                        message = ""
                        scope.launch {
                            try {
                                ApiClient.apiService.createProduct(
                                    businessId = businessId,
                                    userId = userId,
                                    product = Product(
                                        category = category,
                                        name = name,
                                        brand = brand.ifBlank { null },
                                        sku = sku.ifBlank { null },
                                        barcode = barcode.ifBlank { null },
                                        unit = unit,
                                        size = size.ifBlank { null },
                                        thickness = thickness.ifBlank { null },
                                        color = color.ifBlank { null },
                                        model = model.ifBlank { null },
                                        description = description.ifBlank { null },
                                        quantity = qty,
                                        minimumStock = minimumStock.toIntOrNull() ?: 0
                                    )
                                )
                                onNavigateBack()
                            } catch (e: Exception) {
                                message = e.message ?: "Unable to save inventory."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (loading) "Saving..." else "Save Inventory")
                }
            }
            
            if (message.isNotBlank()) {
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
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
