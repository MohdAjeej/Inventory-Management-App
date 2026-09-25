package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryScreenPro(
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
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    val categories = listOf(
        "Electronics", "Clothing", "Groceries", "Hardware", 
        "Furniture", "Stationery", "Medical", "Cosmetics",
        "Toys", "Books", "Ply", "Mica", "Door", 
        "Sanitary", "Paint", "Services", "Other"
    )
    
    val units = listOf("Piece", "Kg", "Gram", "Liter", "ML", "Meter", "Feet", "Box", "Carton", "Dozen", "Set", "Sheet")
    
    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onNavigateBack()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Success!") },
            text = { Text("Product '$name' has been added successfully.") },
            confirmButton = {
                Button(onClick = {
                    showSuccessDialog = false
                    onNavigateBack()
                }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            "Add New Product",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Fill in product details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Category Selection
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { cat ->
                                CategoryChip(
                                    text = cat,
                                    selected = category == cat,
                                    onClick = { category = cat }
                                )
                            }
                        }
                    }
                }
            }
            
            // Basic Information
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Info,
                            title = "Basic Information"
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ModernTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Product Name",
                            icon = Icons.Default.ShoppingBag,
                            required = true
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ModernTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = "Brand",
                            icon = Icons.Default.Label
                        )
                    }
                }
            }
            
            // Identification
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = Icons.Default.QrCode,
                            title = "Identification"
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModernTextField(
                                value = sku,
                                onValueChange = { sku = it },
                                label = "SKU",
                                icon = Icons.Default.Tag,
                                modifier = Modifier.weight(1f)
                            )
                            
                            ModernTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = "Barcode",
                                icon = Icons.Default.QrCode2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Stock Information
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Inventory,
                            title = "Stock Information"
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModernTextField(
                                value = quantity,
                                onValueChange = { quantity = it.filter { char -> char.isDigit() } },
                                label = "Quantity",
                                icon = Icons.Default.Numbers,
                                keyboardType = KeyboardType.Number,
                                required = true,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Unit Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                var expanded by remember { mutableStateOf(false) }
                                
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = { },
                                    label = { Text("Unit") },
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = { expanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    units.forEach { u ->
                                        DropdownMenuItem(
                                            text = { Text(u) },
                                            onClick = {
                                                unit = u
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ModernTextField(
                            value = minimumStock,
                            onValueChange = { minimumStock = it.filter { char -> char.isDigit() } },
                            label = "Minimum Stock Alert",
                            icon = Icons.Default.Warning,
                            keyboardType = KeyboardType.Number
                        )
                    }
                }
            }
            
            // Additional Details
            if (category in listOf("Ply", "Mica", "Door")) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                icon = Icons.Default.Straighten,
                                title = "Specifications"
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ModernTextField(
                                    value = size,
                                    onValueChange = { size = it },
                                    label = "Size",
                                    icon = Icons.Default.AspectRatio,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                ModernTextField(
                                    value = thickness,
                                    onValueChange = { thickness = it },
                                    label = "Thickness",
                                    icon = Icons.Default.Height,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Description
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Description,
                            title = "Description"
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Product Description") },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            
            // Error Message
            if (message.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Save Button
            item {
                Button(
                    onClick = {
                        // Validation
                        if (name.isBlank()) {
                            message = "⚠️ Product name is required"
                            return@Button
                        }
                        
                        val qty = quantity.toIntOrNull()
                        if (qty == null || qty < 0) {
                            message = "⚠️ Please enter a valid quantity (0 or more)"
                            return@Button
                        }
                        
                        if (unit.isBlank()) {
                            message = "⚠️ Please select a unit"
                            return@Button
                        }
                        
                        // Start loading
                        loading = true
                        message = ""
                        
                        scope.launch {
                            try {
                                val product = Product(
                                    category = category,
                                    name = name.trim(),
                                    brand = brand.trim().ifBlank { null },
                                    sku = sku.trim().ifBlank { null },
                                    barcode = barcode.trim().ifBlank { null },
                                    unit = unit,
                                    size = size.trim().ifBlank { null },
                                    thickness = thickness.trim().ifBlank { null },
                                    color = color.trim().ifBlank { null },
                                    model = model.trim().ifBlank { null },
                                    description = description.trim().ifBlank { null },
                                    quantity = qty,
                                    minimumStock = minimumStock.toIntOrNull() ?: 0
                                )
                                
                                val result = ApiClient.apiService.createProduct(
                                    businessId = businessId,
                                    userId = userId,
                                    product = product
                                )
                                
                                // Show success dialog
                                showSuccessDialog = true
                                
                            } catch (e: Exception) {
                                message = "❌ Error: ${e.message ?: "Unable to save. Please check your internet connection and try again."}"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(56.dp),
                    enabled = !loading,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (loading) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Product", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) ButtonDefaults.outlinedButtonBorder else null,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer 
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { 
            Row {
                Text(label)
                if (required) {
                    Text(" *", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp)
    )
}
