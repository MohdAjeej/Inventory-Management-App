package com.b2binventory.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("api/auth/refresh")
    fun refresh(@Body body: RefreshRequest): retrofit2.Call<AuthResponse>
}

/** Every endpoint derives the business from the bearer token; no business id is ever sent. */
interface ApiService {

    // ---- auth
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("api/auth/logout") suspend fun logout(@Body body: RefreshRequest): Response<Unit>
    @GET("api/auth/invitations/{code}") suspend fun previewInvitation(@Path("code") code: String): InvitationPreview
    @POST("api/auth/accept-invitation") suspend fun acceptInvitation(@Body body: AcceptInvitationRequest): AuthResponse
    @POST("api/auth/switch-business") suspend fun switchBusiness(@Body body: SwitchBusinessRequest): AuthResponse
    @GET("api/auth/me") suspend fun me(): Me
    @PUT("api/auth/me") suspend fun updateProfile(@Body body: UpdateProfileRequest): Me
    @POST("api/auth/change-password") suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    // ---- business
    @GET("api/business") suspend fun business(): Business
    @PUT("api/business") suspend fun updateBusiness(@Body body: UpdateBusinessRequest): Business
    @Multipart @POST("api/business/logo") suspend fun uploadLogo(@Part file: MultipartBody.Part): Business
    @GET("api/business/settings") suspend fun settings(): BusinessSettings
    @PUT("api/business/settings") suspend fun updateSettings(@Body body: BusinessSettings): BusinessSettings

    // ---- employees
    @GET("api/permissions") suspend fun permissionCatalog(): PermissionCatalog
    @GET("api/employees") suspend fun employees(@Query("q") q: String? = null, @Query("size") size: Int = 200): Page<Employee>
    @GET("api/employees/{id}") suspend fun employee(@Path("id") id: Long): Employee
    @PUT("api/employees/{id}/role") suspend fun changeRole(@Path("id") id: Long, @Body body: ChangeRoleRequest): Employee
    @PUT("api/employees/{id}/permissions") suspend fun setPermissions(@Path("id") id: Long, @Body body: PermissionOverridesRequest): Employee
    @POST("api/employees/{id}/deactivate") suspend fun deactivate(@Path("id") id: Long): Employee
    @POST("api/employees/{id}/reactivate") suspend fun reactivate(@Path("id") id: Long): Employee
    @GET("api/invitations") suspend fun invitations(@Query("status") status: String? = "PENDING", @Query("size") size: Int = 100): Page<Invitation>
    @POST("api/invitations") suspend fun invite(@Body body: InviteRequest): Invitation
    @DELETE("api/invitations/{id}") suspend fun cancelInvitation(@Path("id") id: Long): Response<Unit>

    // ---- units
    @GET("api/units") suspend fun units(@Query("includeArchived") includeArchived: Boolean = false): List<UnitDto>
    @POST("api/units") suspend fun createUnit(@Body body: UnitRequest): UnitDto
    @PUT("api/units/{id}") suspend fun updateUnit(@Path("id") id: Long, @Body body: UnitRequest): UnitDto
    @POST("api/units/{id}/archive") suspend fun archiveUnit(@Path("id") id: Long): UnitDto
    @POST("api/units/{id}/restore") suspend fun restoreUnit(@Path("id") id: Long): UnitDto
    @GET("api/unit-conversions") suspend fun conversions(): List<Conversion>
    @POST("api/unit-conversions") suspend fun createConversion(@Body body: ConversionRequest): Conversion
    @DELETE("api/unit-conversions/{id}") suspend fun deleteConversion(@Path("id") id: Long): Response<Unit>

    // ---- categories & attributes
    @GET("api/categories") suspend fun categories(@Query("includeArchived") includeArchived: Boolean = false): List<CategoryNode>
    @POST("api/categories") suspend fun createCategory(@Body body: CategoryRequest): CategoryNode
    @PUT("api/categories/{id}") suspend fun updateCategory(@Path("id") id: Long, @Body body: CategoryRequest): CategoryNode
    @POST("api/categories/{id}/archive") suspend fun archiveCategory(@Path("id") id: Long): CategoryNode
    @POST("api/categories/{id}/restore") suspend fun restoreCategory(@Path("id") id: Long): CategoryNode
    @GET("api/form-schema") suspend fun formSchema(@Query("categoryId") categoryId: Long?): FormSchema
    @GET("api/attributes") suspend fun attributes(@Query("categoryId") categoryId: Long? = null, @Query("includeArchived") includeArchived: Boolean = true): List<AttributeDef>
    @POST("api/attributes") suspend fun createAttribute(@Body body: AttributeRequest): AttributeDef
    @PUT("api/attributes/{id}") suspend fun updateAttribute(@Path("id") id: Long, @Body body: AttributeRequest): AttributeDef
    @POST("api/attributes/{id}/archive") suspend fun archiveAttribute(@Path("id") id: Long): AttributeDef
    @POST("api/attributes/{id}/restore") suspend fun restoreAttribute(@Path("id") id: Long): AttributeDef

