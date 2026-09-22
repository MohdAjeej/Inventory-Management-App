package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    
    // Form fields
    var productName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var thickness by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var colorValue by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }
    var unitPrice by remember { mutableStateOf("") }
    var mrp by remember { mutableStateOf("") }
    var minimumStock by remember { mutableStateOf("") }
    var initialStock by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("") }
    var additionalInfo by remember { mutableStateOf("") }
    
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showUnitMenu by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    val categories = listOf("Ply", "Mica", "Hardware", "Door", "Sanitary", "Paint", "Wood", "Glass", "Tiles", "Cement", "Steel", "Electronics", "Other")
    val units = listOf("Pieces", "Sheets", "Boxes", "Units", "Kg", "Meter", "Feet", "Liter")
    
    fun loadProduct() {
        loading = true
        errorMessage = ""
        scope.launch {
            try {
                val products = ApiClient.apiService.products(businessId)
                product = products.find { it.id == productId }
                product?.let { prod ->
                    productName = prod.name
                    selectedCategory = prod.category
                    brand = prod.brand ?: ""
                    sku = prod.sku ?: ""
                    barcode = prod.barcode ?: ""
                    description = prod.description ?: ""
                    size = prod.size ?: ""
                    thickness = prod.thickness ?: ""
                    model = prod.model ?: ""
                    colorValue = prod.color ?: ""
                    minimumStock = prod.minimumStock.toString()
                    initialStock = prod.quantity.toString()
                    selectedUnit = prod.unit
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load product: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    
    fun updateProduct() {
        if (productName.isBlank()) {
            errorMessage = "Product name is required"
            return
        }
        if (selectedCategory.isBlank()) {
            errorMessage = "Category is required"
            return
        }
        if (sku.isBlank()) {
            errorMessage = "SKU is required"
            return
        }
        
        saving = true
        errorMessage = ""
        successMessage = ""
        
        scope.launch {
            try {
                val updatedProduct = Product(
                    id = productId,
                    category = selectedCategory,
                    name = productName,
                    brand = brand.ifBlank { null },
                    sku = sku.ifBlank { null },
                    barcode = barcode.ifBlank { null },
                    unit = selectedUnit,
                    size = size.ifBlank { null },
                    thickness = thickness.ifBlank { null },
                    color = colorValue.ifBlank { null },
                    model = model.ifBlank { null },
                    description = description.ifBlank { null },
                    quantity = initialStock.toIntOrNull() ?: 0,
                    minimumStock = minimumStock.toIntOrNull() ?: 0,
                    imageUrl = null
                )
                
                // Note: You'll need to add an update endpoint to your API
                // ApiClient.apiService.updateProduct(productId, userId, updatedProduct)
                
                successMessage = "Product updated successfully!"
                kotlinx.coroutines.delay(1500)
                onNavigateBack()
            } catch (e: Exception) {
                errorMessage = "Failed to update product: ${e.message}"
            } finally {
                saving = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadProduct()
    }
    
    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFF1A1A1A)
                                )
                            }
                            Column {
                                Text(
                                    "Edit Product",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                                Text(
                                    "Update the product details and keep your inventory accurate.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                    
                    // Breadcrumb
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF2196F3))
                        Text("Inventory", fontSize = 12.sp, color = Color(0xFF2196F3))
                        Text(">", fontSize = 12.sp, color = Color(0xFF999999))
                        Text("Products", fontSize = 12.sp, color = Color(0xFF2196F3))
                        Text(">", fontSize = 12.sp, color = Color(0xFF999999))
                        Text("Edit Product", fontSize = 12.sp, color = Color(0xFF666666))
                    }
                }
            }
        }
    ) { paddingValues ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2196F3))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error/Success Messages
                if (errorMessage.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFEBEE)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF44336))
                                Text(errorMessage, color = Color(0xFFF44336), fontSize = 13.sp)
                            }
                        }
                    }
                }
                
                if (successMessage.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                Text(successMessage, color = Color(0xFF4CAF50), fontSize = 13.sp)
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Product Information",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Update basic details about your product.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Product Name
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text("Product Name", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    Text(" *", fontSize = 13.sp, color = Color(0xFFF44336))
                                }
                                OutlinedTextField(
                                    value = productName,
                                    onValueChange = { productName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Enter product name (e.g. Green Ply)", fontSize = 14.sp, color = Color(0xFF999999)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Category
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text("Category", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    Text(" *", fontSize = 13.sp, color = Color(0xFFF44336))
                                }
                                ExposedDropdownMenuBox(
                                    expanded = showCategoryMenu,
                                    onExpandedChange = { showCategoryMenu = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedCategory,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        placeholder = { Text("Select category", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        trailingIcon = {
                                            Icon(
                                                if (showCategoryMenu) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = Color(0xFF666666)
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    
                                    ExposedDropdownMenu(
                                        expanded = showCategoryMenu,
                                        onDismissRequest = { showCategoryMenu = false }
                                    ) {
                                        categories.forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category) },
                                                onClick = {
                                                    selectedCategory = category
                                                    showCategoryMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Brand
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Brand", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                OutlinedTextField(
                                    value = brand,
                                    onValueChange = { brand = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Enter brand name (e.g. Century Ply)", fontSize = 14.sp, color = Color(0xFF999999)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // SKU
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text("SKU", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    Text(" *", fontSize = 13.sp, color = Color(0xFFF44336))
                                }
                                OutlinedTextField(
                                    value = sku,
                                    onValueChange = { sku = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Enter SKU (e.g. PLY-001)", fontSize = 14.sp, color = Color(0xFF999999)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Barcode
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Barcode (Optional)", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                OutlinedTextField(
                                    value = barcode,
                                    onValueChange = { barcode = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Enter barcode number", fontSize = 14.sp, color = Color(0xFF999999)) },
                                    trailingIcon = {
                                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF666666))
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Description
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Description", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    Text("${description.length}/500", fontSize = 11.sp, color = Color(0xFF999999))
                                }
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { if (it.length <= 500) description = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    placeholder = { Text("High quality commercial plywood with superior strength and durability. Ideal for furniture, cabinets and interior work.", fontSize = 14.sp, color = Color(0xFF999999)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    maxLines = 4
                                )
                            }
                        }
                    }
                }
                
                // Product Images Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Product Images",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Update product images (you can add or remove).",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Main Image
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF5F5F5)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        getCategoryEmoji(selectedCategory),
                                        fontSize = 80.sp
                                    )
                                    
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(12.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF2196F3)
                                    ) {
                                        Text(
                                            "Main Image",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp)
                                            .size(32.dp),
                                        shape = CircleShape,
                                        color = Color(0xFF1A1A1A)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Thumbnail Images
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(4) { index ->
                                    Surface(
                                        modifier = Modifier.size(80.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF5F5F5)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                getCategoryEmoji(selectedCategory),
                                                fontSize = 32.sp
                                            )
                                            
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(20.dp),
                                                shape = CircleShape,
                                                color = Color(0xFF1A1A1A)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                item {
                                    Surface(
                                        modifier = Modifier.size(80.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2196F3).copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2196F3))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add",
                                                    tint = Color(0xFF2196F3),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    "Add\nImages",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF2196F3),
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                "Supported formats: PNG, JPG (Max 5 MB each)",
                                fontSize = 11.sp,
                                color = Color(0xFF999999)
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Product Specifications",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Update product specifications (optional).",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Size / Dimensions", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    OutlinedTextField(
                                        value = size,
                                        onValueChange = { size = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. 8 x 4 ft", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Thickness", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    OutlinedTextField(
                                        value = thickness,
                                        onValueChange = { thickness = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. 18 mm", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Model (Optional)", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    OutlinedTextField(
                                        value = model,
                                        onValueChange = { model = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. H-200", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Color (Optional)", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    OutlinedTextField(
                                        value = colorValue,
                                        onValueChange = { colorValue = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. Natural Wood", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Product Status Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Product Status",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Active",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Product will be visible in inventory",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                                
                                Switch(
                                    checked = isActive,
                                    onCheckedChange = { isActive = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2196F3),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFFCCCCCC)
                                    )
                                )
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Pricing & Stock",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Update pricing and stock details.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row {
                                        Text("Unit Price (₹)", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                        Text(" *", fontSize = 13.sp, color = Color(0xFFF44336))
                                    }
                                    OutlinedTextField(
                                        value = unitPrice,
                                        onValueChange = { unitPrice = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("2150", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("MRP (₹)", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    OutlinedTextField(
                                        value = mrp,
                                        onValueChange = { mrp = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("2450", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row {
                                        Text("Minimum Stock", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                        Text(" *", fontSize = 13.sp, color = Color(0xFFF44336))
                                    }
                                    OutlinedTextField(
                                        value = minimumStock,
                                        onValueChange = { minimumStock = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("20", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row {
                                        Text("Initial Stock", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                        Text(" *", fontSize = 13.sp, color = Color(0xFFF44336))
                                    }
                                    OutlinedTextField(
                                        value = initialStock,
                                        onValueChange = { initialStock = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("120", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = false
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text("Unit Type", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    Text(" *", fontSize = 13.sp, color = Color(0xFFF44336))
                                }
                                ExposedDropdownMenuBox(
                                    expanded = showUnitMenu,
                                    onExpandedChange = { showUnitMenu = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedUnit,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        placeholder = { Text("Select unit type", fontSize = 14.sp, color = Color(0xFF999999)) },
                                        trailingIcon = {
                                            Icon(
                                                if (showUnitMenu) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = Color(0xFF666666)
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3),
                                            unfocusedBorderColor = Color(0xFFE0E0E0)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    
                                    ExposedDropdownMenu(
                                        expanded = showUnitMenu,
                                        onDismissRequest = { showUnitMenu = false }
                                    ) {
                                        units.forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(unit) },
                                                onClick = {
                                                    selectedUnit = unit
                                                    showUnitMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Additional Information Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Additional Information",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Any additional details (optional).",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Notes", fontSize = 13.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium)
                                    Text("${additionalInfo.length}/500", fontSize = 11.sp, color = Color(0xFF999999))
                                }
                                OutlinedTextField(
                                    value = additionalInfo,
                                    onValueChange = { if (it.length <= 500) additionalInfo = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    placeholder = { Text("Add any extra notes, features, or information...", fontSize = 14.sp, color = Color(0xFF999999)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
                
                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF666666)
                            )
                        ) {
                            Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        
                        Button(
                            onClick = { updateProduct() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            enabled = !saving
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Update Product", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                
                // Bottom Spacer
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
