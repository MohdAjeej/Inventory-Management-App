package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LowStockProductsScreen(
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToRestock: (productId: Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    
    var allProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var filteredProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSort by remember { mutableStateOf("Low Stock First") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    
    // Pagination
    var currentPage by remember { mutableStateOf(1) }
    val itemsPerPage = 10

    // Load products
    LaunchedEffect(businessId) {
        scope.launch {
            try {
                isLoading = true
                val products = ApiClient.apiService.products(businessId)
                // Filter for low stock and out of stock
                allProducts = products.filter { 
                    it.quantity <= it.minimumStock 
                }
                filteredProducts = allProducts
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // Statistics
    val lowStockCount = allProducts.filter { it.quantity > 0 && it.quantity <= it.minimumStock }.size
    val outOfStockCount = allProducts.filter { it.quantity == 0 }.size
    val totalProducts = allProducts.size + allProducts.filter { it.quantity > it.minimumStock }.size
    val inStockCount = totalProducts - lowStockCount - outOfStockCount
    
    // Categories
    val categories = allProducts.map { it.category }.distinct().sorted()

    // Apply filters
    LaunchedEffect(searchQuery, selectedCategory, selectedSort, allProducts) {
        var result = allProducts.filter { product ->
            val matchesSearch = searchQuery.isEmpty() || 
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.sku?.contains(searchQuery, ignoreCase = true) == true ||
                product.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = selectedCategory == null || product.category == selectedCategory
            
            matchesSearch && matchesCategory
        }
        
        // Sort
        result = when (selectedSort) {
            "Low Stock First" -> result.sortedBy { it.quantity }
            "Category" -> result.sortedBy { it.category }
            "Name (A-Z)" -> result.sortedBy { it.name }
            else -> result
        }
        
        filteredProducts = result
        currentPage = 1
    }

    // Paginated items
    val paginatedItems = remember(filteredProducts, currentPage) {
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, filteredProducts.size)
        if (startIndex < filteredProducts.size) {
            filteredProducts.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    val totalPages = (filteredProducts.size + itemsPerPage - 1) / itemsPerPage

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Low Stock Products", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Products that are at or below the minimum stock level.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Alert Banner
                if (lowStockCount > 0 || outOfStockCount > 0) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFEBEE)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF44336),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Attention Required!",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                    Text(
                                        "${lowStockCount + outOfStockCount} products are at or below their minimum stock level. Please restock to avoid interruptions.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF666666),
                                        lineHeight = 18.sp
                                    )
                                }
                                Button(
                                    onClick = { /* Reorder now */ },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF44336)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reorder Now", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Statistics Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LowStockStatCard(
                            icon = Icons.Default.Warning,
                            iconColor = Color(0xFFF44336),
                            iconBgColor = Color(0xFFFFEBEE),
                            title = "Low Stock Products",
                            value = lowStockCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        LowStockStatCard(
                            icon = Icons.Default.ErrorOutline,
                            iconColor = Color(0xFFFF9800),
                            iconBgColor = Color(0xFFFFF3E0),
                            title = "Out of Stock",
                            value = outOfStockCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LowStockStatCard(
                            icon = Icons.Default.Inventory2,
                            iconColor = Color(0xFF2196F3),
                            iconBgColor = Color(0xFFE3F2FD),
                            title = "Total Products",
                            value = totalProducts.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        LowStockStatCard(
                            icon = Icons.Default.CheckCircle,
                            iconColor = Color(0xFF4CAF50),
                            iconBgColor = Color(0xFFE8F5E9),
                            title = "In Stock",
                            value = inStockCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Filters
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Search
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search by product name, SKU or category...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Category Filter
                                ExposedDropdownMenuBox(
                                    expanded = showCategoryDropdown,
                                    onExpandedChange = { showCategoryDropdown = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selectedCategory ?: "All Categories",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    ExposedDropdownMenu(
                                        expanded = showCategoryDropdown,
                                        onDismissRequest = { showCategoryDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All Categories") },
                                            onClick = {
                                                selectedCategory = null
                                                showCategoryDropdown = false
                                            }
                                        )
                                        categories.forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category) },
                                                onClick = {
                                                    selectedCategory = category
                                                    showCategoryDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Sort Filter
                                ExposedDropdownMenuBox(
                                    expanded = showSortDropdown,
                                    onExpandedChange = { showSortDropdown = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selectedSort,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    ExposedDropdownMenu(
                                        expanded = showSortDropdown,
                                        onDismissRequest = { showSortDropdown = false }
                                    ) {
                                        listOf("Low Stock First", "Category", "Name (A-Z)").forEach { sort ->
                                            DropdownMenuItem(
                                                text = { Text(sort) },
                                                onClick = {
                                                    selectedSort = sort
                                                    showSortDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Table Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Text("Product", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("SKU", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("Current Stock", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                            Text("Minimum Stock", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                            Text("Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                            Text("Action", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                        }
                    }
                }

                // Table Rows
                itemsIndexed(paginatedItems) { index, product ->
                    val globalIndex = (currentPage - 1) * itemsPerPage + index + 1
                    LowStockProductRow(
                        index = globalIndex,
                        product = product,
                        onRestock = { onNavigateToRestock(product.id ?: 0L) }
                    )
                }

                // Empty State
                if (filteredProducts.isEmpty() && !isLoading) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "All products have sufficient stock!",
                                        fontSize = 16.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "No low stock products found",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Pagination
                if (filteredProducts.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Showing ${(currentPage - 1) * itemsPerPage + 1} to ${minOf(currentPage * itemsPerPage, filteredProducts.size)} of ${filteredProducts.size} low stock products",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { if (currentPage > 1) currentPage-- },
                                        enabled = currentPage > 1
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                                    }

                                    (1..minOf(totalPages, 3)).forEach { page ->
                                        Surface(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clickable { currentPage = page },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (page == currentPage) Color(0xFF2196F3) else Color.Transparent
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    page.toString(),
                                                    color = if (page == currentPage) Color.White else Color(0xFF666666),
                                                    fontWeight = if (page == currentPage) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { if (currentPage < totalPages) currentPage++ },
                                        enabled = currentPage < totalPages
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Set Low Stock Alerts
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF2196F3),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Set Low Stock Alerts",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Get notified when products reach minimum stock level.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { /* Configure alerts */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Configure Alerts", fontSize = 13.sp)
                                }
                            }
                        }

                        // Generate Purchase Order
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Generate Purchase Order",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Create purchase orders for low stock items in bulk.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { /* Create purchase order */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create Purchase Order", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun LowStockStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconBgColor,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Column {
                Text(
                    value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Text(
                    title,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun LowStockProductRow(
    index: Int,
    product: Product,
    onRestock: () -> Unit
) {
    val isOutOfStock = product.quantity == 0
    val isLowStock = product.quantity > 0 && product.quantity <= product.minimumStock
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Index
                Text(
                    index.toString(),
                    fontSize = 14.sp,
                    modifier = Modifier.width(30.dp)
                )

                // Product with image
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF5E6D3)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = null,
                            tint = Color(0xFFD4A574),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        product.name,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }

                // SKU
                Text(
                    product.sku ?: "N/A",
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1
                )

                // Category
                Text(
                    product.category,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1
                )

                // Current Stock
                Text(
                    product.quantity.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOutOfStock) Color(0xFFF44336) else Color(0xFFFF9800),
                    modifier = Modifier.width(90.dp),
                    textAlign = TextAlign.Center
                )

                // Minimum Stock
                Text(
                    product.minimumStock.toString(),
                    fontSize = 13.sp,
                    modifier = Modifier.width(90.dp),
                    textAlign = TextAlign.Center
                )

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isOutOfStock) Color(0xFFFFEBEE) else Color(0xFFFFF3E0),
                    modifier = Modifier.width(90.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isOutOfStock) Color(0xFFF44336) else Color(0xFFFF9800),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isOutOfStock) "Out of Stock" else "Low Stock",
                            fontSize = 11.sp,
                            color = if (isOutOfStock) Color(0xFFF44336) else Color(0xFFFF9800),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Restock Button
                Button(
                    onClick = onRestock,
                    modifier = Modifier.width(80.dp).height(32.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restock", fontSize = 11.sp)
                }
            }
            
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
        }
    }
}
