package com.b2binventory.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------------------------------------------------------- common

@Serializable
data class Page<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class ErrorBody(
    val status: Int = 0,
    val code: String? = null,
    val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
)

// ---------------------------------------------------------------- auth

@Serializable
data class RegisterRequest(
    val businessName: String,
    val businessType: String? = null,
    val businessEmail: String? = null,
    val businessPhone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val password: String,
    val timezone: String? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String, val businessId: Long? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class SwitchBusinessRequest(val businessId: Long)

@Serializable
data class AcceptInvitationRequest(val code: String, val fullName: String? = null, val phone: String? = null, val password: String)

@Serializable
data class InvitationPreview(
    val businessName: String,
    val email: String,
    val role: String,
    val existingUser: Boolean,
    val expiresAt: String,
)

@Serializable
data class MembershipSummary(val membershipId: Long, val businessId: Long, val businessName: String, val role: String,
                             val businessType: String? = null)

@Serializable
data class Me(
    val userId: Long,
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val membershipId: Long,
    val businessId: Long,
    val businessName: String,
    val role: String,
    val permissions: Set<String> = emptySet(),
    val memberships: List<MembershipSummary> = emptyList(),
) {
    fun can(permission: String) = permission in permissions
}

@Serializable
data class AuthResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: Me)

@Serializable
data class UpdateProfileRequest(val fullName: String, val phone: String? = null)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

// ---------------------------------------------------------------- business

@Serializable
data class Business(
    val id: Long,
    val name: String,
    val businessType: String? = null,
    val logoUrl: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    /** Public code other businesses search for in Business network. */
    val businessCode: String? = null,
    val taxId: String? = null,
)

@Serializable
data class UpdateBusinessRequest(
    val name: String,
    val businessType: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    /** Empty clears it; null leaves it unchanged. */
    val taxId: String? = null,
)

@Serializable
data class BusinessSettings(
    val allowNegativeStock: Boolean = false,
    val defaultLowStockThreshold: Int = 5,
    val trackBatches: Boolean = false,
    val trackExpiry: Boolean = false,
    val trackSerials: Boolean = false,
    val trackDamagedReserved: Boolean = false,
    val notifyLowStock: Boolean = true,
    val timezone: String = "UTC",
    val currencyCode: String = "INR",
)

// ---------------------------------------------------------------- employees

@Serializable
data class Employee(
    val membershipId: Long,
    val userId: Long,
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val role: String,
    val status: String,
    val permissions: Set<String> = emptySet(),
    val overrides: Map<String, Boolean> = emptyMap(),
    val joinedAt: String? = null,
)

@Serializable
data class ChangeRoleRequest(val role: String)

@Serializable
data class PermissionOverridesRequest(val overrides: Map<String, Boolean>)

@Serializable
data class InviteRequest(val email: String, val role: String)

@Serializable
data class Invitation(
    val id: Long,
    val email: String,
    val role: String,
    val code: String,
    val status: String,
    val invitedBy: String? = null,
    val expiresAt: String,
    val createdAt: String,
)

@Serializable
data class PermissionInfo(val permission: String, val label: String)

@Serializable
data class PermissionCatalog(
    val permissions: List<PermissionInfo> = emptyList(),
    val roleDefaults: Map<String, Set<String>> = emptyMap(),
)

// ---------------------------------------------------------------- catalog

@Serializable
data class UnitDto(val id: Long, val code: String, val name: String, val decimals: Int = 0, val archived: Boolean = false)

@Serializable
data class UnitRequest(val code: String, val name: String, val decimals: Int)

@Serializable
data class Conversion(
    val id: Long,
    val fromUnit: UnitDto,
    val toUnit: UnitDto,
    val factor: Double,
    val productId: Long? = null,
    val productName: String? = null,
)

@Serializable
data class ConversionRequest(val fromUnitId: Long, val toUnitId: Long, val factor: Double, val productId: Long? = null)

@Serializable
data class ConvertResult(val quantity: Double, val fromUnit: String, val result: Double, val toUnit: String)

