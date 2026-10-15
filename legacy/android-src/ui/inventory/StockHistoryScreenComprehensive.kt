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
fun StockHistoryScreenComprehensive(
    businessId: Long,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    
    var allHistory by remember { mutableStateOf<List<StockAdjustmentResponse>>(emptyList()) }
    var filteredHistory by remember { mutableStateOf<List<StockAdjustmentResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedAdjustmentType by remember { mutableStateOf<String?>(null) } // null, "ADDED", "REDUCED"
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showUserDropdown by remember { mutableStateOf(false) }
    
    // Pagination
    var currentPage by remember { mutableStateOf(1) }
    val itemsPerPage = 10
    
    // Statistics
    val totalInward = allHistory.filter { it.type == "ADDED" }.sumOf { it.quantity }
    val totalOutward = allHistory.filter { it.type == "REDUCED" }.sumOf { it.quantity }
    val netIncrease = totalInward - totalOutward
    val totalEntries = allHistory.size
    
    // Extract unique values for filters
    val categories = allHistory.map { it.productName }.distinct().sorted()
    val users = allHistory.map { it.addedBy }.distinct().sorted()

    // Load all history for business
    LaunchedEffect(businessId) {
        scope.launch {
            try {
                isLoading = true
                allHistory = ApiClient.apiService.getBusinessStockHistory(businessId)
                filteredHistory = allHistory
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // Apply filters
    LaunchedEffect(searchQuery, selectedAdjustmentType, selectedCategory, selectedUser, allHistory) {
        filteredHistory = allHistory.filter { adjustment ->
            val matchesSearch = searchQuery.isEmpty() || 
                adjustment.productName.contains(searchQuery, ignoreCase = true) ||
                adjustment.reason.contains(searchQuery, ignoreCase = true)
            
            val matchesType = selectedAdjustmentType == null || adjustment.type == selectedAdjustmentType
            val matchesCategory = selectedCategory == null || adjustment.productName == selectedCategory
            val matchesUser = selectedUser == null || adjustment.addedBy == selectedUser
            
            matchesSearch && matchesType && matchesCategory && matchesUser
        }
        currentPage = 1
    }

    // Paginated items
    val paginatedItems = remember(filteredHistory, currentPage) {
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, filteredHistory.size)
        if (startIndex < filteredHistory.size) {
            filteredHistory.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    val totalPages = (filteredHistory.size + itemsPerPage - 1) / itemsPerPage

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stock History", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "View all stock adjustments and transactions.",
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
                // Statistics Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = Icons.Default.Inventory2,
                            iconColor = Color(0xFF2196F3),
                            iconBgColor = Color(0xFFE3F2FD),
                            title = "Total Inward",
                            value = totalInward.toString(),
                            subtitle = "Units",
                            trend = "up",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.ShoppingCart,
                            iconColor = Color(0xFFF44336),
                            iconBgColor = Color(0xFFFFEBEE),
                            title = "Total Outward",
                            value = totalOutward.toString(),
                            subtitle = "Units",
                            trend = "down",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = Icons.Default.TrendingUp,
                            iconColor = Color(0xFF4CAF50),
                            iconBgColor = Color(0xFFE8F5E9),
                            title = "Net Increase",
                            value = netIncrease.toString(),
                            subtitle = "Units",
                            trend = "up",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.Description,
                            iconColor = Color(0xFF2196F3),
                            iconBgColor = Color(0xFFE3F2FD),
                            title = "Total Entries",
                            value = totalEntries.toString(),
                            subtitle = "Records",
                            trend = null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Filters Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search by product name, SKU or barcode...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3)
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Date Range (placeholder)
                            OutlinedButton(
                                onClick = { /* Date picker */ },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF666666)
                                )
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("01 Sep 2025 - 30 Sep 2025", fontSize = 14.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Filter Dropdowns
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Adjustment Type Filter
                                ExposedDropdownMenuBox(
                                    expanded = showTypeDropdown,
                                    onExpandedChange = { showTypeDropdown = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selectedAdjustmentType?.let {
                                            if (it == "ADDED") "Added" else "Reduced"
                                        } ?: "All Types",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3)
                                        )
                                    )

                                    ExposedDropdownMenu(
                                        expanded = showTypeDropdown,
                                        onDismissRequest = { showTypeDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All Types") },
                                            onClick = {
                                                selectedAdjustmentType = null
                                                showTypeDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Added") },
                                            onClick = {
                                                selectedAdjustmentType = "ADDED"
                                                showTypeDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Reduced") },
                                            onClick = {
                                                selectedAdjustmentType = "REDUCED"
                                                showTypeDropdown = false
                                            }
                                        )
                                    }
                                }

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
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3)
                                        )
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
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Added By Filter
                                ExposedDropdownMenuBox(
                                    expanded = showUserDropdown,
                                    onExpandedChange = { showUserDropdown = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selectedUser ?: "All Users",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2196F3)
                                        )
                                    )

                                    ExposedDropdownMenu(
                                        expanded = showUserDropdown,
                                        onDismissRequest = { showUserDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All Users") },
                                            onClick = {
                                                selectedUser = null
                                                showUserDropdown = false
                                            }
                                        )
                                        users.forEach { user ->
                                            DropdownMenuItem(
                                                text = { Text(user) },
                                                onClick = {
                                                    selectedUser = user
                                                    showUserDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Action Buttons
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            searchQuery = ""
                                            selectedAdjustmentType = null
                                            selectedCategory = null
                                            selectedUser = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Clear Filters", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { /* Already auto-applied */ },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2196F3)
                                        )
                                    ) {
                                        Text("Apply Filters", fontSize = 12.sp)
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
                            Text("Date & Time", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                            Text("Product", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                            Text("Quantity", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                            Text("Stock After", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("Reason", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                            Text("Added By", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Spacer(modifier = Modifier.width(30.dp)) // Actions column
                        }
                    }
                }

                // Table Rows
                itemsIndexed(paginatedItems) { index, adjustment ->
                    val globalIndex = (currentPage - 1) * itemsPerPage + index + 1
                    StockHistoryTableRow(
                        index = globalIndex,
                        adjustment = adjustment
                    )
                }

                // Empty State
                if (filteredHistory.isEmpty() && !isLoading) {
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
                                        Icons.Default.HistoryToggleOff,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No stock adjustments found",
                                        fontSize = 16.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Try adjusting your filters",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Pagination
                if (filteredHistory.isNotEmpty()) {
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
                                    "Showing ${(currentPage - 1) * itemsPerPage + 1} to ${minOf(currentPage * itemsPerPage, filteredHistory.size)} of ${filteredHistory.size} records",
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

                // Info Banner
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE3F2FD)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Keep track of your inventory movements",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A1A1A)
                                )
                                Text(
                                    "All stock adjustments are recorded for better transparency and inventory control.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 16.sp
                                )
                            }
                            OutlinedButton(
                                onClick = { /* Export functionality */ },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF2196F3)
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export History", fontSize = 13.sp)
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
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    title: String,
    value: String,
    subtitle: String,
    trend: String?, // "up", "down", or null
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconBgColor,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                
                if (trend != null) {
                    Icon(
                        if (trend == "up") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (trend == "up") Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                title,
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StockHistoryTableRow(
    index: Int,
    adjustment: StockAdjustmentResponse
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

                // Date & Time
                Column(modifier = Modifier.width(90.dp)) {
                    Text(
                        formatDate(adjustment.adjustedAt),
                        fontSize = 11.sp,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        formatTime(adjustment.adjustedAt),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                // Product with image
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF5E6D3)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = null,
                            tint = Color(0xFFD4A574),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        adjustment.productName,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }

                // Type Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (adjustment.type == "ADDED") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier.width(70.dp)
                ) {
                    Text(
                        if (adjustment.type == "ADDED") "Added" else "Reduced",
                        fontSize = 11.sp,
                        color = if (adjustment.type == "ADDED") Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Quantity
                Text(
                    "${if (adjustment.type == "ADDED") "+" else "-"}${adjustment.quantity}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (adjustment.type == "ADDED") Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.width(70.dp),
                    textAlign = TextAlign.Center
                )

                // Stock After
                Text(
                    adjustment.stockAfter.toString(),
                    fontSize = 13.sp,
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.Center
                )

                // Reason
                Text(
                    adjustment.reason,
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.width(100.dp),
                    maxLines = 1
                )

                // Added By
                Text(
                    adjustment.addedBy,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1
                )

                // Actions
                IconButton(
                    onClick = { /* More options */ },
                    modifier = Modifier.size(30.dp)
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

fun formatDate(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoString
    }
}

fun formatTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter
            .ofPattern("hh:mm a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        ""
    }
}
