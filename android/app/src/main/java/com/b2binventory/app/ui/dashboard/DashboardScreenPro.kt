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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

data class StatsCard(
    val title: String,
    val value: String,
    val change: String,
    val changePercentage: String,
    val isPositive: Boolean,
    val icon: ImageVector,
    val iconColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenPro(
    businessId: Long,
    userId: Long,
    onNavigateToInventory: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToParties: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    onAddProduct: () -> Unit,
    userName: String = "User",
    userRole: String = "Admin",
    businessName: String = "My Business"
) {
    val location = "$businessName\nDistributor | India"
    
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
                products = ApiClient.apiService.products(businessId = businessId)
                android.util.Log.d("Dashboard", "Loaded ${products.size} products")
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Error loading products", e)
                errorMessage = "Failed to load dashboard: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    
    // Load data on first launch
    LaunchedEffect(Unit) {
        fetchProducts()
    }
    
    // Calculate stats from products
    val totalProducts = products.size
    val totalStock = products.sumOf { it.quantity }
    val lowStockCount = products.count { it.quantity > 0 && it.quantity <= it.minimumStock }
    val outOfStockCount = products.count { it.quantity == 0 }
    
    val statsCards = listOf(
        StatsCard("Total Products", totalProducts.toString(), "12%", "vs last month", true, Icons.Default.Inventory2, Color(0xFF2196F3)),
        StatsCard("Total Stock", totalStock.toString(), "6%", "vs last month", true, Icons.Default.Layers, Color(0xFF4CAF50)),
        StatsCard("Low Stock", lowStockCount.toString(), "5%", "vs last month", false, Icons.Default.Warning, Color(0xFFFFA726)),
        StatsCard("Out of Stock", outOfStockCount.toString(), "2%", "vs last month", false, Icons.Default.ErrorOutline, Color(0xFFF44336))
    )
    
    val recentProducts = products.sortedByDescending { it.id ?: 0 }.take(4)
    
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
                        // Logo
                        Surface(
                            modifier = Modifier.size(48.dp),
                            color = Color(0xFF2196F3),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "📦",
                                    fontSize = 24.sp
                                )
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
                        // Refresh button
                        IconButton(onClick = { fetchProducts() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (loading) Color(0xFF2196F3) else Color(0xFF666666)
                            )
                        }
                        
                        // Notification with badge
                        Box {
                            IconButton(onClick = { }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF666666)
                                )
                            }
                            if (lowStockCount + outOfStockCount > 0) {
                                Surface(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .offset(x = 8.dp, y = 8.dp),
                                    color = Color(0xFFF44336),
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${lowStockCount + outOfStockCount}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "3",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        // User profile
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
            
            // Main content with gradient background
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F7FA)),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Loading State
                if (loading && products.isEmpty()) {
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
                    return@LazyColumn
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
                                TextButton(onClick = { fetchProducts() }) {
                                    Text("Retry", color = Color(0xFFF44336))
                                }
                            }
                        }
                    }
                }
                
                // Welcome Card with Gradient
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
                                    Text(
                                        text = "👋",
                                        fontSize = 28.sp
                                    )
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
                            
                            // Warehouse illustration text overlay
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = Color.White.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Your Business\nOur Support\nA Better\nTomorrow",
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            lineHeight = 12.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
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
                            StatsCardItem(statsCards[0], Modifier.weight(1f))
                            StatsCardItem(statsCards[1], Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatsCardItem(statsCards[2], Modifier.weight(1f))
                            StatsCardItem(statsCards[3], Modifier.weight(1f))
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
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
                                    text = "Stock Status",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                                TextButton(onClick = { }) {
                                    Text(
                                        text = "View Details",
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
                            
                            // Simplified pie chart representation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mock pie chart
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
                                            text = "1,248",
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
                                    StockLegendItem("Healthy Stock", "78%", Color(0xFF4CAF50))
                                    StockLegendItem("Low Stock", "15%", Color(0xFFFFA726))
                                    StockLegendItem("Out of Stock", "7%", Color(0xFFF44336))
                                }
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Top Categories Section (Horizontal with colored bars)
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
                                    text = "Top Categories",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                                TextButton(onClick = { }) {
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
                            
                            CategoryBarItem("Ply", 28, Color(0xFF2196F3))
                            CategoryBarItem("Wood", 18, Color(0xFF9C27B0))
                            CategoryBarItem("Mica", 15, Color(0xFFFF9800))
                            CategoryBarItem("Door", 12, Color(0xFF00BCD4))
                            CategoryBarItem("Paint", 10, Color(0xFF4CAF50))
                            CategoryBarItem("Sanitary", 8, Color(0xFF8BC34A))
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Quick Actions
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick Actions",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                            TextButton(onClick = { }) {
                                Text(
                                    text = "See All",
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
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickActionItem(Icons.Default.Inventory, "Inventory", Color(0xFF2196F3), onNavigateToInventory)
                            QuickActionItem(Icons.Default.Add, "Add Product", Color(0xFF4CAF50), onAddProduct)
                            QuickActionItem(Icons.Default.Category, "Categories", Color(0xFFE91E63), onNavigateToCategories)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickActionItem(Icons.Default.SwapVert, "Adjust Stock", Color(0xFFFFA726), { })
                            QuickActionItem(Icons.Default.Description, "Reports", Color(0xFF9C27B0), onNavigateToReports)
                            QuickActionItem(Icons.Default.People, "Parties", Color(0xFF00BCD4), onNavigateToParties)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickActionItem(Icons.Default.Settings, "Settings", Color(0xFF607D8B), onNavigateToSettings)
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Alert Banner
                if (lowStockCount + outOfStockCount > 0) {
                    item {
                        Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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
                                    text = "${lowStockCount + outOfStockCount} Products Need Attention",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                                Text(
                                    text = "$lowStockCount low stock items and $outOfStockCount out of stock items require your attention.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            TextButton(
                                onClick = onNavigateToInventory,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFFF44336)
                                )
                            ) {
                                Text(
                                    text = "View Alerts",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
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
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                
                items(recentProducts) { product ->
                    ProductListItem(product)
                }
            }
        }
    }
}
@Composable
fun StatsCardItem(stats: StatsCard, modifier: Modifier = Modifier) {
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = stats.iconColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            stats.icon,
                            contentDescription = null,
                            tint = stats.iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            
            Column {
                Text(
                    text = stats.title,
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stats.value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (stats.isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (stats.isPositive) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${if (stats.isPositive) "+" else ""}${stats.changePercentage}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (stats.isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stats.change,
                        fontSize = 10.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
    }
}

@Composable
fun StockLegendItem(label: String, percentage: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(12.dp),
            color = color,
            shape = CircleShape
        ) {}
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF666666),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = percentage,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun CategoryBarItem(name: String, percentage: Int, color: Color) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Category,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = name,
                    fontSize = 14.sp,
                    color = Color(0xFF333333)
                )
            }
            Text(
                text = "$percentage%",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF0F0F0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun QuickActionItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(60.dp)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(14.dp),
            color = color.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF333333),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

@Composable
fun ProductListItem(product: Product) {
    val status = when {
        product.quantity == 0 -> "Out of Stock" to Color(0xFFF44336)
        product.quantity <= product.minimumStock -> "Low Stock" to Color(0xFFFFA726)
        else -> "In Stock" to Color(0xFF4CAF50)
    }
    
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
            // Product icon
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
