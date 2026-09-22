package com.b2binventory.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentConfirmScreen(
    productId: Long,
    businessId: Long,
    userId: Long,
    adjustmentType: String, // "ADDED" or "REDUCED"
    quantity: Int,
    reason: String,
    notes: String?,
    userName: String,
    onConfirm: () -> Unit,
    onEditDetails: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    
    var product by remember { mutableStateOf<Product?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Load product
    LaunchedEffect(productId) {
        scope.launch {
            try {
                isLoading = true
                val products = ApiClient.apiService.products(businessId)
                product = products.find { it.id == productId }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    val currentStock = product?.quantity ?: 0
    val newStock = if (adjustmentType == "ADDED") {
        currentStock + quantity
    } else {
        currentStock - quantity
    }

    val currentDateTime = LocalDateTime.now()
    val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
    val formattedDateTime = currentDateTime.format(dateTimeFormatter)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Confirm Stock Adjustment", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Review the details below before applying the stock adjustment.",
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
                // Product Info Card
                item {
                    product?.let { prod ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
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
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        prod.category,
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Tags
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        prod.sku?.let {
                                            ProductTag(label = "SKU: $it", color = Color(0xFF2196F3))
                                        }
                                        prod.unit?.let {
                                            ProductTag(label = it, color = Color(0xFF4CAF50))
                                        }
                                        prod.size?.let {
                                            ProductTag(label = it, color = Color(0xFFFF9800))
                                        }
                                        prod.thickness?.let {
                                            ProductTag(label = it, color = Color(0xFF9C27B0))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Adjustment Preview Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2196F3).copy(alpha = 0.1f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Adjustment Preview",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Please review the adjustment details before confirming.",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                            Spacer(modifier = Modifier.height(16.dp))

                            // Adjustment Details
                            PreviewDetailRow(
                                label = "Adjustment Type",
                                value = if (adjustmentType == "ADDED") "Add Stock" else "Reduce Stock",
                                valueColor = if (adjustmentType == "ADDED") Color(0xFF4CAF50) else Color(0xFFF44336),
                                icon = if (adjustmentType == "ADDED") Icons.Default.Add else Icons.Default.Remove
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PreviewDetailRow(
                                label = "Quantity to ${if (adjustmentType == "ADDED") "Add" else "Remove"}",
                                value = "${if (adjustmentType == "ADDED") "+" else "-"}$quantity Units",
                                valueColor = if (adjustmentType == "ADDED") Color(0xFF4CAF50) else Color(0xFFF44336),
                                valueFontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PreviewDetailRow(
                                label = "Current Stock",
                                value = "$currentStock Units",
                                valueColor = Color(0xFF1A1A1A)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PreviewDetailRow(
                                label = "New Stock (After Adjustment)",
                                value = "$newStock Units",
                                valueColor = Color(0xFF2196F3),
                                valueFontWeight = FontWeight.Bold,
                                valueFontSize = 16.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                            Spacer(modifier = Modifier.height(16.dp))

                            PreviewDetailRow(
                                label = "Reason",
                                value = reason,
                                valueColor = Color(0xFF1A1A1A)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PreviewDetailRow(
                                label = "Date & Time",
                                value = formattedDateTime,
                                valueColor = Color(0xFF666666)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PreviewDetailRow(
                                label = "Added By",
                                value = userName,
                                valueColor = Color(0xFF1A1A1A)
                            )

                            if (!notes.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column {
                                    Text(
                                        "Notes",
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        notes,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1A1A1A),
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Ready to Update Badge
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Ready to Update",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                                Text(
                                    "The stock will be ${if (adjustmentType == "ADDED") "increased" else "decreased"} by $quantity units and the new stock will be $newStock units.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Stock Change Summary
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Stock Change Summary",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                            }

                            Text(
                                "Visual representation of stock after adjustment.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Current Stock
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFE3F2FD),
                                        modifier = Modifier.size(80.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Current Stock",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        "$currentStock",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        "Units",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                // Arrow with quantity
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (adjustmentType == "ADDED") 
                                            Color(0xFFE8F5E9) 
                                        else 
                                            Color(0xFFFFEBEE)
                                    ) {
                                        Text(
                                            "${if (adjustmentType == "ADDED") "+" else "-"}$quantity",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (adjustmentType == "ADDED") 
                                                Color(0xFF4CAF50) 
                                            else 
                                                Color(0xFFF44336),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        "Units",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                // New Stock
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFE8F5E9),
                                        modifier = Modifier.size(80.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Inventory,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "New Stock",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        "$newStock",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        "Units",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Warning Message
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE3F2FD)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "This action will update the inventory.",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A1A1A)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Once confirmed, the stock adjustment will be recorded in the system and cannot be undone directly. You can, however, create a reverse adjustment if needed.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Error Message
                errorMessage?.let { error ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
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
                                Text(error, color = Color(0xFFF44336), fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Success Message
                successMessage?.let { success ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(success, color = Color(0xFF4CAF50), fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Action Buttons
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSubmitting = true
                                    errorMessage = null
                                    try {
                                        val request = StockAdjustmentRequest(
                                            productId = productId,
                                            businessId = businessId,
                                            userId = userId,
                                            type = adjustmentType,
                                            quantity = quantity,
                                            reason = reason,
                                            notes = notes
                                        )

                                        ApiClient.apiService.adjustStock(request)
                                        successMessage = "Stock updated successfully!"
                                        
                                        // Call onConfirm after a short delay to show success message
                                        kotlinx.coroutines.delay(1000)
                                        onConfirm()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Failed to update stock"
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Updating...")
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Confirm & Update Stock", fontSize = 16.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = onEditDetails,
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF2196F3)
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Details", fontSize = 16.sp)
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
fun PreviewDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF1A1A1A),
    valueFontWeight: FontWeight = FontWeight.Normal,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Surface(
                    shape = CircleShape,
                    color = valueColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = valueColor,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            Text(
                value,
                fontSize = valueFontSize,
                color = valueColor,
                fontWeight = valueFontWeight,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun ProductTag(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
