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
import com.b2binventory.app.data.StockAdjustmentResponse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    businessId: Long,
    userId: Long
) {
    var stockAdjustments by remember { mutableStateOf<List<StockAdjustmentResponse>>(emptyList()) }
    var filteredAdjustments by remember { mutableStateOf<List<StockAdjustmentResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    
    val scope = rememberCoroutineScope()
    
    fun applyFilters() {
        var filtered = stockAdjustments
        
        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.productName.contains(searchQuery, ignoreCase = true) ||
                it.reason.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Apply type filter
        filtered = when (selectedFilter) {
            "Stock In" -> filtered.filter { it.type == "ADDED" }
            "Stock Out" -> filtered.filter { it.type == "REDUCED" }
            else -> filtered
        }
        
        filteredAdjustments = filtered
    }
    
    fun fetchData() {
        loading = true
        errorMessage = ""
        scope.launch {
            try {
                stockAdjustments = ApiClient.apiService.getBusinessStockHistory(businessId)
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
    val totalReceipts = filteredAdjustments.size
    val totalStockIn = filteredAdjustments.filter { it.type == "ADDED" }.sumOf { it.quantity }
    val totalStockOut = filteredAdjustments.filter { it.type == "REDUCED" }.sumOf { it.quantity }
    val netStock = totalStockIn - totalStockOut
    
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
        if (loading && stockAdjustments.isEmpty()) {
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
                            "${filteredAdjustments.size} items",
                            fontSize = 13.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
                
                // Receipts List
                if (filteredAdjustments.isEmpty()) {
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
                    items(filteredAdjustments) { adjustment ->
                        StockAdjustmentItem(adjustment = adjustment)
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
fun StockAdjustmentItem(adjustment: StockAdjustmentResponse) {
    // Parse the ISO-8601 timestamp
    val instant = try {
        Instant.parse(adjustment.adjustedAt)
    } catch (e: Exception) {
        Instant.now()
    }
    val dateTime = instant.atZone(ZoneId.systemDefault())
    val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val timeFormat = DateTimeFormatter.ofPattern("hh:mm a")
    
    val isStockIn = adjustment.type == "ADDED"
    val bgColor = if (isStockIn) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val iconColor = if (isStockIn) Color(0xFF4CAF50) else Color(0xFFF44336)
    val icon = if (isStockIn) Icons.Default.TrendingUp else Icons.Default.TrendingDown
    
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
                    adjustment.productName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Text(
                    adjustment.reason,
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                if (adjustment.notes != null && adjustment.notes.isNotEmpty()) {
                    Text(
                        adjustment.notes,
                        fontSize = 11.sp,
                        color = Color(0xFF999999),
                        maxLines = 1
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dateFormat.format(dateTime),
                        fontSize = 11.sp,
                        color = Color(0xFF999999)
                    )
                    Text("•", fontSize = 11.sp, color = Color(0xFF999999))
                    Text(
                        timeFormat.format(dateTime),
                        fontSize = 11.sp,
                        color = Color(0xFF999999)
                    )
                    Text("•", fontSize = 11.sp, color = Color(0xFF999999))
                    Text(
                        "By: ${adjustment.addedBy}",
                        fontSize = 11.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (isStockIn) "+${adjustment.quantity}" else "-${adjustment.quantity}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                Text(
                    "${adjustment.stockBefore} → ${adjustment.stockAfter}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}
