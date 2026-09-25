package com.b2binventory.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b2binventory.app.data.ApiClient
import com.b2binventory.app.data.Business
import com.b2binventory.app.data.SessionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    businessId: Long,
    userId: Long,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToBusinessProfile: () -> Unit = {},
    onNavigateToTeamMembers: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToHelpSupport: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
    val userName = remember { sessionManager.getUserName() ?: "User" }
    val userEmail = remember { sessionManager.getUserEmail() ?: "user@example.com" }
    val userRole = remember { sessionManager.getUserRole() ?: "Admin" }
    
    var business by remember { mutableStateOf<Business?>(null) }
    var businessLoading by remember { mutableStateOf(true) }
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    // Load preferences from SharedPreferences
    val prefs = remember { 
        try {
            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }
    var notificationsEnabled by remember { mutableStateOf(prefs?.getBoolean("notifications", true) ?: true) }
    var darkModeEnabled by remember { mutableStateOf(prefs?.getBoolean("dark_mode", false) ?: false) }
    var autoBackup by remember { mutableStateOf(prefs?.getBoolean("auto_backup", true) ?: true) }
    
    // Save preferences when changed
    fun savePreference(key: String, value: Boolean) {
        try {
            prefs?.edit()?.putBoolean(key, value)?.apply()
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    // Fetch business data
    LaunchedEffect(businessId) {
        try {
            businessLoading = true
            business = ApiClient.apiService.getBusinessById(businessId)
        } catch (e: Exception) {
            // Handle error silently
        } finally {
            businessLoading = false
        }
    }
    
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { 
                Text(
                    "Logout", 
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text("Are you sure you want to sign out of your account?") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        sessionManager.logout()
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { 
                Text(
                    "B2B Inventory Management", 
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Column {
                    Text("Version: 1.0.0")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Build: 2026.09.25")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A comprehensive inventory management solution for B2B businesses.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("© 2026 B2B Inventory. All rights reserved.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings & Preferences",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F7FA))
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card with Gradient
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF2196F3),
                                        Color(0xFF1976D2)
                                    )
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Avatar with shadow
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 8.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF64B5F6),
                                                Color(0xFF2196F3)
                                            )
                                        )
                                    )
                                ) {
                                    Text(
                                        text = userName.firstOrNull()?.uppercase() ?: "U",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = userEmail,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = userRole,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            
                            IconButton(
                                onClick = { /* Edit profile */ },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.2f),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Business Information Section
            item {
                SectionHeader("Business Information")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Business,
                        iconBg = Color(0xFFE3F2FD),
                        iconColor = Color(0xFF2196F3),
                        title = "Business Profile",
                        subtitle = if (businessLoading) "Loading..." 
                                  else business?.name ?: "My Business",
                        onClick = onNavigateToBusinessProfile
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Info,
                        iconBg = Color(0xFFF3E5F5),
                        iconColor = Color(0xFF9C27B0),
                        title = "Business Details",
                        subtitle = business?.type ?: "View business information",
                        onClick = { /* TODO: Navigate to business details */ }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.LocationOn,
                        iconBg = Color(0xFFE8F5E9),
                        iconColor = Color(0xFF4CAF50),
                        title = "Address & Location",
                        subtitle = business?.address ?: "Update business address",
                        onClick = { /* TODO: Navigate to address */ }
                    )
                }
            }
            
            // Inventory Settings Section
            item {
                SectionHeader("Inventory Management")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Category,
                        iconBg = Color(0xFFFCE4EC),
                        iconColor = Color(0xFFE91E63),
                        title = "Categories",
                        subtitle = "Manage product categories",
                        onClick = onNavigateToCategories
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Inventory,
                        iconBg = Color(0xFFFFF3E0),
                        iconColor = Color(0xFFFF9800),
                        title = "Stock Management",
                        subtitle = "Configure stock alerts and thresholds",
                        onClick = { /* TODO: Navigate to stock settings */ }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Receipt,
                        iconBg = Color(0xFFE0F7FA),
                        iconColor = Color(0xFF00BCD4),
                        title = "Barcode Scanner",
                        subtitle = "Configure barcode scanning",
                        onClick = { /* TODO: Navigate to barcode settings */ }
                    )
                }
            }
            
            // Team Management Section
            item {
                SectionHeader("Team & Access")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Group,
                        iconBg = Color(0xFFE8EAF6),
                        iconColor = Color(0xFF3F51B5),
                        title = "Team Members",
                        subtitle = "Manage employees and roles",
                        onClick = onNavigateToTeamMembers
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Security,
                        iconBg = Color(0xFFFFF8E1),
                        iconColor = Color(0xFFFFC107),
                        title = "Permissions",
                        subtitle = "Configure user permissions",
                        onClick = { /* TODO: Navigate to permissions */ }
                    )
                }
            }
            
            // Preferences Section
            item {
                SectionHeader("App Preferences")
            }
            
            item {
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        iconBg = Color(0xFFFFF3E0),
                        iconColor = Color(0xFFFF9800),
                        title = "Notifications",
                        subtitle = "Enable push notifications",
                        checked = notificationsEnabled,
                        onCheckedChange = { 
                            notificationsEnabled = it
                            savePreference("notifications", it)
                        }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        icon = Icons.Default.DarkMode,
                        iconBg = Color(0xFFEDE7F6),
                        iconColor = Color(0xFF673AB7),
                        title = "Dark Mode",
                        subtitle = "Use dark theme",
                        checked = darkModeEnabled,
                        onCheckedChange = { 
                            darkModeEnabled = it
                            savePreference("dark_mode", it)
                        }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        icon = Icons.Default.Backup,
                        iconBg = Color(0xFFE0F2F1),
                        iconColor = Color(0xFF009688),
                        title = "Auto Backup",
                        subtitle = "Automatic data backup",
                        checked = autoBackup,
                        onCheckedChange = { 
                            autoBackup = it
                            savePreference("auto_backup", it)
                        }
                    )
                }
            }
            
            // Reports & Analytics Section
            item {
                SectionHeader("Reports & Analytics")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Assessment,
                        iconBg = Color(0xFFE8F5E9),
                        iconColor = Color(0xFF4CAF50),
                        title = "Reports",
                        subtitle = "View inventory and stock reports",
                        onClick = { /* TODO: Navigate to reports */ }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.BarChart,
                        iconBg = Color(0xFFF3E5F5),
                        iconColor = Color(0xFF9C27B0),
                        title = "Analytics",
                        subtitle = "Business insights and trends",
                        onClick = { /* TODO: Navigate to analytics */ }
                    )
                }
            }
            
            // Support & About Section
            item {
                SectionHeader("Support & Information")
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.HelpOutline,
                        iconBg = Color(0xFFE3F2FD),
                        iconColor = Color(0xFF2196F3),
                        title = "Help & Support",
                        subtitle = "Get help with using the app",
                        onClick = onNavigateToHelpSupport
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Info,
                        iconBg = Color(0xFFFFF3E0),
                        iconColor = Color(0xFFFF9800),
                        title = "About",
                        subtitle = "Version 1.0.0 • B2B Inventory",
                        onClick = { showAboutDialog = true }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Description,
                        iconBg = Color(0xFFF3E5F5),
                        iconColor = Color(0xFF9C27B0),
                        title = "Terms & Privacy",
                        subtitle = "Legal information",
                        onClick = { /* TODO: Navigate to terms */ }
                    )
                }
            }
            
            // Logout Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Logout",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Sign out of your account",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                        
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Footer padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = iconBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color(0xFF666666),
                maxLines = 1
            )
        }
        
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF999999),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = iconBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color(0xFF666666)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE0E0E0)
            )
        )
    }
}
