package com.b2binventory.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val title: String
) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    object Inventory : BottomNavItem("inventory", Icons.Default.Inventory, "Inventory")
    object Analytics : BottomNavItem("analytics", Icons.Default.Analytics, "Analytics")
    object Receipts : BottomNavItem("receipts", Icons.Default.Receipt, "Receipts")
    object More : BottomNavItem("more", Icons.Default.Menu, "More")
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    businessId: Long,
    userId: Long
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Inventory,
        BottomNavItem.Analytics,
        BottomNavItem.Receipts,
        BottomNavItem.More
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        items.forEach { item ->
            val isSelected = when {
                currentRoute?.startsWith("dashboard") == true -> item == BottomNavItem.Home
                currentRoute?.startsWith("inventory") == true || 
                currentRoute?.startsWith("add_inventory") == true ||
                currentRoute?.startsWith("product") == true ||
                currentRoute?.startsWith("edit_product") == true ||
                currentRoute?.startsWith("stock_adjustment") == true ||
                currentRoute?.startsWith("stock_history") == true -> item == BottomNavItem.Inventory
                currentRoute?.startsWith("analytics") == true -> item == BottomNavItem.Analytics
                currentRoute?.startsWith("receipts") == true -> item == BottomNavItem.Receipts
                currentRoute?.startsWith("settings") == true || 
                currentRoute?.startsWith("categories") == true ||
                currentRoute?.startsWith("more") == true -> item == BottomNavItem.More
                else -> false
            }
            
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = isSelected,
                onClick = {
                    when (item) {
                        BottomNavItem.Home -> {
                            navController.navigate(Screen.Dashboard.createRoute(businessId, userId)) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        BottomNavItem.Inventory -> {
                            navController.navigate(Screen.Inventory.createRoute(businessId, userId)) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        BottomNavItem.Analytics -> {
                            navController.navigate("analytics/$businessId/$userId") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        BottomNavItem.Receipts -> {
                            navController.navigate("receipts/$businessId/$userId") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        BottomNavItem.More -> {
                            navController.navigate(Screen.Settings.createRoute(businessId, userId)) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF2196F3),
                    selectedTextColor = Color(0xFF2196F3),
                    unselectedIconColor = Color(0xFF999999),
                    unselectedTextColor = Color(0xFF999999),
                    indicatorColor = Color(0xFF2196F3).copy(alpha = 0.1f)
                )
            )
        }
    }
}
