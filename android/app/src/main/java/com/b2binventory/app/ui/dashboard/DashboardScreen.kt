package com.b2binventory.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    businessId: Long,
    userId: Long,
    onNavigateToInventory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Use the professional dashboard design
    DashboardScreenPro(
        businessId = businessId,
        userId = userId,
        onNavigateToInventory = onNavigateToInventory,
        onNavigateToReports = { /* TODO */ },
        onNavigateToParties = { /* TODO */ },
        onNavigateToSettings = onNavigateToSettings,
        onAddProduct = onNavigateToInventory
    )
}
