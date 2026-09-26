package com.b2binventory.app.ui.inventory

import androidx.compose.material3.*
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryScreen(
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit
) {
    // Use professional version
    AddInventoryScreenPro(
        businessId = businessId,
        userId = userId,
        onNavigateBack = onNavigateBack
    )
}