    // ---- products
    @GET("api/products") suspend fun products(
        @Query("q") q: String? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("warehouseId") warehouseId: Long? = null,
        @Query("status") status: String? = null,
        @Query("archived") archived: Boolean = false,
        @Query("sort") sort: String = "name",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
    ): Page<ProductSummary>
    @GET("api/products/{id}") suspend fun product(@Path("id") id: Long): ProductDetail
    @POST("api/products") suspend fun createProduct(@Body body: ProductRequest): ProductDetail
    @PUT("api/products/{id}") suspend fun updateProduct(@Path("id") id: Long, @Body body: ProductRequest): ProductDetail
    @POST("api/products/{id}/archive") suspend fun archiveProduct(@Path("id") id: Long): ProductDetail
    @POST("api/products/{id}/restore") suspend fun restoreProduct(@Path("id") id: Long): ProductDetail
    @POST("api/products/{id}/variants") suspend fun addVariant(@Path("id") id: Long, @Body body: VariantRequest): ProductDetail
    @PUT("api/variants/{id}") suspend fun updateVariant(@Path("id") id: Long, @Body body: VariantRequest): ProductDetail
    @POST("api/variants/{id}/archive") suspend fun archiveVariant(@Path("id") id: Long): ProductDetail
    @POST("api/variants/{id}/restore") suspend fun restoreVariant(@Path("id") id: Long): ProductDetail
    @Multipart @POST("api/products/{id}/images") suspend fun uploadImage(@Path("id") id: Long, @Part file: MultipartBody.Part): ProductDetail
    @DELETE("api/products/{id}/images/{imageId}") suspend fun deleteImage(@Path("id") id: Long, @Path("imageId") imageId: Long): ProductDetail

    // ---- stock
    @POST("api/stock/operations") suspend fun stockOperation(@Body body: StockOperationRequest): OperationResult
    @POST("api/stock/transfers") suspend fun transfer(@Body body: TransferRequest): OperationResult
    @GET("api/stock/movements") suspend fun movements(
        @Query("productId") productId: Long? = null,
        @Query("variantId") variantId: Long? = null,
        @Query("warehouseId") warehouseId: Long? = null,
        @Query("types") types: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
    ): Page<Movement>
    @GET("api/variants/{id}/batches") suspend fun batches(@Path("id") variantId: Long): List<BatchDto>
    @GET("api/variants/{id}/serials") suspend fun serials(@Path("id") variantId: Long): List<SerialDto>

    // ---- warehouses
    @GET("api/warehouses") suspend fun warehouses(@Query("includeArchived") includeArchived: Boolean = false): List<Warehouse>
    @POST("api/warehouses") suspend fun createWarehouse(@Body body: WarehouseRequest): Warehouse
    @PUT("api/warehouses/{id}") suspend fun updateWarehouse(@Path("id") id: Long, @Body body: WarehouseRequest): Warehouse
    @POST("api/warehouses/{id}/archive") suspend fun archiveWarehouse(@Path("id") id: Long): Warehouse
    @POST("api/warehouses/{id}/restore") suspend fun restoreWarehouse(@Path("id") id: Long): Warehouse
    @POST("api/warehouses/{id}/default") suspend fun makeDefaultWarehouse(@Path("id") id: Long): Warehouse
    @GET("api/warehouses/{id}/locations") suspend fun locations(@Path("id") id: Long): List<LocationDto>
    @POST("api/warehouses/{id}/locations") suspend fun createLocation(@Path("id") id: Long, @Body body: LocationRequest): LocationDto
    @PUT("api/locations/{id}") suspend fun updateLocation(@Path("id") id: Long, @Body body: LocationRequest): LocationDto
    @POST("api/locations/{id}/archive") suspend fun archiveLocation(@Path("id") id: Long): LocationDto
    @POST("api/locations/{id}/restore") suspend fun restoreLocation(@Path("id") id: Long): LocationDto

    // ---- stock counts
    @GET("api/stock-counts") suspend fun stockCounts(@Query("size") size: Int = 50): Page<StockCountSummary>
    @GET("api/stock-counts/{id}") suspend fun stockCount(@Path("id") id: Long): StockCountDetail
    @POST("api/stock-counts") suspend fun createStockCount(@Body body: StockCountRequest): StockCountDetail
    @PUT("api/stock-counts/{id}/lines") suspend fun updateCountLines(@Path("id") id: Long, @Body body: CountLinesRequest): StockCountDetail
    @POST("api/stock-counts/{id}/reconcile") suspend fun reconcile(@Path("id") id: Long): ReconcileResult
    @POST("api/stock-counts/{id}/cancel") suspend fun cancelCount(@Path("id") id: Long): StockCountSummary

