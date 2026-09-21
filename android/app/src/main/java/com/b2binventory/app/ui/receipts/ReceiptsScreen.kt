package com.b2binventory.app.ui.receipts

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class Receipt(
    val id: Long,
    val productId: Long,
    val productName: String,
    val category: String,
    val quantity: Int,
    val price: Double,
    val totalAmount: Double,
    val transactionType: String, // "IN" or "OUT"
    val date: Date,
    val sku: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    businessId: Long,
    userId: Long
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var receipts by remember { mutableStateOf<List<Receipt>>(emptyList()) }
    var filteredReceipts by remember { mutableStateOf<List<Receipt>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    
    val scope = rememberCoroutineScope()
    
    fun generateReceipts(products: List<Product>) {
        val receiptsList = mutableListOf<Receipt>()
        var receiptId = 1L
        
        // Generate receipts from products (simulating transaction history)
        products.forEach { product ->
            // Use a default price based on product name hash for consistency
            val defaultPrice = ((product.name.hashCode() % 500) + 50).toDouble()
            
            // Simulate some "IN" transactions (stock additions)
            val inTransactions = (1..3).random()
            repeat(inTransactions) {
                val qty = (10..50).random()
                receiptsList.add(
                    Receipt(
                        id = receiptId++,
                        productId = product.id ?: 0L,
                        productName = product.name,
                        category = product.category,
                        quantity = qty,
                        price = defaultPrice,
                        totalAmount = defaultPrice * qty,
                        transactionType = "IN",
                        date = Date(System.currentTimeMillis() - (0..30L).random() * 24 * 60 * 60 * 1000),
                        sku = product.sku
                    )
                )
            }
            
            // Simulate some "OUT" transactions (sales/usage)
            val outTransactions = (1..2).random()
            repeat(outTransactions) {
                val qty = (1..20).random()
                receiptsList.add(
                    Receipt(
                        id = receiptId++,
                        productId = product.id ?: 0L,
                        productName = product.name,
                        category = product.category,
                        quantity = qty,
                        price = defaultPrice,
                        totalAmount = defaultPrice * qty,
                        transactionType = "OUT",
                        date = Date(System.currentTimeMillis() - (0..30L).random() * 24 * 60 * 60 * 1000),
                        sku = product.sku
                    )
                )
            }
        }
        
        receipts = receiptsList.sortedByDescending { it.date }
    }
    
    fun applyFilters() {
        var filtered = receipts
        
        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.productName.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.sku?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        
        // Apply type filter
        filtered = when (selectedFilter) {
            "Stock In" -> filtered.filter { it.transactionType == "IN" }
            "Stock Out" -> filtered.filter { it.transactionType == "OUT" }
            else -> filtered
        }
        
        filteredReceipts = filtered
    }
    
    fun fetchData() {
        loading = true
        errorMessage = ""
        scope.launch {
            try {
                products = ApiClient.apiService.products(businessId = businessId)
                generateReceipts(products)
                applyFilters()
            } catch (e: Exception) {
                errorMessage = "Failed to load receipts: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        fetchData()
    }
    
    LaunchedEffect(searchQuery, selectedFilter) {
        applyFilters()
    }
    
    // Calculate stats
    val totalReceipts = filteredReceipts.size
    val totalStockIn = filteredReceipts.filter { it.transactionType == "IN" }.sumOf { it.quantity }
    val totalStockOut = filteredReceipts.filter { it.transactionType == "OUT" }.sumOf { it.quantity }
    val totalValue = filteredReceipts.sumOf { it.totalAmount }
    
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
                                color = Color(0xFF9C27B0),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Receipts", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Transaction history", fontSize = 12.sp, color = Color(0xFF666666))
                            }
                        }
                        
                        IconButton(onClick = { fetchData() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (loading) Color(0xFF9C27B0) else Color(0xFF666666)
                            )
                        }
                    }
                    Divider(color = Color(0xFFEEEEEE))
                }
            }
        }
    ) { padding ->
        if (loading && receipts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF9C27B0))
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
                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search receipts...", color = Color(0xFF999999)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF666666))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF666666))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF9C27B0),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                
                // Filter Chips
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Stock In", "Stock Out").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { 
                                    Text(
                                        filter,
                                        fontSize = 13.sp,
                                        color = if (selectedFilter == filter) Color.White else Color(0xFF666666)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF9C27B0),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White
                                )
                            )
                        }
                    }
                }
                
                // Stats Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatsCard(
                            title = "Total",
                            value = totalReceipts.toString(),
                            color = Color(0xFF9C27B0),
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            title = "Stock In",
                            value = totalStockIn.toString(),
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            title = "Stock Out",
                            value = totalStockOut.toString(),
                            color = Color(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Receipts List Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Transactions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                        Text(
                            "${filteredReceipts.size} items",
                            fontSize = 13.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
                
                // Receipts List
                if (filteredReceipts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color(0xFFCCCCCC)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No receipts found", color = Color(0xFF999999))
                            }
                        }
                    }
                } else {
                    items(filteredReceipts) { receipt ->
                        ReceiptItem(receipt = receipt)
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    title: String,
    value: String,
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
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = Color(0xFF666666))
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ReceiptItem(receipt: Receipt) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    
    val bgColor = if (receipt.transactionType == "IN") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val iconColor = if (receipt.transactionType == "IN") Color(0xFF4CAF50) else Color(0xFFF44336)
    val icon = if (receipt.transactionType == "IN") Icons.Default.TrendingUp else Icons.Default.TrendingDown
    
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = bgColor,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    receipt.productName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Text(
                    receipt.category,
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dateFormat.format(receipt.date),
                        fontSize = 11.sp,
                        color = Color(0xFF999999)
                    )
                    Text("•", fontSize = 11.sp, color = Color(0xFF999999))
                    Text(
                        timeFormat.format(receipt.date),
                        fontSize = 11.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (receipt.transactionType == "IN") "+${receipt.quantity}" else "-${receipt.quantity}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                Text(
                    "$${String.format("%.2f", receipt.totalAmount)}",
                    fontSize = 13.sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}
