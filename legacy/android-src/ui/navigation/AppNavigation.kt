package com.b2binventory.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.b2binventory.app.data.SessionManager
import com.b2binventory.app.ui.auth.LoginScreenPro
import com.b2binventory.app.ui.auth.RegisterBusinessScreen
import com.b2binventory.app.ui.dashboard.DashboardScreenPro
import com.b2binventory.app.ui.inventory.*
import com.b2binventory.app.ui.settings.SettingsScreen
import com.b2binventory.app.ui.splash.SplashScreen
import com.b2binventory.app.ui.analytics.AnalyticsScreen
import com.b2binventory.app.ui.receipts.ReceiptsScreen

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
    object StockAdjustmentConfirm : Screen("stock_adjustment_confirm/{productId}/{businessId}/{userId}/{adjustmentType}/{quantity}/{reason}/{notes}") {
        fun createRoute(
            productId: Long, 
            businessId: Long, 
            userId: Long, 
            adjustmentType: String, 
            quantity: Int, 
            reason: String, 
            notes: String
        ) = "stock_adjustment_confirm/$productId/$businessId/$userId/$adjustmentType/$quantity/${java.net.URLEncoder.encode(reason, "UTF-8")}/${java.net.URLEncoder.encode(notes, "UTF-8")}"
    }
    object StockHistory : Screen("stock_history/{productId}/{businessId}") {
        fun createRoute(productId: Long, businessId: Long) = "stock_history/$productId/$businessId"
    }
    object StockHistoryAll : Screen("stock_history_all/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "stock_history_all/$businessId/$userId"
    }
    object LowStockProducts : Screen("low_stock_products/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "low_stock_products/$businessId/$userId"
    }
    object OutOfStockProducts : Screen("out_of_stock_products/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "out_of_stock_products/$businessId/$userId"
    }
    object CategoriesManagement : Screen("categories_management/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "categories_management/$businessId/$userId"
    }
    object BusinessProfile : Screen("business_profile/{businessId}") {
        fun createRoute(businessId: Long) = "business_profile/$businessId"
    }
    object TeamMembers : Screen("team_members/{businessId}") {
        fun createRoute(businessId: Long) = "team_members/$businessId"
    }
    object HelpSupport : Screen("help_support")
    object Settings : Screen("settings/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "settings/$businessId/$userId"
    }
    object Analytics : Screen("analytics/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "analytics/$businessId/$userId"
    }
    object Receipts : Screen("receipts/{businessId}/{userId}") {
        fun createRoute(businessId: Long, userId: Long) = "receipts/$businessId/$userId"
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
            val context = LocalContext.current
            val sessionManager = remember { SessionManager(context) }
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            val userName = sessionManager.getUserName() ?: "User"
            val userRole = sessionManager.getUserRole() ?: "Admin"
            
            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        navController = navController,
                        businessId = businessId,
                        userId = userId
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    DashboardScreenPro(
                        businessId = businessId,
                        userId = userId,
                        onNavigateToInventory = {
                            navController.navigate(Screen.Inventory.createRoute(businessId, userId))
                        },
                        onNavigateToReports = {
                            // Navigate to reports when implemented
                        },
                        onNavigateToParties = {
                            // Navigate to parties when implemented
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.createRoute(businessId, userId))
                        },
                        onNavigateToCategories = {
                            navController.navigate(Screen.CategoriesManagement.createRoute(businessId, userId))
                        },
                        onAddProduct = {
                            navController.navigate(Screen.AddInventory.createRoute(businessId, userId))
                        },
                        userName = userName,
                        userRole = userRole,
                        businessName = "My Business"
                    )
                }
            }
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
            
            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        navController = navController,
                        businessId = businessId,
                        userId = userId
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    InventoryListScreenSimple(
                        businessId = businessId,
                        onNavigateBack = { navController.popBackStack() },
                        onAddProduct = {
                            navController.navigate(Screen.AddInventory.createRoute(businessId, userId))
                        },
                        onProductClick = { product ->
                            navController.navigate(Screen.ProductDetails.createRoute(product.id ?: 0L, businessId, userId))
                        }
                    )
                }
            }
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
            
            AddProductScreenFunctional(
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onProductSaved = { navController.popBackStack() }
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
            val context = LocalContext.current
            val sessionManager = remember { SessionManager(context) }
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            StockAdjustmentScreen(
                productId = productId,
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConfirm = { adjType, qty, reason, notes ->
                    navController.navigate(
                        Screen.StockAdjustmentConfirm.createRoute(
                            productId, businessId, userId, adjType, qty, reason, notes
                        )
                    )
                }
            )
        }
        
        composable(
            route = Screen.StockAdjustmentConfirm.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType },
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType },
                navArgument("adjustmentType") { type = NavType.StringType },
                navArgument("quantity") { type = NavType.IntType },
                navArgument("reason") { type = NavType.StringType },
                navArgument("notes") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val sessionManager = remember { SessionManager(context) }
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            val adjustmentType = backStackEntry.arguments?.getString("adjustmentType") ?: "ADDED"
            val quantity = backStackEntry.arguments?.getInt("quantity") ?: 0
            val reason = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("reason") ?: "", 
                "UTF-8"
            )
            val notes = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("notes") ?: "", 
                "UTF-8"
            )
            val userName = sessionManager.getUserName() ?: "Unknown User"
            
            StockAdjustmentConfirmScreen(
                productId = productId,
                businessId = businessId,
                userId = userId,
                adjustmentType = adjustmentType,
                quantity = quantity,
                reason = reason,
                notes = if (notes.isEmpty()) null else notes,
                userName = userName,
                onConfirm = {
                    // Pop back to product details after successful update
                    navController.popBackStack(
                        Screen.ProductDetails.createRoute(productId, businessId, userId),
                        inclusive = false
                    )
                },
                onEditDetails = { navController.popBackStack() },
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
            route = Screen.StockHistoryAll.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            StockHistoryScreenComprehensive(
                businessId = businessId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.LowStockProducts.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            LowStockProductsScreen(
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRestock = { productId ->
                    navController.navigate(
                        Screen.StockAdjustment.createRoute(productId, businessId, userId)
                    )
                }
            )
        }
        
        composable(
            route = Screen.OutOfStockProducts.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            OutOfStockProductsScreen(
                businessId = businessId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRestock = { productId ->
                    navController.navigate(
                        Screen.StockAdjustment.createRoute(productId, businessId, userId)
                    )
                }
            )
        }
        
        composable(
            route = Screen.CategoriesManagement.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val sessionManager = remember { SessionManager(context) }
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            com.b2binventory.app.ui.categories.CategoriesManagementScreen(
                navController = navController,
                sessionManager = sessionManager
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
            
            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        navController = navController,
                        businessId = businessId,
                        userId = userId
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    SettingsScreen(
                        businessId = businessId,
                        userId = userId,
                        onNavigateBack = { navController.popBackStack() },
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigateToBusinessProfile = {
                            navController.navigate(Screen.BusinessProfile.createRoute(businessId))
                        },
                        onNavigateToTeamMembers = {
                            navController.navigate(Screen.TeamMembers.createRoute(businessId))
                        },
                        onNavigateToCategories = {
                            navController.navigate(Screen.CategoriesManagement.createRoute(businessId, userId))
                        },
                        onNavigateToHelpSupport = {
                            navController.navigate(Screen.HelpSupport.route)
                        }
                    )
                }
            }
        }
        
        composable(
            route = Screen.Analytics.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        navController = navController,
                        businessId = businessId,
                        userId = userId
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    AnalyticsScreen(
                        businessId = businessId,
                        userId = userId
                    )
                }
            }
        }
        
        composable(
            route = Screen.Receipts.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            
            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        navController = navController,
                        businessId = businessId,
                        userId = userId
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    ReceiptsScreen(
                        businessId = businessId,
                        userId = userId
                    )
                }
            }
        }
        
        // Business Profile Screen
        composable(
            route = Screen.BusinessProfile.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            
            com.b2binventory.app.ui.settings.BusinessProfileScreen(
                businessId = businessId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Team Members Screen
        composable(
            route = Screen.TeamMembers.route,
            arguments = listOf(
                navArgument("businessId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val businessId = backStackEntry.arguments?.getLong("businessId") ?: 0L
            
            com.b2binventory.app.ui.settings.TeamMembersScreen(
                businessId = businessId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Help & Support Screen
        composable(route = Screen.HelpSupport.route) {
            com.b2binventory.app.ui.settings.HelpSupportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
