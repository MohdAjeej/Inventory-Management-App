package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentScreen(
    productId: Long,
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToConfirm: (adjustmentType: String, quantity: Int, reason: String, notes: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    var product by remember { mutableStateOf<Product?>(null) }
    var adjustmentType by remember { mutableStateOf<String?>(null) } // "ADDED" or "REDUCED"
    var quantity by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var stockHistory by remember { mutableStateOf<List<StockAdjustmentResponse>>(emptyList()) }
    var historyFilter by remember { mutableStateOf<String?>(null) } // null, "ADDED", "REDUCED"
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showReasonDropdown by remember { mutableStateOf(false) }
    var showHistoryFilterDropdown by remember { mutableStateOf(false) }
    var previewStock by remember { mutableStateOf<Int?>(null) }

    val reasonOptions = mapOf(
        "ADDED" to listOf(
            "Purchase Order",
            "New Stock",
            "Return from Customer",
            "Stock Transfer In",
            "Adjustment - Count Error",
            "Other"
        ),
        "REDUCED" to listOf(
            "Sales Order",
            "Damaged Items",
            "Return to Supplier",
            "Stock Transfer Out",
            "Sales Return",
            "Expired",
            "Lost",
            "Other"
        )
    )

    // Load product and history
    LaunchedEffect(productId) {
        scope.launch {
            try {
                val businessId = sessionManager.getBusinessId()
                if (businessId != null) {
                    val products = ApiClient.apiService.products(businessId)
                    product = products.find { it.id == productId }
                    
                    // Load history
                    stockHistory = ApiClient.apiService.getStockHistory(productId, historyFilter)
                }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    // Update preview stock when quantity or type changes
    LaunchedEffect(quantity, adjustmentType, product) {
        val currentStock = product?.quantity ?: 0
        val qty = quantity.toIntOrNull() ?: 0
        
        previewStock = when (adjustmentType) {
            "ADDED" -> currentStock + qty
            "REDUCED" -> currentStock - qty
            else -> null
        }
    }

    // Reload history when filter changes
    LaunchedEffect(historyFilter) {
        scope.launch {
            try {
                stockHistory = ApiClient.apiService.getStockHistory(productId, historyFilter)
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stock Adjustment", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Add or reduce stock for your products.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            // Product Info Card
            item {
                product?.let { prod ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Product Image Placeholder
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF5E6D3)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = Color(0xFFD4A574),
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    prod.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    prod.category,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("SKU: ", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        prod.sku ?: "N/A",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Barcode: ", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        prod.barcode ?: "N/A",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Current Stock",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            "${prod.quantity} Units",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2196F3)
                                        )
                                    }
                                    Column {
                                        Text(
                                            "Minimum Stock",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            "${prod.minimumStock} Units",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Stock Status Badge
                            Surface(
                                shape = CircleShape,
                                color = if (prod.quantity > prod.minimumStock) 
                                    Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    if (prod.quantity > prod.minimumStock) 
                                        Icons.Default.CheckCircle 
                                    else 
                                        Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(8.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Adjust Stock Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SyncAlt,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Adjust Stock",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Update the stock quantity by adding or reducing units.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Adjustment Type Selection
                        Text(
                            "Adjustment Type",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Add Stock Button
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clickable { 
                                        adjustmentType = "ADDED"
                                        selectedReason = null
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (adjustmentType == "ADDED") 
                                        Color(0xFFE8F5E9) 
                                    else 
                                        Color(0xFFF5F5F5)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = if (adjustmentType == "ADDED")
                                    androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50))
                                else null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Add Stock",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        "Increase stock quantity",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Reduce Stock Button
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clickable { 
                                        adjustmentType = "REDUCED"
                                        selectedReason = null
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (adjustmentType == "REDUCED") 
                                        Color(0xFFFFEBEE) 
                                    else 
                                        Color(0xFFF5F5F5)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = if (adjustmentType == "REDUCED")
                                    androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF44336))
                                else null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = null,
                                        tint = Color(0xFFF44336),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Reduce Stock",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF44336)
                                    )
                                    Text(
                                        "Decrease stock quantity",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (adjustmentType != null) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Quantity Input
                            Text(
                                "Quantity *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                                placeholder = { Text("Enter quantity") },
                                trailingIcon = { Text("Units", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Reason Dropdown
                            Text(
                                "Reason *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = showReasonDropdown,
                                onExpandedChange = { showReasonDropdown = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedReason ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    placeholder = { Text("Select reason") },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                ExposedDropdownMenu(
                                    expanded = showReasonDropdown,
                                    onDismissRequest = { showReasonDropdown = false }
                                ) {
                                    reasonOptions[adjustmentType]?.forEach { reason ->
                                        DropdownMenuItem(
                                            text = { Text(reason) },
                                            onClick = {
                                                selectedReason = reason
                                                showReasonDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Notes (Optional)
                            Text(
                                "Notes (Optional)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { if (it.length <= 200) notes = it },
                                placeholder = { 
                                    Text("Add additional notes (e.g. purchase, return, damage, etc.)")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                maxLines = 4,
                                shape = RoundedCornerShape(8.dp),
                                supportingText = {
                                    Text(
                                        "${notes.length}/200",
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )

                            // Preview
                            if (previewStock != null && quantity.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE3F2FD),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF2196F3)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Current stock: ${product?.quantity ?: 0} Units",
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            "After adjustment: Preview will be shown here.",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2196F3)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        adjustmentType = null
                                        quantity = ""
                                        selectedReason = null
                                        notes = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = {
                                        // Navigate to confirmation screen
                                        if (adjustmentType != null && selectedReason != null && quantity.isNotEmpty()) {
                                            onNavigateToConfirm(
                                                adjustmentType!!,
                                                quantity.toInt(),
                                                selectedReason!!,
                                                notes.ifEmpty { "" }
                                            )
                                        }
                                    },
                                    enabled = !isLoading && adjustmentType != null && 
                                              quantity.isNotEmpty() && selectedReason != null,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Preview & Confirm")
                                }
                            }
                        }
                    }
                }
            }

            // Error Message
            errorMessage?.let { error ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFF44336)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(error, color = Color(0xFFF44336))
                        }
                    }
                }
            }

            // Stock History Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Stock History",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "View all stock adjustments for this product.",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            // Filter Dropdown
                            ExposedDropdownMenuBox(
                                expanded = showHistoryFilterDropdown,
                                onExpandedChange = { showHistoryFilterDropdown = it }
                            ) {
                                OutlinedButton(
                                    onClick = { showHistoryFilterDropdown = true },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        when (historyFilter) {
                                            "ADDED" -> "Added"
                                            "REDUCED" -> "Reduced"
                                            else -> "All Types"
                                        },
                                        fontSize = 12.sp
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                ExposedDropdownMenu(
                                    expanded = showHistoryFilterDropdown,
                                    onDismissRequest = { showHistoryFilterDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Types") },
                                        onClick = {
                                            historyFilter = null
                                            showHistoryFilterDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Added") },
                                        onClick = {
                                            historyFilter = "ADDED"
                                            showHistoryFilterDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Reduced") },
                                        onClick = {
                                            historyFilter = "REDUCED"
                                            showHistoryFilterDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // History Items
            items(stockHistory) { adjustment ->
                StockHistoryItem(adjustment)
            }

            if (stockHistory.isEmpty()) {
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
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
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

@Composable
fun StockHistoryItem(adjustment: StockAdjustmentResponse) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (adjustment.type == "ADDED") 
                    Color(0xFFE8F5E9) 
                else 
                    Color(0xFFFFEBEE),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (adjustment.type == "ADDED") 
                            Icons.Default.Add 
                        else 
                            Icons.Default.Remove,
                        contentDescription = null,
                        tint = if (adjustment.type == "ADDED") 
                            Color(0xFF4CAF50) 
                        else 
                            Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (adjustment.type == "ADDED") "+${adjustment.quantity}" 
                        else "-${adjustment.quantity}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (adjustment.type == "ADDED") 
                            Color(0xFF4CAF50) 
                        else 
                            Color(0xFFF44336)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    adjustment.reason,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                adjustment.notes?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${adjustment.stockBefore} → ${adjustment.stockAfter}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2196F3)
                    )
                    Text("•", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        adjustment.addedBy,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatDateTime(adjustment.adjustedAt).split(",")[0],
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    formatDateTime(adjustment.adjustedAt).split(",")[1].trim(),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

fun formatDateTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoString
    }
}
