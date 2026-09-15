package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import com.b2binventory.app.data.StockRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentScreen(
    productId: Long,
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var selectedType by remember { mutableStateOf("ADD") }
    var quantity by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    fun loadProduct() {
        scope.launch {
            try {
                val products = ApiClient.apiService.products(businessId)
                product = products.find { it.id == productId }
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
                title = { Text("Adjust Stock") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            product?.let { prod ->
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = prod.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Current Stock: ${prod.quantity} ${prod.unit}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                Text(
                    text = "Adjustment Type",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Column(Modifier.selectableGroup()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedType == "ADD",
                                onClick = { selectedType = "ADD" },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == "ADD",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Add Stock",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Increase inventory quantity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedType == "REDUCE",
                                onClick = { selectedType = "REDUCE" },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == "REDUCE",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Reduce Stock",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Decrease inventory quantity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { char -> char.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Quantity *") },
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reason (optional)") },
                    minLines = 2,
                    placeholder = { Text("e.g., New stock received, Damaged goods, etc.") }
                )
                
                val qty = quantity.toIntOrNull() ?: 0
                val newQuantity = if (selectedType == "ADD") {
                    prod.quantity + qty
                } else {
                    prod.quantity - qty
                }
                
                if (qty > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Current: ${prod.quantity} ${prod.unit}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${if (selectedType == "ADD") "+" else "-"}$qty ${prod.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedType == "ADD") 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "New Stock: $newQuantity ${prod.unit}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = {
                        val qty = quantity.toIntOrNull() ?: 0
                        if (qty <= 0) {
                            message = "Please enter a valid quantity"
                            return@Button
                        }
                        
                        if (selectedType == "REDUCE" && qty > prod.quantity) {
                            message = "Cannot reduce more than available stock"
                            return@Button
                        }
                        
                        loading = true
                        message = ""
                        scope.launch {
                            try {
                                ApiClient.apiService.changeStock(
                                    id = productId,
                                    businessId = businessId,
                                    userId = userId,
                                    body = StockRequest(
                                        type = selectedType,
                                        quantity = qty,
                                        reason = reason.ifBlank { null }
                                    )
                                )
                                onNavigateBack()
                            } catch (e: Exception) {
                                message = e.message ?: "Failed to adjust stock"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && quantity.toIntOrNull() ?: 0 > 0
                ) {
                    Text(if (loading) "Processing..." else "Confirm Adjustment")
                }
                
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
