package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreenFunctional(
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit,
    onProductSaved: () -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sizeDimensions by remember { mutableStateOf("") }
    var thickness by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var unitPrice by remember { mutableStateOf("") }
    var minimumStock by remember { mutableStateOf("") }
    var initialStock by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var unitTypeExpanded by remember { mutableStateOf(false) }
    
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    val categories = listOf("Ply", "Mica", "Hardware", "Door", "Paint", "Sanitary", "Wood", "Glass", "Tiles", "Cement", "Steel", "Electronics")
    val unitTypes = listOf("Piece", "Box", "Carton", "Kg", "Gram", "Meter", "Sq.Ft", "Sq.Meter", "Liter", "Set")
    
    // Validation function
    fun validateForm(): String? {
        if (productName.isBlank()) return "Product name is required"
        if (category.isBlank()) return "Category is required"
        if (sku.isBlank()) return "SKU is required"
        if (unitType.isBlank()) return "Unit type is required"
        if (initialStock.isBlank()) return "Initial stock is required"
        if (minimumStock.isBlank()) return "Minimum stock is required"
        
        initialStock.toIntOrNull() ?: return "Initial stock must be a valid number"
        minimumStock.toIntOrNull() ?: return "Minimum stock must be a valid number"
        
        return null
    }
    
    // Save product function
    fun saveProduct() {
        val validationError = validateForm()
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        
        loading = true
        errorMessage = ""
        
        scope.launch {
            try {
                val product = Product(
                    category = category,
                    name = productName,
                    brand = brand.ifBlank { null },
                    sku = sku,
                    barcode = barcode.ifBlank { null },
                    unit = unitType,
                    size = sizeDimensions.ifBlank { null },
                    thickness = thickness.ifBlank { null },
                    color = color.ifBlank { null },
                    model = model.ifBlank { null },
                    description = description.ifBlank { null },
                    quantity = initialStock.toInt(),
                    minimumStock = minimumStock.toInt()
                )
                
                val response = ApiClient.apiService.createProduct(
                    businessId = businessId,
                    userId = userId,
                    product = product
                )
                
                android.util.Log.d("AddProduct", "Product created: $response")
                
                loading = false
                showSuccessDialog = true
                
                // Clear form after 2 seconds and navigate back
                kotlinx.coroutines.delay(2000)
                onProductSaved()
                
            } catch (e: Exception) {
                android.util.Log.e("AddProduct", "Error creating product", e)
                errorMessage = "Failed to save product: ${e.message}"
                loading = false
            }
        }
    }
    
    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Product Added Successfully!",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("$productName has been added to your inventory.")
            },
            confirmButton = {}
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F7FA))
        ) {
            // Top Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF333333)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Logo
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = Color(0xFF2196F3),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(text = "📦", fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "B2B",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2196F3)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Inventory",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                            Text(
                                text = "MANAGEMENT",
                                fontSize = 7.sp,
                                color = Color(0xFF666666),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
            
            // Main Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                item {
                    Column {
                        Text(
                            text = "Add Product",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                        Text(
                            text = "Fill in the details to add a new product to your inventory.",
                            fontSize = 13.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
                
                // Error Message
                if (errorMessage.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = errorMessage,
                                    color = Color(0xFFC62828),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                
                // Product Information Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Product Information",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        text = "Enter basic details about your product.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Product Name
                            Text(
                                text = "Product Name *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = productName,
                                onValueChange = { productName = it; errorMessage = "" },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter product name (e.g. Green Ply)", fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Category
                            Text(
                                text = "Category *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ExposedDropdownMenuBox(
                                expanded = categoryExpanded,
                                onExpandedChange = { categoryExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = category,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    placeholder = { Text("Select category", fontSize = 14.sp) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF666666)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
                                ) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                category = cat
                                                categoryExpanded = false
                                                errorMessage = ""
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Brand
                            Text(
                                text = "Brand",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter brand name (e.g. Century Ply)", fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // SKU
                            Text(
                                text = "SKU *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = sku,
                                onValueChange = { sku = it; errorMessage = "" },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter SKU (e.g. PLY-001)", fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Barcode
                            Text(
                                text = "Barcode (Optional)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter barcode number", fontSize = 14.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan",
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.clickable { }
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Description
                            Text(
                                text = "Description",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = description,
                                onValueChange = { if (it.length <= 500) description = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                placeholder = { Text("Enter product description, features, etc...", fontSize = 14.sp) },
                                maxLines = 4,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            Text(
                                text = "${description.length}/500",
                                fontSize = 11.sp,
                                color = Color(0xFF999999),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
                
                // Product Specifications Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Product Specifications",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        text = "Add product specifications (e.g. size, model, color).",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Size / Dimensions",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF333333)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = sizeDimensions,
                                        onValueChange = { sizeDimensions = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. 8 x 4 ft", fontSize = 14.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        )
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Thickness",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF333333)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = thickness,
                                        onValueChange = { thickness = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. 18 mm", fontSize = 14.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        )
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Model (Optional)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF333333)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = model,
                                        onValueChange = { model = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. H-200", fontSize = 14.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        )
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Color (Optional)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF333333)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = color,
                                        onValueChange = { color = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. Brown", fontSize = 14.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Pricing & Stock Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Stock Details",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        text = "Set stock details.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Initial Stock
                            Text(
                                text = "Initial Stock *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = initialStock,
                                onValueChange = { 
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        initialStock = it
                                        errorMessage = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter initial stock quantity", fontSize = 14.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Minimum Stock
                            Text(
                                text = "Minimum Stock *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = minimumStock,
                                onValueChange = { 
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        minimumStock = it
                                        errorMessage = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter minimum stock level", fontSize = 14.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Unit Type
                            Text(
                                text = "Unit Type *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ExposedDropdownMenuBox(
                                expanded = unitTypeExpanded,
                                onExpandedChange = { unitTypeExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = unitType,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    placeholder = { Text("Select unit type", fontSize = 14.sp) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF666666)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = unitTypeExpanded,
                                    onDismissRequest = { unitTypeExpanded = false }
                                ) {
                                    unitTypes.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit) },
                                            onClick = {
                                                unitType = unit
                                                unitTypeExpanded = false
                                                errorMessage = ""
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Bottom spacing for action buttons
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            
            // Bottom Action Buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = !loading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2196F3)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2196F3))
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Button(
                        onClick = { saveProduct() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = !loading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save Product",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
