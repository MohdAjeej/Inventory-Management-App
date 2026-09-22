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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutOfStockProductsScreen(
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToRestock: (productId: Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    
    var allProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var outOfStockProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var filteredProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf("Out of Stock") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showStatusDropdown by remember { mutableStateOf(false) }
    
    // Pagination
    var currentPage by remember { mutableStateOf(1) }
    val itemsPerPage = 10

    // Load products
    LaunchedEffect(businessId) {
        scope.launch {
            try {
                isLoading = true
                allProducts = ApiClient.apiService.products(businessId)
                // Filter for out of stock only
                outOfStockProducts = allProducts.filter { it.quantity == 0 }
                filteredProducts = outOfStockProducts
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // Statistics
    val outOfStockCount = outOfStockProducts.size
    val totalProducts = allProducts.size
    val pendingPurchaseOrders = 8 // This would come from backend
    val restockedToday = 0 // This would come from backend
    
    // Categories
    val categories = outOfStockProducts.map { it.category }.distinct().sorted()

    // Apply filters
    LaunchedEffect(searchQuery, selectedCategory, outOfStockProducts) {
        filteredProducts = outOfStockProducts.filter { product ->
            val matchesSearch = searchQuery.isEmpty() || 
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.sku?.contains(searchQuery, ignoreCase = true) == true ||
                product.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = selectedCategory == null || product.category == selectedCategory
            
            matchesSearch && matchesCategory
        }
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
                        Text("Out of Stock Products", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Products that are currently out of stock.",
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
                // Critical Alert Banner
                if (outOfStockCount > 0) {
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
                                        "Stock Unavailable",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                    Text(
                                        "$outOfStockCount products are currently out of stock. Please restock as soon as possible to avoid order delays and lost sales.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF666666),
                                        lineHeight = 18.sp
                                    )
                                }
                                Button(
                                    onClick = { /* Create purchase order */ },
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
                                    Text("Create Purchase Order", fontSize = 13.sp)
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
                        OutOfStockStatCard(
                            icon = Icons.Default.Inventory2,
                            iconColor = Color(0xFFF44336),
                            iconBgColor = Color(0xFFFFEBEE),
                            title = "Out of Stock",
                            value = outOfStockCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        OutOfStockStatCard(
                            icon = Icons.Default.ShoppingCart,
                            iconColor = Color(0xFF2196F3),
                            iconBgColor = Color(0xFFE3F2FD),
                            title = "Total Products",
                            value = totalProducts.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutOfStockStatCard(
                            icon = Icons.Default.HourglassEmpty,
                            iconColor = Color(0xFFFF9800),
                            iconBgColor = Color(0xFFFFF3E0),
                            title = "Pending Purchase Orders",
                            value = pendingPurchaseOrders.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        OutOfStockStatCard(
                            icon = Icons.Default.LocalShipping,
                            iconColor = Color(0xFF4CAF50),
                            iconBgColor = Color(0xFFE8F5E9),
                            title = "Restocked Today",
                            value = restockedToday.toString(),
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

                                // Status Filter (showing Out of Stock by default)
                                ExposedDropdownMenuBox(
                                    expanded = showStatusDropdown,
                                    onExpandedChange = { showStatusDropdown = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selectedStatus,
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
                                        expanded = showStatusDropdown,
                                        onDismissRequest = { showStatusDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Out of Stock") },
                                            onClick = {
                                                selectedStatus = "Out of Stock"
                                                showStatusDropdown = false
                                            }
                                        )
                                    }
                                }

                                // Apply Button
                                Button(
                                    onClick = { /* Already auto-applied */ },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Text("Apply", fontSize = 14.sp)
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
                            Text("Last Stock Date", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp), textAlign = TextAlign.Center)
                            Text("Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp), textAlign = TextAlign.Center)
                            Text("Action", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                        }
                    }
                }

                // Table Rows
                itemsIndexed(paginatedItems) { index, product ->
                    val globalIndex = (currentPage - 1) * itemsPerPage + index + 1
                    OutOfStockProductRow(
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
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "All products are in stock!",
                                        fontSize = 16.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "No out of stock products found",
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
                                    "Showing ${(currentPage - 1) * itemsPerPage + 1} to ${minOf(currentPage * itemsPerPage, filteredProducts.size)} of ${filteredProducts.size} products",
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

                // Info Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Why is this important?
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
                                        Icons.Default.BarChart,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Why is this important?",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Out of stock products can lead to lost sales, customer dissatisfaction, and delays in operations. Keep your inventory replenished.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { /* Configure alert settings */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Configure Alert Settings", fontSize = 13.sp)
                                }
                            }
                        }

                        // Bulk Restock
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
                                        Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Bulk Restock",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Create a purchase order for all out-of-stock items at once.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { /* Create bulk purchase order */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create Bulk Purchase Order", fontSize = 13.sp)
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
fun OutOfStockStatCard(
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
fun OutOfStockProductRow(
    index: Int,
    product: Product,
    onRestock: () -> Unit
) {
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

                // Last Stock Date (would come from backend)
                Text(
                    formatLastStockDate(),
                    fontSize = 12.sp,
                    modifier = Modifier.width(100.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.width(100.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Out of Stock",
                            fontSize = 11.sp,
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Restock Button
                Button(
                    onClick = onRestock,
                    modifier = Modifier.width(90.dp).height(32.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
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

                // More options
                IconButton(
                    onClick = { /* More options */ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
        }
    }
}

fun formatLastStockDate(): String {
    // In a real app, this would come from the product's last stock update timestamp
    // For now, we'll show some sample dates
    val days = (1..30).random()
    val instant = Instant.now().minusSeconds(days.toLong() * 24 * 3600)
    val formatter = DateTimeFormatter
        .ofPattern("dd MMM yyyy")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
