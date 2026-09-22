package com.b2binventory.app.data

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val businessName: String,
    val businessType: String,
    val businessMobile: String,
    val businessEmail: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val gstNumber: String?,
    val name: String,
    val email: String,
    val password: String
)

data class Product(
    val id: Long? = null,
    val category: String,
    val name: String,
    val brand: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val unit: String,
    val size: String? = null,
    val thickness: String? = null,
    val color: String? = null,
    val model: String? = null,
    val description: String? = null,
    val quantity: Int = 0,
    val minimumStock: Int = 0,
    val imageUrl: String? = null
)

data class StockRequest(
    val type: String,
    val quantity: Int,
    val reason: String?
)

data class StockMovement(
    val id: Long,
    val type: String,
    val quantity: Int,
    val previousQuantity: Int,
    val newQuantity: Int,
    val reason: String?,
    val createdAt: String,
    val user: User? = null
)

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val role: String
)

data class Business(
    val id: Long,
    val name: String,
    val type: String?,
    val mobile: String?,
    val email: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val gstNumber: String?
)

data class LoginResponse(
    val message: String,
    val userId: Long,
    val businessId: Long,
    val name: String,
    val email: String,
    val role: String
)

data class RegisterResponse(
    val message: String,
    val businessId: Long,
    val userId: Long
)

data class StockAdjustmentRequest(
    val productId: Long,
    val businessId: Long,
    val userId: Long,
    val type: String, // "ADDED" or "REDUCED"
    val quantity: Int,
    val reason: String,
    val notes: String? = null
)

data class StockAdjustmentResponse(
    val id: Long,
    val productId: Long,
    val productName: String,
    val type: String,
    val quantity: Int,
    val stockBefore: Int,
    val stockAfter: Int,
    val reason: String,
    val notes: String?,
    val addedBy: String,
    val adjustedAt: String
)
