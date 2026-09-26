package com.b2binventory.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

data class DashboardStats(
    val totalProducts: Int,
    val totalStock: Int,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val totalProductsChange: Double = 0.0,
    val totalStockChange: Double = 0.0,
    val lowStockChange: Double = 0.0,
    val outOfStockChange: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenFunctional(
    businessId: Long,
    userId: Long,
    businessName: String,
    onNavigateToInventory: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToParties: () -> Unit,
    onAddProduct: () -> Unit
) {
    val userName = "Mohd Ajeej"
    val userRole = "Admin"
    val location = "ABC Traders\nDistributor | Noida, Uttar Pradesh"
    
    var stats by remember { mutableStateOf<DashboardStats?>(null) }
    var recentProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var topCategories by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    // Fetch dashboard data
    fun fetchDashboardData() {
        loading = true
        errorMessage = ""
        
        scope.launch {
            try {
                // Fetch all products to calculate stats
                val products = ApiClient.apiService.products(
                    businessId = businessId
                )
                
                // Calculate stats from products
                val totalProducts = products.size
                val totalStock = products.sumOf { it.quantity }
                val lowStockCount = products.count { it.quantity > 0 && it.quantity <= it.minimumStock }
                val outOfStockCount = products.count { it.quantity == 0 }
                
                stats = DashboardStats(
                    totalProducts = totalProducts,
                    totalStock = totalStock,
                    lowStockCount = lowStockCount,
                    outOfStockCount = outOfStockCount,
                    totalProductsChange = 12.0,  // Mock change for now
                    totalStockChange = 6.0,
                    lowStockChange = -5.0,
                    outOfStockChange = -2.0
                )
                
                // Get recent products (last 4)
                recentProducts = products.sortedByDescending { it.id ?: 0 }.take(4)
                
                // Calculate top categories
                topCategories = products
                    .groupBy { it.category }
                    .mapValues { it.value.size }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(6)
                    .toMap()
                
                android.util.Log.d("Dashboard", "Stats loaded: $stats")
                
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error fetching dashboard data", e)
                errorMessage = "Failed to load dashboard: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    
    // Load data on first launch
    LaunchedEffect(Unit) {
        fetchDashboardData()
    }
    
    // Get stock status
    fun getStockStatus(product: Product): Pair<String, Color> {
        return when {
            product.quantity == 0 -> "Out of Stock" to Color(0xFFF44336)
            product.quantity <= product.minimumStock -> "Low Stock" to Color(0xFFFFA726)
            else -> "In Stock" to Color(0xFF4CAF50)
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        Surface(
                            modifier = Modifier.size(48.dp),
                            color = Color(0xFF2196F3),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(text = "📦", fontSize = 24.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "B2B",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2196F3)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Inventory",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                            Text(
                                text = "MANAGEMENT",
                                fontSize = 8.sp,
                                color = Color(0xFF666666),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Manage Today. Grow Tomorrow.",
                                fontSize = 9.sp,
                                color = Color(0xFF999999)
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = { fetchDashboardData() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (loading) Color(0xFF2196F3) else Color(0xFF666666)
                            )
                        }
                        
                        Box {
                            IconButton(onClick = { }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF666666)
                                )
                            }
                            if (stats != null && (stats!!.lowStockCount + stats!!.outOfStockCount) > 0) {
                                Surface(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .offset(x = 8.dp, y = 8.dp),
                                    color = Color(0xFFF44336),
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${stats!!.lowStockCount + stats!!.outOfStockCount}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { }
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = Color(0xFF2196F3),
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "A",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = userName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A1A1A)
                                )
                                Text(
                                    text = userRole,
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF666666),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // Main content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F7FA)),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Loading State
                if (loading && stats == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF2196F3))
                                Text(
                                    text = "Loading dashboard...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
                
                // Error Message
                if (errorMessage.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = errorMessage,
                                        color = Color(0xFFC62828),
                                        fontSize = 14.sp
                                    )
                                }
                                TextButton(onClick = { fetchDashboardData() }) {
                                    Text("Retry", color = Color(0xFFF44336))
                                }
                            }
                        }
                    }
                }
                
                // Show content only if stats are loaded
                if (stats != null) {
                    // Welcome Card
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 4.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF1976D2),
                                                Color(0xFF2196F3)
                                            )
                                        )
                                    )
                                    .padding(20.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Good Morning,",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = userName,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "👋", fontSize = 28.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = location,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "\"Better Inventory. Stronger Business.\"",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 20.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Stock\nTrack\nManage\nGrow",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.4f),
                                            lineHeight = 20.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Stats Cards Grid
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatsCardItem(
                                    title = "Total Products",
                                    value = stats!!.totalProducts.toString(),
                                    change = stats!!.totalProductsChange,
                                    icon = Icons.Default.Inventory2,
                                    iconColor = Color(0xFF2196F3),
                                    modifier = Modifier.weight(1f)
                                )
                                StatsCardItem(
                                    title = "Total Stock",
                                    value = stats!!.totalStock.toString(),
                                    change = stats!!.totalStockChange,
                                    icon = Icons.Default.Layers,
                                    iconColor = Color(0xFF4CAF50),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatsCardItem(
                                    title = "Low Stock",
                                    value = stats!!.lowStockCount.toString(),
                                    change = stats!!.lowStockChange,
                                    icon = Icons.Default.Warning,
                                    iconColor = Color(0xFFFFA726),
                                    modifier = Modifier.weight(1f)
                                )
                                StatsCardItem(
                                    title = "Out of Stock",
                                    value = stats!!.outOfStockCount.toString(),
                                    change = stats!!.outOfStockChange,
                                    icon = Icons.Default.ErrorOutline,
                                    iconColor = Color(0xFFF44336),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    
                    // Stock Status Section
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Stock Overview",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    TextButton(onClick = onNavigateToInventory) {
                                        Text(
                                            text = "View All",
                                            fontSize = 13.sp,
                                            color = Color(0xFF2196F3)
                                        )
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(120.dp),
                                            shape = CircleShape,
                                            color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                        ) {}
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stats!!.totalProducts.toString(),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1A1A1A)
                                            )
                                            Text(
                                                text = "Products",
                                                fontSize = 12.sp,
                                                color = Color(0xFF666666)
                                            )
                                        }
                                    }
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        val healthyStock = stats!!.totalProducts - stats!!.lowStockCount - stats!!.outOfStockCount
                                        val healthyPercent = if (stats!!.totalProducts > 0) 
                                            (healthyStock * 100) / stats!!.totalProducts else 0
                                        val lowPercent = if (stats!!.totalProducts > 0) 
                                            (stats!!.lowStockCount * 100) / stats!!.totalProducts else 0
                                        val outPercent = if (stats!!.totalProducts > 0) 
                                            (stats!!.outOfStockCount * 100) / stats!!.totalProducts else 0
                                        
                                        StockLegendItem("Healthy Stock", "$healthyPercent%", Color(0xFF4CAF50))
                                        StockLegendItem("Low Stock", "$lowPercent%", Color(0xFFFFA726))
                                        StockLegendItem("Out of Stock", "$outPercent%", Color(0xFFF44336))
                                    }
                                }
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    
                    // Top Categories
                    if (topCategories.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "Top Categories",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    topCategories.entries.forEachIndexed { index, (category, count) ->
                                        val percentage = if (stats!!.totalProducts > 0) 
                                            (count * 100) / stats!!.totalProducts else 0
                                        val colors = listOf(
                                            Color(0xFF2196F3),
                                            Color(0xFF9C27B0),
                                            Color(0xFFFF9800),
                                            Color(0xFF00BCD4),
                                            Color(0xFF4CAF50),
                                            Color(0xFF8BC34A)
                                        )
                                        CategoryBarItem(category, percentage, colors[index % colors.size])
                                        if (index < topCategories.size - 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                        
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                    
                    // Quick Actions
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = "Quick Actions",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionItem(Icons.Default.Add, "Add\nProduct", Color(0xFF2196F3), onAddProduct)
                                QuickActionItem(Icons.Default.SwapVert, "Adjust\nStock", Color(0xFF4CAF50), { })
                                QuickActionItem(Icons.Default.Description, "View\nReports", Color(0xFF9C27B0), onNavigateToReports)
                                QuickActionItem(Icons.Default.People, "Manage\nParties", Color(0xFFFFA726), onNavigateToParties)
                                QuickActionItem(Icons.Default.Category, "Categories", Color(0xFF00BCD4), onNavigateToInventory)
                                QuickActionItem(Icons.Default.Settings, "Settings", Color(0xFF607D8B), { })
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    
                    // Alert Banner
                    if (stats!!.lowStockCount + stats!!.outOfStockCount > 0) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clickable { onNavigateToInventory() },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFEBEE)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = Color(0xFFF44336).copy(alpha = 0.15f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFF44336),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${stats!!.lowStockCount + stats!!.outOfStockCount} Products Need Attention",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFC62828)
                                        )
                                        Text(
                                            text = "${stats!!.lowStockCount} low stock and ${stats!!.outOfStockCount} out of stock items.",
                                            fontSize = 12.sp,
                                            color = Color(0xFFD32F2F)
                                        )
                                    }
                                    
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFFF44336)
                                    )
                                }
                            }
                        }
                        
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                    
                    // Recent Products
                    if (recentProducts.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Products",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    TextButton(onClick = onNavigateToInventory) {
                                        Text(
                                            text = "View All",
                                            fontSize = 13.sp,
                                            color = Color(0xFF2196F3)
                                        )
                                    }
                                }
                            }
                        }
                        
                        items(recentProducts) { product ->
                            val status = getStockStatus(product)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable { },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF5F5F5)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = Color(0xFF999999),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1A1A1A)
                                        )
                                        Text(
                                            text = "${product.brand ?: product.category} | ${product.sku ?: ""}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF666666)
                                        )
                                    }
                                    
                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            color = status.second.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(status.second)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = status.first,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = status.second
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${product.quantity} ${product.unit}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1A1A1A)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFFCCCCCC),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCardItem(
    title: String,
    value: String,
    change: Double,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = iconColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (change >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (change >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${if (change >= 0) "+" else ""}${change.toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (change >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "vs last month",
                        fontSize = 10.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
    }
}

