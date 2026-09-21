package com.b2binventory.app.ui.analytics

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    businessId: Long,
    userId: Long
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    fun fetchData() {
        loading = true
        errorMessage = ""
        scope.launch {
            try {
                products = ApiClient.apiService.products(businessId = businessId)
            } catch (e: Exception) {
                errorMessage = "Failed to load analytics: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        fetchData()
    }
    
    // Calculate analytics
    val totalProducts = products.size
    val totalStock = products.sumOf { it.quantity }
    val lowStockCount = products.count { it.quantity > 0 && it.quantity <= it.minimumStock }
    val outOfStockCount = products.count { it.quantity == 0 }
    val categories = products.groupBy { it.category }
    val topCategories = categories.entries.sortedByDescending { it.value.size }.take(5)
    val topProducts = products.sortedByDescending { it.quantity }.take(5)
    
    Scaffold(
        topBar = {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = Color(0xFF2196F3),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Analytics", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Business insights", fontSize = 12.sp, color = Color(0xFF666666))
                            }
                        }
                        
                        IconButton(onClick = { fetchData() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (loading) Color(0xFF2196F3) else Color(0xFF666666)
                            )
                        }
                    }
                    Divider(color = Color(0xFFEEEEEE))
                }
            }
        }
    ) { padding ->
        if (loading && products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2196F3))
            }
        } else if (errorMessage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage, color = Color.Red)
                    Button(onClick = { fetchData() }) {
                        Text("Retry")
                    }
                }
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
                // Overview Cards
                item {
                    Text(
                        "Overview",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnalyticsCard(
                            title = "Total Products",
                            value = totalProducts.toString(),
                            icon = Icons.Default.Inventory,
                            color = Color(0xFF2196F3),
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsCard(
                            title = "Total Stock",
                            value = totalStock.toString(),
                            icon = Icons.Default.Layers,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnalyticsCard(
                            title = "Low Stock",
                            value = lowStockCount.toString(),
                            icon = Icons.Default.Warning,
                            color = Color(0xFFFFA726),
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsCard(
                            title = "Out of Stock",
                            value = outOfStockCount.toString(),
                            icon = Icons.Default.ErrorOutline,
                            color = Color(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Top Categories
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Top Categories",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                
                items(topCategories) { (category, prods) ->
                    CategoryAnalyticsItem(
                        category = category,
                        productCount = prods.size,
                        totalStock = prods.sumOf { it.quantity }
                    )
                }
                
                // Top Products by Stock
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Top Products by Stock",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                
                items(topProducts) { product ->
                    ProductAnalyticsItem(product = product)
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun AnalyticsCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Text(title, fontSize = 12.sp, color = Color(0xFF666666))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        }
    }
}

@Composable
fun CategoryAnalyticsItem(category: String, productCount: Int, totalStock: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFF2196F3).copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(category, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("$productCount products", fontSize = 12.sp, color = Color(0xFF666666))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$totalStock",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                Text("Total Stock", fontSize = 11.sp, color = Color(0xFF666666))
            }
        }
    }
}

@Composable
fun ProductAnalyticsItem(product: Product) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(product.category, fontSize = 12.sp, color = Color(0xFF666666))
                Text("SKU: ${product.sku ?: "N/A"}", fontSize = 11.sp, color = Color(0xFF999999))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${product.quantity}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                Text("${product.unit}", fontSize = 12.sp, color = Color(0xFF666666))
            }
        }
    }
}
