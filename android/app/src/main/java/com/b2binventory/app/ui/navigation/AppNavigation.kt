package com.b2binventory.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.b2binventory.app.ui.auth.LoginScreenPro
import com.b2binventory.app.ui.auth.RegisterBusinessScreen
import com.b2binventory.app.ui.dashboard.DashboardScreen
import com.b2binventory.app.ui.inventory.*
import com.b2binventory.app.ui.settings.SettingsScreen
import com.b2binventory.app.ui.splash.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "dashboard/$businessId/$userId"
    }
    object Inventory : Screen("inventory/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "inventory/$businessId/$userId"
    }
    object AddInventory : Screen("add_inventory/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "add_inventory/$businessId/$userId"
    }
    object ProductDetails : Screen("product/{productId}/{businessId}/{userId}") {
        fun createRoute(productId: Long, businessId: Long, userId: Long) = "product/$productId/$businessId/$userId"
    }
    object EditProduct : Screen("edit_product/{productId}/{businessId}/{userId}") {
        fun createRoute(productId: Long, businessId: Long, userId: Long) = "edit_product/$productId/$businessId/$userId"
    }
    object StockAdjustment : Screen("stock_adjustment/{productId}/{businessId}/{userId}") {
        fun createRoute(productId: Long, businessId: Long, userId: Long) = "stock_adjustment/$productId/$businessId/$userId"
    }
    object StockHistory : Screen("stock_history/{productId}/{businessId}") {
        fun createRoute(productId: Long, businessId: Long) = "stock_history/$productId/$businessId"
    }
    object Settings : Screen("settings/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "settings/$businessId/$userId"
    }
}

@Composable
fun AppNavigation(
    startDestination: String = Screen.Splash.route,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Login.route) {
            LoginScreenPro(
                onLoginSuccess = { businessId, userId ->
                    navController.navigate(Screen.Dashboard.createRoute(businessId, userId)) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }
        
        composable(Screen.Register.route) {
            RegisterBusinessScreen(
                onRegisterSuccess = { businessId, userId ->
                    navController.navigate(Screen.Dashboard.createRoute(businessId, userId)) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.Dashboard.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            DashboardScreen(
                businessId = businessId,
                userId = userId,
                onNavigateToInventory = {
                    navController.navigate(Screen.Inventory.createRoute(businessId, userId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.createRoute(businessId, userId))
                }
            )
        }
        
        composable(
            route = Screen.Inventory.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            InventoryScreen(
                businessId = businessId,
                userId = userId,
                onNavigateToAddInventory = {
                    navController.navigate(Screen.AddInventory.createRoute(businessId, userId))
                },
                onNavigateToProductDetails = { productId ->
                    navController.navigate(Screen.ProductDetails.createRoute(productId, businessId, userId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.AddInventory.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            AddInventoryScreen(
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.ProductDetails.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType },
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            ProductDetailsScreen(
                productId = productId,
                businessId = businessId,
                userId = userId,
                onNavigateToStockAdjustment = {
                    navController.navigate(Screen.StockAdjustment.createRoute(productId, businessId, userId))
                },
                onNavigateToStockHistory = {
                    navController.navigate(Screen.StockHistory.createRoute(productId, businessId))
                },
                onNavigateToEdit = {
                    navController.navigate(Screen.EditProduct.createRoute(productId, businessId, userId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.EditProduct.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType },
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            EditProductScreen(
                productId = productId,
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.StockAdjustment.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType },
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            StockAdjustmentScreen(
                productId = productId,
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.StockHistory.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType },
                navArgument("businessId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            
            StockHistoryScreen(
                productId = productId,
                businessId = businessId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Settings.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            SettingsScreen(
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
