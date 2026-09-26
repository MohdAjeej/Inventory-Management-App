package com.b2binventory.app.ui.auth

import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun RegisterBusinessScreen(
    onRegisterSuccess: (Long, Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    // Use professional version with 3-step wizard
    RegisterBusinessScreenPro(
        onRegisterSuccess = onRegisterSuccess,
        onNavigateBack = onNavigateBack
    )
}
