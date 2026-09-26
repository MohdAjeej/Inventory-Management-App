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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: Long,
    businessId: Long,
    userId: Long,
    onNavigateToStockAdjustment: () -> Unit,
    onNavigateToStockHistory: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    
    fun loadProduct() {
        loading = true
        errorMessage = ""
        scope.launch {
            try {
                val products = ApiClient.apiService.products(businessId)
                product = products.find { it.id == productId }
                if (product == null) {
                    errorMessage = "Product not found"
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load product: ${e.message}"
            } finally {
                loading = false
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
                                    "Product Details",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                                Text(
                                    "View complete information about your product",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onNavigateToEdit,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF2196F3), RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { /* More options */ },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = Color(0xFF666666)
                                )
                            }
                        }
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
        } else if (errorMessage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(errorMessage, color = Color.Red)
                    Button(
                        onClick = { loadProduct() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
        } else if (product != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Product Image Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column {
                            // Main Product Image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .background(Color(0xFFF5F5F5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    getCategoryEmoji(product!!.category),
                                    fontSize = 120.sp
                                )
                                
                                // Stock Status Badge
                                val status = getStockStatusFunc(product!!)
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
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp),
                                    color = statusColor,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                        Text(
                                            statusText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            
                            // Thumbnail Images Row
                            LazyRow(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(4) { index ->
                                    Surface(
                                        modifier = Modifier.size(60.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF5F5F5),
                                        border = if (index == 0) androidx.compose.foundation.BorderStroke(
                                            2.dp,
                                            Color(0xFF2196F3)
                                        ) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                getCategoryEmoji(product!!.category),
                                                fontSize = 24.sp
                                            )
                                        }
                                    }
                                }
                                item {
                                    Surface(
                                        modifier = Modifier.size(60.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2196F3)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    "+2",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    "More",
                                                    fontSize = 10.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Product Info Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        product!!.name,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        product!!.brand ?: product!!.category,
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Product Specs Pills
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    SpecPill("Ply", product!!.category)
                                }
                                if (!product!!.thickness.isNullOrBlank()) {
                                    item {
                                        SpecPill("18 mm", product!!.thickness!!)
                                    }
                                }
                                if (!product!!.size.isNullOrBlank()) {
                                    item {
                                        SpecPill("8 x 4 ft", product!!.size!!)
                                    }
                                }
                                item {
                                    SpecPill("Interior Use", "Indoor")
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Description
                            if (!product!!.description.isNullOrBlank()) {
                                Text(
                                    product!!.description!!,
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            // Product Details Grid
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DetailItem("SKU", product!!.sku ?: "N/A", Modifier.weight(1f))
                                    DetailItem("Barcode", product!!.barcode ?: "N/A", Modifier.weight(1f))
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DetailItem("Category", product!!.category, Modifier.weight(1f))
                                    DetailItem("Brand", product!!.brand ?: "N/A", Modifier.weight(1f))
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DetailItem("Size", product!!.size ?: "N/A", Modifier.weight(1f))
                                    DetailItem("Thickness", product!!.thickness ?: "N/A", Modifier.weight(1f))
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DetailItem("Color", product!!.color ?: "Natural Wood", Modifier.weight(1f))
                                    DetailItem("Unit Type", product!!.unit, Modifier.weight(1f))
                                }
                                DetailItem("Minimum Stock", "${product!!.minimumStock} Units", Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                
                // Stock Summary Cards Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StockSummaryCard(
                            title = "Current Stock",
                            value = "${product!!.quantity} Units",
                            icon = Icons.Default.Inventory,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                        StockSummaryCard(
                            title = "Total Inward",
                            value = "150 Units",
                            icon = Icons.Default.ShoppingCart,
                            color = Color(0xFF2196F3),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StockSummaryCard(
                            title = "Total Outward",
                            value = "30 Units",
                            icon = Icons.Default.LocalShipping,
                            color = Color(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                        StockSummaryCard(
                            title = "Minimum Stock",
                            value = "${product!!.minimumStock} Units",
                            icon = Icons.Default.Warning,
                            color = Color(0xFFFFA726),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Tabs
                item {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF2196F3),
                        edgePadding = 0.dp
                    ) {
                        listOf("Overview", "Stock History", "Pricing", "Suppliers", "Related Products").forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
                
                // Tab Content
                when (selectedTab) {
                    0 -> {
                        // Overview - Stock Status
                        item {
                            StockStatusCard(product!!)
                        }
                        // Pricing Information
                        item {
                            PricingInfoCard()
                        }
                        // Supplier Information
                        item {
                            SupplierInfoCard()
                        }
                        // Product Description
                        item {
                            ProductDescriptionCard(product!!.description)
                        }
                    }
                    1 -> {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = Color(0xFFCCCCCC)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Stock history coming soon", color = Color(0xFF999999))
                                        Button(
                                            onClick = onNavigateToStockHistory,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2196F3)
                                            )
                                        ) {
                                            Text("View History")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Quick Actions
                item {
                    QuickActionsCard(
                        onAddStock = onNavigateToStockAdjustment,
                        onRemoveStock = onNavigateToStockAdjustment,
                        onUpdatePrice = onNavigateToEdit,
                        onTransferStock = { /* TODO */ }
                    )
                }
                
                // Bottom Spacer
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun SpecPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Text(
            value,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color(0xFF666666)
        )
    }
}

@Composable
fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            fontSize = 11.sp,
            color = Color(0xFF999999)
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun StockSummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column {
                Text(
                    title,
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
                Text(
                    value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
fun StockStatusCard(product: Product) {
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
                Text(
                    "Stock Status",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stock Donut Chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Placeholder for donut chart
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${product.quantity}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        Text(
                            "Units",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StockLegendItem("In Stock", product.quantity, Color(0xFF4CAF50))
                    StockLegendItem("Reserved", 10, Color(0xFFFFA726))
                    StockLegendItem("Out of Stock", 0, Color(0xFFF44336))
                }
            }
        }
    }
}

@Composable
fun StockLegendItem(label: String, value: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            label,
            fontSize = 13.sp,
            color = Color(0xFF666666),
            modifier = Modifier.width(100.dp)
        )
        Text(
            value.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun PricingInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Text(
                        "Pricing Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                IconButton(onClick = { /* Edit */ }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PricingRow("Unit Price", "₹ 2,150")
                PricingRow("MRP", "₹ 2,450")
                PricingRow("Tax (GST)", "18%")
                HorizontalDivider(color = Color(0xFFEEEEEE))
                PricingRow("Price (Incl. GST)", "₹ 2,537", true)
            }
        }
    }
}

@Composable
fun PricingRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color(0xFF666666)
        )
        Text(
            value,
            fontSize = if (isBold) 16.sp else 14.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun SupplierInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Supplier Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                IconButton(onClick = { /* Edit */ }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupplierRow("Supplier Name", "ABC Traders")
                SupplierRow("Contact Person", "Rohit Sharma")
                SupplierRow("Phone", "+91 98765 43210")
                SupplierRow("Email", "abc@traders.com")
            }
        }
    }
}

@Composable
fun SupplierRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color(0xFF666666)
        )
        Text(
            value,
            fontSize = 14.sp,
            color = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun ProductDescriptionCard(description: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Text(
                        "Product Description",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                IconButton(onClick = { /* Edit */ }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                description ?: "High quality commercial plywood with superior strength and durability. Suitable for furniture, cabinets, partitions and interior work. Moisture resistant and long lasting.",
                fontSize = 13.sp,
                color = Color(0xFF666666),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun QuickActionsCard(
    onAddStock: () -> Unit,
    onRemoveStock: () -> Unit,
    onUpdatePrice: () -> Unit,
    onTransferStock: () -> Unit
) {
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
                    Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Quick Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Add,
                    label = "Add Stock",
                    color = Color(0xFF4CAF50),
                    onClick = onAddStock,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.Remove,
                    label = "Remove Stock",
                    color = Color(0xFFF44336),
                    onClick = onRemoveStock,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.AttachMoney,
                    label = "Update Price",
                    color = Color(0xFF2196F3),
                    onClick = onUpdatePrice,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.SwapHoriz,
                    label = "Transfer Stock",
                    color = Color(0xFF9C27B0),
                    onClick = onTransferStock,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}