@Serializable
data class CategoryNode(
    val id: Long,
    val name: String,
    val description: String? = null,
    val parentId: Long? = null,
    val path: String = "",
    val archived: Boolean = false,
    val productCount: Long = 0,
    val depth: Int = 0,
    val children: List<CategoryNode> = emptyList(),
)

@Serializable
data class CategoryRequest(val name: String, val description: String? = null, val parentId: Long? = null)

@Serializable
data class AttributeDef(
    val id: Long,
    val key: String,
    val label: String,
    val fieldType: String,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class AttributeRequest(
    val key: String,
    val label: String,
    val fieldType: String,
    val required: Boolean,
    val options: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val categoryId: Long? = null,
)

@Serializable
data class FormSchema(
    val categoryId: Long? = null,
    val fields: List<AttributeDef> = emptyList(),
    val trackBatches: Boolean = false,
    val trackExpiry: Boolean = false,
    val trackSerials: Boolean = false,
    val trackDamagedReserved: Boolean = false,
)

// ---------------------------------------------------------------- products

@Serializable
data class StockTotals(
    val onHand: Double = 0.0,
    val reserved: Double = 0.0,
    val damaged: Double = 0.0,
    val available: Double = 0.0,
)

@Serializable
data class OpeningStock(
    val quantity: Double,
    val warehouseId: Long? = null,
    val lotNumber: String? = null,
    val expiryDate: String? = null,
    val serials: List<String>? = null,
)

@Serializable
data class ProductRequest(
    val name: String,
    val categoryId: Long? = null,
    val baseUnitId: Long? = null,
    val brand: String? = null,
    val model: String? = null,
    val description: String? = null,
    val minStock: Int? = null,
    val attributes: Map<String, JsonElement> = emptyMap(),
    val sku: String? = null,
    val barcode: String? = null,
    val trackBatches: Boolean = false,
    val trackSerials: Boolean = false,
    val openingStock: OpeningStock? = null,
    val salePrice: Double? = null,
    val purchasePrice: Double? = null,
    val taxRate: Double? = null,
    val supplierId: Long? = null,
)

@Serializable
data class VariantRequest(
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val attributes: Map<String, JsonElement> = emptyMap(),
    val minStock: Int? = null,
)

@Serializable
data class Variant(
    val id: Long,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val attributes: Map<String, JsonElement> = emptyMap(),
    val minStock: Int? = null,
    val effectiveMinStock: Int = 0,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val stock: StockTotals = StockTotals(),
    val status: String = "IN_STOCK",
)

@Serializable
data class ImageDto(val id: Long, val url: String)

@Serializable
data class Balance(
    val warehouseId: Long,
    val warehouseName: String,
    val variantId: Long,
    val variantName: String,
    val batchId: Long? = null,
    val lotNumber: String? = null,
    val expiryDate: String? = null,
    val onHand: Double = 0.0,
    val reserved: Double = 0.0,
    val damaged: Double = 0.0,
    val available: Double = 0.0,
)

/** unitCost is the weighted-average cost (AVERAGE) or, before any purchase, the purchase price (PURCHASE_PRICE). */
@Serializable
data class Pricing(
    val unitCost: Double? = null,
    val costSource: String? = null,
    val profitPerUnit: Double? = null,
    val marginPercent: Double? = null,
    val stockValueAtCost: Double? = null,
    val stockValueAtSellingPrice: Double? = null,
    val potentialProfit: Double? = null,
)

@Serializable
data class ProductSummary(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val unitCode: String = "",
    val sku: String? = null,
    val barcode: String? = null,
    val imageUrl: String? = null,
    val stock: StockTotals = StockTotals(),
    val effectiveMinStock: Int = 0,
    val status: String = "IN_STOCK",
    val variantCount: Int = 1,
    val archived: Boolean = false,
    val updatedAt: String? = null,
    val salePrice: Double? = null,
    /** Only sent to members who can see costs. */
    val purchasePrice: Double? = null,
)

@Serializable
data class ProductDetail(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val description: String? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val categoryPath: String? = null,
    val baseUnit: UnitDto,
    val minStock: Int? = null,
    val effectiveMinStock: Int = 0,
    val attributes: Map<String, JsonElement> = emptyMap(),
    val trackBatches: Boolean = false,
    val trackSerials: Boolean = false,
    val archived: Boolean = false,
    val stock: StockTotals = StockTotals(),
    val status: String = "IN_STOCK",
    val variants: List<Variant> = emptyList(),
    val images: List<ImageDto> = emptyList(),
    val balances: List<Balance> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val salePrice: Double? = null,
    val purchasePrice: Double? = null,
    val averageCost: Double? = null,
    val taxRate: Double? = null,
    val supplierId: Long? = null,
    val supplierName: String? = null,
    /** Margin calculations; omitted for members who cannot see costs. */
    val pricing: Pricing? = null,
) {
    val defaultVariant: Variant? get() = variants.firstOrNull { it.isDefault }
}

// ---------------------------------------------------------------- stock

@Serializable
data class StockOperationRequest(
    val type: String,
    val variantId: Long,
    val warehouseId: Long? = null,
    val quantity: Double,
    val reason: String? = null,
    val notes: String? = null,
    val batchId: Long? = null,
    val lotNumber: String? = null,
    val expiryDate: String? = null,
    val serials: List<String>? = null,
    val fromDamaged: Boolean = false,
    val clientRequestId: String? = null,
)

@Serializable
data class TransferRequest(
    val variantId: Long,
    val fromWarehouseId: Long,
    val toWarehouseId: Long,
    val quantity: Double,
    val batchId: Long? = null,
    val lotNumber: String? = null,
    val serials: List<String>? = null,
    val reason: String? = null,
    val notes: String? = null,
    val clientRequestId: String? = null,
)

@Serializable
data class Movement(
    val id: Long,
    val type: String,
    val bucket: String = "ON_HAND",
    val productId: Long,
    val productName: String,
    val variantId: Long,
    val variantName: String,
    val sku: String? = null,
    val warehouseId: Long,
    val warehouseName: String,
    val batchId: Long? = null,
    val lotNumber: String? = null,
    val quantity: Double,
    val quantityBefore: Double,
    val quantityAfter: Double,
    val unitCode: String = "",
    val reason: String? = null,
    val notes: String? = null,
    val serials: List<String>? = null,
    val actorId: Long? = null,
    val actorName: String = "",
    val transferGroupId: String? = null,
    val stockCountId: Long? = null,
    val createdAt: String,
)

@Serializable
data class OperationResult(val movements: List<Movement> = emptyList(), val balances: List<Balance> = emptyList())

@Serializable
data class BatchDto(val id: Long, val lotNumber: String, val expiryDate: String? = null, val onHand: Double = 0.0)

@Serializable
data class SerialDto(
    val id: Long,
    val serial: String,
    val status: String,
    val warehouseId: Long? = null,
    val warehouseName: String? = null,
)

@Serializable
data class Warehouse(
    val id: Long,
    val name: String,
    val code: String,
    val address: String? = null,
    val branch: Boolean = false,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val productCount: Long = 0,
    val totalQuantity: Double = 0.0,
)

@Serializable
data class WarehouseRequest(val name: String, val code: String, val address: String? = null, val branch: Boolean = false)

@Serializable
data class LocationDto(val id: Long, val warehouseId: Long, val code: String, val description: String? = null, val archived: Boolean = false)

@Serializable
data class LocationRequest(val code: String, val description: String? = null)

@Serializable
data class StockCountRequest(val warehouseId: Long, val categoryId: Long? = null, val notes: String? = null)

@Serializable
data class CountLineUpdate(val lineId: Long, val countedQty: Double?)

@Serializable
data class CountLinesRequest(val lines: List<CountLineUpdate>)

@Serializable
data class CountLine(
    val id: Long,
    val variantId: Long,
    val productId: Long,
    val productName: String,
    val variantName: String,
    val sku: String? = null,
    val unitCode: String = "",
    val batchId: Long? = null,
    val lotNumber: String? = null,
    val expectedQty: Double = 0.0,
    val countedQty: Double? = null,
    val difference: Double? = null,
)

@Serializable
data class StockCountSummary(
    val id: Long,
    val warehouseId: Long,
    val warehouseName: String,
    val status: String,
    val notes: String? = null,
    val createdBy: String = "",
    val createdAt: String,
    val reconciledAt: String? = null,
    val lineCount: Int = 0,
    val countedLines: Int = 0,
)

@Serializable
data class StockCountDetail(val summary: StockCountSummary, val lines: List<CountLine> = emptyList())

@Serializable
data class ReconcileResult(val summary: StockCountSummary, val adjustedLines: Int)

// ---------------------------------------------------------------- dashboard & reports

@Serializable
data class StockAlert(
    val productId: Long,
    val productName: String,
    val unit: String = "",
    val available: Double = 0.0,
    val minStock: Int = 0,
    val status: String,
)

@Serializable
data class CategorySummary(val categoryId: Long? = null, val categoryName: String, val productCount: Long, val quantity: Double)

@Serializable
data class WarehouseSummary(val warehouseId: Long, val warehouseName: String, val productCount: Long, val quantity: Double)

@Serializable
data class Dashboard(
    val activeProducts: Long = 0,
    val totalQuantity: Double = 0.0,
    val lowStockCount: Long = 0,
    val outOfStockCount: Long = 0,
    val lowStockItems: List<StockAlert> = emptyList(),
    val outOfStockItems: List<StockAlert> = emptyList(),
    val recentMovements: List<Movement> = emptyList(),
    val categories: List<CategorySummary> = emptyList(),
    val warehouses: List<WarehouseSummary> = emptyList(),
    val unreadNotifications: Long = 0,
    val recentlyUpdated: List<RecentProduct> = emptyList(),
    /** Only sent to members who can view analytics. */
    val sales: SalesHighlights? = null,
    /** Products with the most available stock, highest first. */
    val mostStocked: List<RecentProduct> = emptyList(),
)

@Serializable
data class RecentProduct(
    val productId: Long,
    val productName: String,
    val unit: String = "",
    val available: Double = 0.0,
    val status: String = "IN_STOCK",
    val updatedAt: String? = null,
)

@Serializable
data class SalesSnapshot(val sales: Long = 0, val units: Double = 0.0, val revenue: Double = 0.0, val grossProfit: Double? = null,
                         val currencyCode: String = "INR")

@Serializable
data class SalesHighlights(
    val today: SalesSnapshot = SalesSnapshot(),
    val month: SalesSnapshot = SalesSnapshot(),
    val monthBestSeller: ProductRef? = null,
    val monthLowestSeller: ProductRef? = null,
    val monthProductsWithNoSales: Long = 0,
    val inventory: InventoryValue = InventoryValue(),
)

/** Stock movement totals for a period (GET api/reports/movement-summary). */
@Serializable
data class MovementSummary(
    val from: String? = null,
    val to: String? = null,
    val totalIn: Double = 0.0,
    val totalOut: Double = 0.0,
    val movementCount: Long = 0,
    val byType: List<TypeTotal> = emptyList(),
    val fastMoving: List<ProductMovement> = emptyList(),
    val slowMoving: List<ProductMovement> = emptyList(),
)

@Serializable
data class TypeTotal(val type: String, val count: Long = 0, val quantity: Double = 0.0)

@Serializable
data class ProductMovement(val productId: Long, val productName: String, val unit: String = "", val quantityOut: Double = 0.0,
                           val available: Double = 0.0)

@Serializable
data class InventoryRow(
    val productId: Long,
    val variantId: Long,
    val productName: String,
    val variantName: String,
    val sku: String? = null,
    val barcode: String? = null,
    val category: String? = null,
    val unit: String = "",
    val onHand: Double = 0.0,
    val reserved: Double = 0.0,
    val damaged: Double = 0.0,
    val available: Double = 0.0,
    val minStock: Int = 0,
    val status: String,
)

@Serializable
data class NotificationDto(
    val id: Long,
    val type: String,
    val title: String,
    val body: String? = null,
    val payload: Map<String, JsonElement> = emptyMap(),
    val read: Boolean = false,
    val createdAt: String,
)

@Serializable
data class UnreadCount(@SerialName("count") val count: Long = 0)

// ---------------------------------------------------------------- sales

/** Prices exclude tax: net = qty x price - discount, tax is charged on the net. */
@Serializable
data class SaleLineRequest(
    val variantId: Long,
    val warehouseId: Long? = null,
    val quantity: Double,
    val unitPrice: Double,
    val discount: Double? = null,
    val taxRate: Double? = null,
    val batchId: Long? = null,
    val serials: List<String>? = null,
)

@Serializable
data class PaymentInput(val method: String, val amount: Double, val reference: String? = null)

@Serializable
data class SaleRequest(
    val items: List<SaleLineRequest>,
    val customerId: Long? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val saveCustomer: Boolean = false,
    val invoiceNumber: String? = null,
    /** ISO instant; null means now. */
    val soldAt: String? = null,
    val payment: PaymentInput? = null,
    val notes: String? = null,
    val clientRequestId: String,
)

@Serializable
data class CancelSaleRequest(val reason: String, val refundMethod: String? = null)

@Serializable
data class PaymentRequest(
    val amount: Double,
    val method: String,
    val reference: String? = null,
    val clientRequestId: String,
)

@Serializable
data class ReturnLineRequest(val saleItemId: Long, val quantity: Double, val resalable: Boolean, val serials: List<String>? = null)

@Serializable
data class ReturnRequest(
    val items: List<ReturnLineRequest>,
    val reason: String,
    val refundAmount: Double? = null,
    val refundMethod: String? = null,
    val notes: String? = null,
    val clientRequestId: String,
)

@Serializable
data class SaleItem(
    val id: Long,
    val lineNo: Int = 1,
    val productId: Long,
    val productName: String,
    val variantId: Long,
    val variantName: String = "",
    val sku: String? = null,
    val unitCode: String = "",
    val warehouseId: Long? = null,
    val warehouseName: String = "",
    val lotNumber: String? = null,
    val quantity: Double,
    val returnedQuantity: Double = 0.0,
    val unitPrice: Double,
    val grossAmount: Double = 0.0,
    val discount: Double = 0.0,
    val netAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val lineTotal: Double = 0.0,
    /** Cost fields are only sent to members who can see costs. */
    val unitCost: Double? = null,
    val costAmount: Double? = null,
    val grossProfit: Double? = null,
    val serials: List<String>? = null,
)

@Serializable
data class PaymentDto(
    val id: Long,
    val kind: String,
    val amount: Double,
    val method: String,
    val reference: String? = null,
    val paidAt: String,
    val recordedBy: String = "",
)

@Serializable
data class ReturnItemDto(val saleItemId: Long, val productName: String, val quantity: Double, val resalable: Boolean,
                         val netAmount: Double = 0.0, val taxAmount: Double = 0.0)

@Serializable
data class SaleReturnDto(
    val id: Long,
    val returnNumber: String,
    val returnedAt: String,
    val reason: String,
    val creditAmount: Double = 0.0,
    val refundAmount: Double = 0.0,
    val refundMethod: String? = null,
    val notes: String? = null,
    val recordedBy: String = "",
    val items: List<ReturnItemDto> = emptyList(),
)

@Serializable
data class StockAfter(val productId: Long, val productName: String, val unitCode: String = "", val available: Double = 0.0,
                      val status: String = "IN_STOCK")

@Serializable
data class SaleSummary(
    val id: Long,
    val invoiceNumber: String,
    val soldAt: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val itemCount: Int = 0,
    val totalQuantity: Double = 0.0,
    val products: String = "",
    val total: Double = 0.0,
    val returnedTotal: Double = 0.0,
    val amountPaid: Double = 0.0,
    val outstanding: Double = 0.0,
    val paymentStatus: String = "UNPAID",
    val status: String = "COMPLETED",
)

@Serializable
data class Sale(
    val id: Long,
    val invoiceNumber: String,
    val status: String,
    val soldAt: String,
    val customerId: Long? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val subtotal: Double = 0.0,
    val discountTotal: Double = 0.0,
    val taxTotal: Double = 0.0,
    val total: Double = 0.0,
    val returnedTotal: Double = 0.0,
    val amountPaid: Double = 0.0,
    val outstanding: Double = 0.0,
    val paymentStatus: String = "UNPAID",
    val netSales: Double = 0.0,
    val costTotal: Double? = null,
    val grossProfit: Double? = null,
    val notes: String? = null,
    val soldBy: String = "",
    val cancelReason: String? = null,
    val cancelledAt: String? = null,
    val cancelledBy: String? = null,
    val createdAt: String? = null,
    val items: List<SaleItem> = emptyList(),
    val payments: List<PaymentDto> = emptyList(),
    val returns: List<SaleReturnDto> = emptyList(),
    val stock: List<StockAfter> = emptyList(),
)

// ---------------------------------------------------------------- purchases & parties

@Serializable
data class PurchaseLineRequest(
    val variantId: Long,
    val warehouseId: Long? = null,
    val quantity: Double,
    val unitCost: Double,
    val discount: Double? = null,
    val taxRate: Double? = null,
    val lotNumber: String? = null,
    val expiryDate: String? = null,
    val serials: List<String>? = null,
)

@Serializable
data class PurchaseRequest(
    val items: List<PurchaseLineRequest>,
    val supplierId: Long? = null,
    val supplierName: String? = null,
    val saveSupplier: Boolean = false,
    val referenceNumber: String? = null,
    val purchasedAt: String? = null,
    val payment: PaymentInput? = null,
    val notes: String? = null,
    val clientRequestId: String,
)

@Serializable
data class CancelPurchaseRequest(val reason: String)

@Serializable
data class PurchaseItem(
    val id: Long,
    val lineNo: Int = 1,
    val productId: Long,
    val productName: String,
    val variantId: Long,
    val variantName: String = "",
    val sku: String? = null,
    val unitCode: String = "",
    val warehouseName: String = "",
    val lotNumber: String? = null,
    val expiryDate: String? = null,
    val quantity: Double,
    val unitCost: Double,
    val grossAmount: Double = 0.0,
    val discount: Double = 0.0,
    val netAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val lineTotal: Double = 0.0,
    val landedUnitCost: Double = 0.0,
)

@Serializable
data class PurchaseSummary(
    val id: Long,
    val purchaseNumber: String,
    val referenceNumber: String? = null,
    val purchasedAt: String,
    val supplierName: String? = null,
    val itemCount: Int = 0,
    val totalQuantity: Double = 0.0,
    val products: String = "",
    val total: Double = 0.0,
    val amountPaid: Double = 0.0,
    val outstanding: Double = 0.0,
    val paymentStatus: String = "UNPAID",
    val status: String = "RECEIVED",
)

@Serializable
data class Purchase(
    val id: Long,
    val purchaseNumber: String,
    val referenceNumber: String? = null,
    val status: String,
    val purchasedAt: String,
    val supplierId: Long? = null,
    val supplierName: String? = null,
    val subtotal: Double = 0.0,
    val discountTotal: Double = 0.0,
    val taxTotal: Double = 0.0,
    val total: Double = 0.0,
    val amountPaid: Double = 0.0,
    val outstanding: Double = 0.0,
    val paymentStatus: String = "UNPAID",
    val notes: String? = null,
    val recordedBy: String = "",
    val cancelReason: String? = null,
    val cancelledAt: String? = null,
    val cancelledBy: String? = null,
    val items: List<PurchaseItem> = emptyList(),
    val payments: List<PaymentDto> = emptyList(),
)

@Serializable
data class PartyRequest(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val taxId: String? = null,
    val notes: String? = null,
)

/** A customer or a supplier. */
@Serializable
data class Party(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val taxId: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
)

// ---------------------------------------------------------------- analytics

@Serializable
data class AnalyticsPeriod(
    val from: String,
    val to: String,
    val days: Int = 1,
    val previousFrom: String? = null,
    val previousTo: String? = null,
    val timezone: String = "UTC",
    val currencyCode: String = "INR",
)

/** growthPercent is null when the previous period has nothing to compare with. */
@Serializable
data class Metric(val value: Double = 0.0, val previous: Double? = null, val growthPercent: Double? = null)

@Serializable
data class ProductRef(val productId: Long, val name: String, val unit: String = "", val units: Double = 0.0, val revenue: Double = 0.0)

/** costOfGoodsSold and grossProfit are null when some sold item has no known cost. */
@Serializable
data class Financials(
    val grossSales: Double = 0.0,
    val discounts: Double = 0.0,
    val returns: Double = 0.0,
    val netSales: Double = 0.0,
    val taxCollected: Double = 0.0,
    val costOfGoodsSold: Double? = null,
    val grossProfit: Double? = null,
    val marginPercent: Double? = null,
    val profitAvailable: Boolean = false,
    val linesWithoutCost: Long = 0,
)

@Serializable
data class InventoryValue(
    val valueAtCost: Double = 0.0,
    val valueAtSellingPrice: Double = 0.0,
    val potentialProfit: Double = 0.0,
    val productsWithoutCost: Long = 0,
    val productsWithoutPrice: Long = 0,
)

@Serializable
data class PurchaseTotals(val purchases: Long = 0, val unitsReceived: Double = 0.0, val totalSpent: Double = 0.0,
                          val previousTotalSpent: Double = 0.0)

@Serializable
data class AnalyticsOverview(
    val period: AnalyticsPeriod,
    val sales: Metric = Metric(),
    val units: Metric = Metric(),
    /** Net sales revenue (after discounts and returns, excluding tax). */
    val revenue: Metric = Metric(),
    val grossProfit: Metric? = null,
    val financials: Financials = Financials(),
    val averageDailyUnits: Double = 0.0,
    val averageDailyRevenue: Double = 0.0,
    val bestSeller: ProductRef? = null,
    val lowestSeller: ProductRef? = null,
    val productsWithNoSales: Long = 0,
    val productsSold: Long = 0,
    val activeProducts: Long = 0,
    val inventory: InventoryValue = InventoryValue(),
    val purchases: PurchaseTotals = PurchaseTotals(),
)

@Serializable
data class ProductPerformance(
    val rank: Int,
    val productId: Long,
    val name: String,
    val sku: String? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val unit: String = "",
    val unitsSold: Double = 0.0,
    val revenue: Double = 0.0,
    val grossProfit: Double? = null,
    val marginPercent: Double? = null,
    val salesCount: Long = 0,
    val daysWithSales: Int = 0,
    val averageDailySales: Double = 0.0,
    val currentStock: Double = 0.0,
    val stockStatus: String = "IN_STOCK",
    val previousUnits: Double = 0.0,
    val growthPercent: Double? = null,
    val performance: String = "NO_DATA",
    val labels: List<String> = emptyList(),
    val lastSoldAt: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class ProductAnalytics(val period: AnalyticsPeriod, val products: List<ProductPerformance> = emptyList())

@Serializable
data class TrendPoint(val start: String, val units: Double = 0.0, val revenue: Double = 0.0, val grossProfit: Double? = null,
                      val sales: Long = 0)

@Serializable
data class SalesTrend(val period: AnalyticsPeriod, val granularity: String = "day", val points: List<TrendPoint> = emptyList())

@Serializable
data class CategoryPerformance(
    val categoryId: Long? = null,
    val categoryName: String,
    val units: Double = 0.0,
    val revenue: Double = 0.0,
    val grossProfit: Double? = null,
    val productsSold: Long = 0,
    val previousUnits: Double = 0.0,
    val growthPercent: Double? = null,
)

@Serializable
data class CategoryAnalytics(val period: AnalyticsPeriod, val categories: List<CategoryPerformance> = emptyList())

@Serializable
data class Insight(
    val kind: String,
    val severity: String = "INFO",
    val title: String,
    val message: String,
    val productId: Long? = null,
    val action: String? = null,
)

@Serializable
data class Insights(val period: AnalyticsPeriod, val insights: List<Insight> = emptyList())