    // ---- dashboard & reports
    @GET("api/dashboard") suspend fun dashboard(): Dashboard
    @GET("api/reports/inventory") suspend fun inventoryReport(
        @Query("warehouseId") warehouseId: Long? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("q") q: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("status") status: List<String>? = null,
    ): Page<InventoryRow>
    @GET("api/reports/movement-summary") suspend fun movementSummary(
        @Query("warehouseId") warehouseId: Long? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): MovementSummary
    @GET("api/reports/low-stock") suspend fun lowStockReport(
        @Query("warehouseId") warehouseId: Long? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): Page<InventoryRow>
    @GET("api/reports/movements") suspend fun movementReport(
        @Query("warehouseId") warehouseId: Long? = null,
        @Query("types") types: List<String>? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): Page<Movement>
    @Streaming @GET("api/reports/{report}/export") suspend fun export(
        @Path("report") report: String,
        @Query("format") format: String,
        @Query("warehouseId") warehouseId: Long? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("types") types: List<String>? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): Response<ResponseBody>

    // ---- backup
    @Streaming @GET("api/backup") suspend fun backup(): Response<ResponseBody>
    @POST("api/backup/restore") suspend fun restoreBackup(@Body body: RequestBody): AuthResponse

    // ---- sales
    @POST("api/sales") suspend fun recordSale(@Body body: SaleRequest): Sale
    @GET("api/sales") suspend fun sales(
        @Query("q") q: String? = null,
        @Query("productId") productId: Long? = null,
        @Query("customerId") customerId: Long? = null,
        @Query("paymentStatus") paymentStatus: String? = null,
        @Query("status") status: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
    ): Page<SaleSummary>
    @GET("api/sales/{id}") suspend fun sale(@Path("id") id: Long): Sale
    @POST("api/sales/{id}/payments") suspend fun addSalePayment(@Path("id") id: Long, @Body body: PaymentRequest): Sale
    @POST("api/sales/{id}/cancel") suspend fun cancelSale(@Path("id") id: Long, @Body body: CancelSaleRequest): Sale
    @POST("api/sales/{id}/returns") suspend fun recordReturn(@Path("id") id: Long, @Body body: ReturnRequest): Sale

    // ---- purchases
    @POST("api/purchases") suspend fun recordPurchase(@Body body: PurchaseRequest): Purchase
    @GET("api/purchases") suspend fun purchases(
        @Query("q") q: String? = null,
        @Query("paymentStatus") paymentStatus: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
    ): Page<PurchaseSummary>
    @GET("api/purchases/{id}") suspend fun purchase(@Path("id") id: Long): Purchase
    @POST("api/purchases/{id}/payments") suspend fun addPurchasePayment(@Path("id") id: Long, @Body body: PaymentRequest): Purchase
    @POST("api/purchases/{id}/cancel") suspend fun cancelPurchase(@Path("id") id: Long, @Body body: CancelPurchaseRequest): Purchase

    // ---- customers & suppliers
    @GET("api/customers") suspend fun customers(@Query("q") q: String? = null): List<Party>
    @POST("api/customers") suspend fun createCustomer(@Body body: PartyRequest): Party
    @PUT("api/customers/{id}") suspend fun updateCustomer(@Path("id") id: Long, @Body body: PartyRequest): Party
    @GET("api/suppliers") suspend fun suppliers(@Query("q") q: String? = null): List<Party>
    @POST("api/suppliers") suspend fun createSupplier(@Body body: PartyRequest): Party
    @PUT("api/suppliers/{id}") suspend fun updateSupplier(@Path("id") id: Long, @Body body: PartyRequest): Party

    // ---- analytics (from/to are inclusive ISO dates in the business timezone)
    @GET("api/analytics/overview") suspend fun analyticsOverview(@Query("from") from: String, @Query("to") to: String): AnalyticsOverview
    @GET("api/analytics/products") suspend fun analyticsProducts(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("categoryId") categoryId: Long? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String = "units",
    ): ProductAnalytics
    @GET("api/analytics/trend") suspend fun analyticsTrend(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("granularity") granularity: String = "day",
    ): SalesTrend
    @GET("api/analytics/categories") suspend fun analyticsCategories(@Query("from") from: String, @Query("to") to: String): CategoryAnalytics
    @GET("api/analytics/insights") suspend fun analyticsInsights(@Query("from") from: String, @Query("to") to: String): Insights
    @Streaming @GET("api/analytics/products/export") suspend fun exportAnalytics(
        @Query("format") format: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("categoryId") categoryId: Long? = null,
        @Query("sort") sort: String = "units",
    ): Response<ResponseBody>

    // ---- notifications
    @GET("api/notifications") suspend fun notifications(@Query("unreadOnly") unreadOnly: Boolean = false, @Query("size") size: Int = 50): Page<NotificationDto>
    @GET("api/notifications/unread-count") suspend fun unreadCount(): UnreadCount
    @POST("api/notifications/{id}/read") suspend fun markRead(@Path("id") id: Long): Response<Unit>
    @POST("api/notifications/read-all") suspend fun markAllRead(): Response<Unit>
}
