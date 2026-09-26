package com.b2binventory.app.ui.inventory

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import com.b2binventory.app.data.StockAdjustmentResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockHistoryScreen(
    productId: Long,
    businessId: Long,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var product by remember { mutableStateOf<Product?>(null) }
    var stockHistory by remember { mutableStateOf<List<StockAdjustmentResponse>>(emptyList()) }
    var filteredHistory by remember { mutableStateOf<List<StockAdjustmentResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf<String?>(null) } // null, "ADDED", "REDUCED"
    var showFilterDropdown by remember { mutableStateOf(false) }

    // Load data
    LaunchedEffect(productId) {
        scope.launch {
            try {
                isLoading = true
                val products = ApiClient.apiService.products(businessId)
                product = products.find { it.id == productId }
                stockHistory = ApiClient.apiService.getStockHistory(productId)
                filteredHistory = stockHistory
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // Apply filter
    LaunchedEffect(selectedFilter, stockHistory) {
        filteredHistory = if (selectedFilter == null) {
            stockHistory
        } else {
            stockHistory.filter { it.type == selectedFilter }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stock History", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        product?.let {
                            Text(
                                it.name,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Filter button
                    ExposedDropdownMenuBox(
                        expanded = showFilterDropdown,
                        onExpandedChange = { showFilterDropdown = it }
                    ) {
                        IconButton(
                            onClick = { showFilterDropdown = true },
                            modifier = Modifier.menuAnchor()
                        ) {
                            Badge(
                                containerColor = if (selectedFilter != null) 
                                    Color(0xFF2196F3) 
                                else 
                                    Color.Transparent
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (selectedFilter != null) 
                                        Color.White 
                                    else 
                                        Color(0xFF1A1A1A)
                                )
                            }
                        }

                        ExposedDropdownMenu(
                            expanded = showFilterDropdown,
                            onDismissRequest = { showFilterDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Types") },
                                onClick = {
                                    selectedFilter = null
                                    showFilterDropdown = false
                                },
                                leadingIcon = {
                                    if (selectedFilter == null) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Added Only") },
                                onClick = {
                                    selectedFilter = "ADDED"
                                    showFilterDropdown = false
                                },
                                leadingIcon = {
                                    if (selectedFilter == "ADDED") {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reduced Only") },
                                onClick = {
                                    selectedFilter = "REDUCED"
                                    showFilterDropdown = false
                                },
                                leadingIcon = {
                                    if (selectedFilter == "REDUCED") {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
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
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        errorMessage ?: "Unknown error",
                        color = Color(0xFFF44336),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5))
            ) {
                // Summary Card
                item {
                    product?.let { prod ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Current Stock",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            "${prod.quantity} Units",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2196F3)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = if (prod.quantity > prod.minimumStock)
                                            Color(0xFF4CAF50)
                                        else
                                            Color(0xFFF44336)
                                    ) {
                                        Icon(
                                            if (prod.quantity > prod.minimumStock)
                                                Icons.Default.CheckCircle
                                            else
                                                Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatItem(
                                        label = "Total Additions",
                                        value = stockHistory.filter { it.type == "ADDED" }
                                            .sumOf { it.quantity },
                                        color = Color(0xFF4CAF50)
                                    )
                                    StatItem(
                                        label = "Total Reductions",
                                        value = stockHistory.filter { it.type == "REDUCED" }
                                            .sumOf { it.quantity },
                                        color = Color(0xFFF44336)
                                    )
                                    StatItem(
                                        label = "Total Changes",
                                        value = stockHistory.size,
                                        color = Color(0xFF2196F3)
                                    )
                                }
                            }
                        }
                    }
                }

                // History Header
                item {
                    Text(
                        "Transaction History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // History Items
                items(filteredHistory) { adjustment ->
                    StockHistoryItem(adjustment)
                }

                if (filteredHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.HistoryToggleOff,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (selectedFilter != null)
                                        "No ${if (selectedFilter == "ADDED") "additions" else "reductions"} found"
                                    else
                                        "No stock adjustments yet",
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
