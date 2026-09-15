# Integration Steps for Universal Business App

## ✅ What Has Been Created

### New UI Screens (Android)
1. **VouchersScreen.kt** - Complete vouchers module with create & view options
2. **ReportsScreen.kt** - Comprehensive reports with all business analytics
3. **PartiesScreen.kt** - Customer & supplier management with filtering
4. **DashboardScreenNew.kt** - BusyApp-style dashboard with quick actions
5. **SettingsScreen.kt** - Already exists with good structure

### Backend
- **WebConfig.java** - CORS configuration (already created)
- All existing controllers support multi-business architecture

## 🔧 Integration Steps

### Step 1: Update Navigation (AppNavigation.kt)

Replace the bottom navigation items to use the new structure:

```kotlin
val items = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Default.Home, "Home"),
    BottomNavItem(Screen.Vouchers, Icons.Default.Receipt, "Vouchers"),
    BottomNavItem(Screen.Reports, Icons.Default.BarChart, "Reports"),
    BottomNavItem(Screen.Parties, Icons.Default.People, "Parties"),
    BottomNavItem(Screen.Settings, Icons.Default.MoreHoriz, "More")
)
```

Add these screen routes:
```kotlin
object Vouchers : Screen("vouchers", "Vouchers")
object Reports : Screen("reports", "Reports")
object Parties : Screen("parties", "Parties")
```

Add composable routes for the new screens:
```kotlin
composable(Screen.Vouchers.route) {
    VouchersScreen(businessId = businessId, onNavigateBack = { navController.popBackStack() })
}
composable(Screen.Reports.route) {
    ReportsScreen(
        businessId = businessId,
        onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
        onNavigateBack = { navController.popBackStack() }
    )
}
composable(Screen.Parties.route) {
    PartiesScreen(businessId = businessId, onNavigateBack = { navController.popBackStack() })
}
```

### Step 2: Replace Dashboard

**Option A:** Replace existing DashboardScreen with DashboardScreenNew
**Option B:** Keep both and add a toggle in settings

To replace:
1. Rename `DashboardScreen.kt` to `DashboardScreenOld.kt`
2. Rename `DashboardScreenNew.kt` to `DashboardScreen.kt`
3. Update function signature to match:

```kotlin
@Composable
fun DashboardScreen(
    businessId: Long,
    userId: Long,
    onNavigateToInventory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    DashboardScreenNew(
        businessId = businessId,
        userId = userId,
        businessName = "Demo Business", // TODO: Get from SessionManager
        onNavigateToInventory = onNavigateToInventory,
        onNavigateToVouchers = { /* TODO: navigate */ },
        onNavigateToReports = { /* TODO: navigate */ },
        onNavigateToParties = { /* TODO: navigate */ }
    )
}
```

### Step 3: Add Business Name to Session

Update `SessionManager.kt` to store business name:

```kotlin
data class Session(
    val businessId: Long,
    val userId: Long,
    val name: String,
    val email: String,
    val role: String,
    val businessName: String  // Add this
)
```

Update login/register to fetch and store business name.

### Step 4: Backend - Add Party/Customer APIs

Create new controllers:
- `PartyController.java` - Customer/Supplier management
- `VoucherController.java` - Invoice, Purchase, Sales Return, etc.
- `ReportController.java` - Business reports and analytics

### Step 5: Update Models (Add to Models.kt)

```kotlin
data class Party(
    val id: Long? = null,
    val name: String,
    val type: String, // "CUSTOMER" or "SUPPLIER"
    val contactPerson: String? = null,
    val mobile: String? = null,
    val email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val gstNumber: String? = null,
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0
)

data class Voucher(
    val id: Long? = null,
    val type: String, // "INVOICE", "PURCHASE", "SALES_RETURN", etc.
    val voucherNumber: String,
    val date: String,
    val partyId: Long,
    val partyName: String,
    val amount: Double,
    val items: List<VoucherItem>,
    val notes: String? = null
)

data class VoucherItem(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val rate: Double,
    val amount: Double
)
```

### Step 6: Update ApiService.kt

Add new endpoints:

```kotlin
// Parties
@GET("api/parties")
suspend fun getParties(
    @Query("businessId") businessId: Long,
    @Query("type") type: String? = null
): List<Party>

@POST("api/parties")
suspend fun createParty(
    @Query("businessId") businessId: Long,
    @Body party: Party
): Party

// Vouchers
@GET("api/vouchers")
suspend fun getVouchers(
    @Query("businessId") businessId: Long,
    @Query("type") type: String? = null
): List<Voucher>

@POST("api/vouchers")
suspend fun createVoucher(
    @Query("businessId") businessId: Long,
    @Query("userId") userId: Long,
    @Body voucher: Voucher
): Voucher

// Reports
@GET("api/reports/daily-summary")
suspend fun getDailySummary(
    @Query("businessId") businessId: Long,
    @Query("date") date: String
): Map<String, Any>

@GET("api/reports/outstanding")
suspend fun getOutstandingReport(
    @Query("businessId") businessId: Long
): List<Party>
```

## 🎨 UI Customization

### Make Categories Universal

Update `AddInventoryScreen.kt`:

```kotlin
val categories = listOf(
    "Electronics", "Clothing", "Groceries", 
    "Hardware", "Furniture", "Stationery",
    "Medical", "Cosmetics", "Toys", "Books",
    "Ply", "Mica", "Door", "Sanitary", "Paint",
    "Other"
)
```

Make it configurable per business type in settings.

### Add Dynamic Fields

Based on category selection, show relevant fields:
- Electronics: Model, Warranty, Specifications
- Clothing: Size, Color, Material
- Groceries: Expiry Date, Batch Number
- Ply/Mica: Thickness, Size
- Services: Duration, Service Type

## 🔄 Testing Steps

1. **Restart Backend**
   ```bash
   cd backend
   .\start-backend.bat
   ```

2. **Rebuild Android App**
   ```bash
   cd android
   .\gradlew.bat assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

3. **Test Features**
   - ✅ Login/Register
   - ✅ Dashboard loads with all cards
   - ✅ Navigate to Vouchers
   - ✅ Navigate to Reports
   - ✅ Navigate to Parties
   - ✅ Navigate to More (Settings)
   - ✅ Add Inventory with different categories
   - ✅ Test bottom navigation

## 📋 TODO List

### High Priority
- [ ] Integrate new screens into navigation
- [ ] Add Party/Customer management backend
- [ ] Add Voucher (Invoice, Purchase) backend
- [ ] Update SessionManager with business name
- [ ] Test all navigation flows

### Medium Priority
- [ ] Add Reports backend APIs
- [ ] Implement invoice creation flow
- [ ] Add party selection in vouchers
- [ ] Implement outstanding reports
- [ ] Add PDF generation for invoices

### Low Priority
- [ ] Add multi-company support
- [ ] Implement approval workflows
- [ ] Add smart alerts
- [ ] Add dashboard customization
- [ ] Add export to Excel

## 🚀 Quick Start (For Testing)

1. The new UI screens are ready to use
2. They show sample/mock data for demonstration
3. Backend APIs need to be created for full functionality
4. Navigation needs to be updated to include new screens

## 📝 Notes

- All new screens follow Material Design 3
- UI is inspired by BusyApp but improved
- Designed for ANY business type (universal)
- Ready for multi-language support
- Responsive and touch-friendly

---

**Current Status:** ✅ UI Complete | ⏳ Backend APIs Pending | ⏳ Navigation Integration Pending
