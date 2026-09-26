package com.b2binventory.app.data

import retrofit2.http.*

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Map<String, Any>

    @POST("api/auth/register-business")
    suspend fun registerBusiness(@Body body: RegisterRequest): Map<String, Any>

    @GET("api/inventory/products")
    suspend fun products(
        @Query("businessId") businessId: Long,
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): List<Product>

    @POST("api/inventory/products")
    suspend fun createProduct(
        @Query("businessId") businessId: Long,
        @Query("userId") userId: Long,
        @Body product: Product
    ): Product

    @POST("api/inventory/products/{id}/stock")
    suspend fun changeStock(
        @Path("id") id: Long,
        @Query("businessId") businessId: Long,
        @Query("userId") userId: Long,
        @Body body: StockRequest
    ): Map<String, Any>

    @GET("api/inventory/products/{id}/history")
    suspend fun productHistory(
        @Path("id") id: Long,
        @Query("businessId") businessId: Long
    ): List<StockMovement>

    @POST("api/stock-adjustments")
    suspend fun adjustStock(@Body request: StockAdjustmentRequest): StockAdjustmentResponse

    @GET("api/stock-adjustments/product/{productId}")
    suspend fun getStockHistory(
        @Path("productId") productId: Long,
        @Query("type") type: String? = null
    ): List<StockAdjustmentResponse>
    
    @GET("api/stock-adjustments/business/{businessId}")
    suspend fun getBusinessStockHistory(
        @Path("businessId") businessId: Long
    ): List<StockAdjustmentResponse>
    
    // Category endpoints
    @GET("api/categories/business/{businessId}")
    suspend fun getCategories(
        @Path("businessId") businessId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null
    ): CategoriesPageResponse
    
    @GET("api/categories/business/{businessId}/stats")
    suspend fun getCategoryStats(
        @Path("businessId") businessId: Long
    ): CategoryStatsResponse
    
    @GET("api/categories/{id}")
    suspend fun getCategoryById(
        @Path("id") id: Long
    ): CategoryResponse
    
    @POST("api/categories")
    suspend fun createCategory(
        @Body request: CategoryRequest
    ): CategoryResponse
    
    @PUT("api/categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: Long,
        @Body request: CategoryRequest
    ): CategoryResponse
    
    @PATCH("api/categories/{id}/toggle-status")
    suspend fun toggleCategoryStatus(
        @Path("id") id: Long
    ): CategoryResponse
    
    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(
        @Path("id") id: Long
    ): Map<String, String>
    
    // Business endpoints
    @GET("api/businesses/{id}")
    suspend fun getBusinessById(
        @Path("id") id: Long
    ): Business
}
