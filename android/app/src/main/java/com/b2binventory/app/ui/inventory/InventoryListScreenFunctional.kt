package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

enum class StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}

data class CategoryFilter(
    val name: String,
    val count: Int
)

fun getStockStatusFunc(product: Product): StockStatus {
    return when {
        product.quantity == 0 -> StockStatus.OUT_OF_STOCK
        product.quantity <= product.minimumStock -> StockStatus.LOW_STOCK
        else -> StockStatus.IN_STOCK
    }
}

fun getCategoryEmoji(category: String): String {
    return when (category.lowercase()) {
        "ply" -> "🪵"
        "mica" -> "✨"
        "hardware" -> "⚙️"
        "door" -> "🚪"
        "sanitary" -> "🚰"
        "paint" -> "🎨"
        "wood" -> "🌲"
        "glass" -> "🪟"
        "tiles" -> "🔲"
        "cement" -> "⚒️"
        "steel" -> "🔩"
        "electronics" -> "⚡"
        else -> "📦"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreenFunctional(
    businessId: Long,
    onNavigateBack: () -> Unit,
    onAddProduct: () -> Unit,
    onProductClick: (Product) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf("Name (A - Z)") }
    var viewMode by remember { mutableStateOf("List") }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    // Fetch products from backend
    fun fetchProducts() {
        loading = true
        errorMessage = ""
        
        scope.launch {
            try {
                val fetchedProducts = ApiClient.apiService.products(
                    businessId = businessId,
                    category = selectedCategory,
                    search = if (searchQuery.isBlank()) null else searchQuery
                )
                products = fetchedProducts
            } catch (e: Exception) {
                errorMessage = "Failed to load products: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    
    // Load data initially and when filters change
    LaunchedEffect(Unit) {
        fetchProducts()
    }
    
    LaunchedEffect(selectedCategory, searchQuery) {
        fetchProducts()
    }
    
    // Calculate categories with counts
    val categories = products.groupBy { it.category }
    val categoryFilters = listOf(
        CategoryFilter("All", products.size)
    ) + categories.map { (category, prods) ->
        CategoryFilter(category, prods.size)
    }
    
    // Apply sorting
    val sortedProducts = when (sortBy) {
        "Name (A - Z)" -> products.sortedBy { it.name }
        "Name (Z - A)" -> products.sortedByDescending { it.name }
        "Stock (High - Low)" -> products.sortedByDescending { it.quantity }
        "Stock (Low - High)" -> products.sortedBy { it.quantity }
        else -> products
    }
    
    val filteredProducts = if (selectedCategory == null || selectedCategory == "All") {
        sortedProducts
    } else {
        sortedProducts.filter { it.category == selectedCategory }
    }
    
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProduct,
                containerColor = Color(0xFF2196F3),
                contentColor = Color.White,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Product",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
        ) {
            // Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Inventory",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                            Text(
                                "${filteredProducts.size} products",
                                fontSize = 13.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        
                        IconButton(
                            onClick = { fetchProducts() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (loading) Color(0xFF2196F3) else Color(0xFF666666)
                            )
                        }
                    }
                }
            }
            
            // Content
            if (loading && products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF2196F3))
                }
            } else if (errorMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(errorMessage, color = Color.Red)
                        Button(
                            onClick = { fetchProducts() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search Bar
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { 
                                Text(
                                    "Search products by name, SKU, brand...",
                                    color = Color(0xFF999999),
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF666666)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF666666)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2196F3),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    
                    // Filter Chips Row
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categoryFilters) { filter ->
                                val isSelected = if (filter.name == "All") {
                                    selectedCategory == null || selectedCategory == "All"
                                } else {
                                    selectedCategory == filter.name
                                }
                                
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategory = if (filter.name == "All") null else filter.name
                                    },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (filter.name != "All") {
                                                Text(
                                                    getCategoryEmoji(filter.name),
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Text(
                                                filter.name,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            Text(
                                                "(${filter.count})",
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF999999)
                                            )
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF2196F3),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White,
                                        labelColor = Color(0xFF333333)
                                    )
                                )
                            }
                        }
                    }
                    
                    // Sort and View Mode Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { showSortMenu = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Sort,
                                            contentDescription = null,
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            sortBy,
                                            fontSize = 13.sp,
                                            color = Color(0xFF333333)
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF666666)
                                        )
                                    }
                                }
                                
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    listOf(
                                        "Name (A - Z)",
                                        "Name (Z - A)",
                                        "Stock (High - Low)",
                                        "Stock (Low - High)"
                                    ).forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option, fontSize = 14.sp) },
                                            onClick = {
                                                sortBy = option
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { viewMode = "List" },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (viewMode == "List") Color(0xFF2196F3) else Color.White,
                                            RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = "List View",
                                        tint = if (viewMode == "List") Color.White else Color(0xFF666666)
                                    )
                                }
                                IconButton(
                                    onClick = { viewMode = "Grid" },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (viewMode == "Grid") Color(0xFF2196F3) else Color.White,
                                            RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.GridView,
                                        contentDescription = "Grid View",
                                        tint = if (viewMode == "Grid") Color.White else Color(0xFF666666)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Products List
                    if (filteredProducts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color(0xFFCCCCCC)
                                    )
                                    Text(
                                        "No products found",
                                        fontSize = 16.sp,
                                        color = Color(0xFF999999)
                                    )
                                    Button(
                                        onClick = onAddProduct,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2196F3)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Product")
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredProducts) { product ->
                            ProductCardImproved(
                                product = product,
                                onClick = { onProductClick(product) }
                            )
                        }
                    }
                    
                    // Bottom Spacer for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCardImproved(
    product: Product,
    onClick: () -> Unit
) {
    val status = getStockStatusFunc(product)
    val statusColor = when (status) {
        StockStatus.IN_STOCK -> Color(0xFF4CAF50)
        StockStatus.LOW_STOCK -> Color(0xFFFFA726)
        StockStatus.OUT_OF_STOCK -> Color(0xFFF44336)
    }
    val statusText = when (status) {
        StockStatus.IN_STOCK -> "In Stock"
        StockStatus.LOW_STOCK -> "Low Stock"
        StockStatus.OUT_OF_STOCK -> "Out of Stock"
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Product Icon
            Surface(
                modifier = Modifier.size(56.dp),
                color = Color(0xFFF5F5F5),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        getCategoryEmoji(product.category),
                        fontSize = 28.sp
                    )
                }
            }
            
            // Product Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF2196F3).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            product.category,
                            fontSize = 11.sp,
                            color = Color(0xFF2196F3),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (!product.sku.isNullOrBlank()) {
                        Text(
                            "SKU: ${product.sku}",
                            fontSize = 11.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            statusText,
                            fontSize = 11.sp,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (status == StockStatus.LOW_STOCK) {
                        Text(
                            "Min: ${product.minimumStock}",
                            fontSize = 11.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
            }
            
            // Stock Quantity
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${product.quantity}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    product.unit,
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}
