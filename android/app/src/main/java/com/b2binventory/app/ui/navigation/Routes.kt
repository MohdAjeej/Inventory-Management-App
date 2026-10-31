package com.b2binventory.app.ui.navigation

import android.net.Uri

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val JOIN = "join"

    const val HOME = "home"
    const val PRODUCTS = "products?status={status}"
    const val STOCK = "stock"
    const val REPORTS = "reports"
    const val MORE = "more"

    const val PRODUCT = "product/{id}"
    const val PRODUCT_NEW = "product-new"
    /** Add Product opened from Record Sale: saving returns the new product to the sale instead of opening it. */
    const val PRODUCT_NEW_FOR_SALE = "product-new-for-sale"
    const val PRODUCT_EDIT = "product/{id}/edit"
    const val STOCK_OPERATION = "stock-operation?productId={productId}&type={type}"
    const val SALE_NEW = "sale-new?productId={productId}"
    const val SALES = "sales"
    const val SALE = "sale/{id}?recorded={recorded}"
    const val SALE_RETURN = "sale/{id}/return"
    const val PURCHASE_NEW = "purchase-new?productId={productId}"
    const val PURCHASE = "purchase/{id}"
    const val COUNTS = "counts"
    const val COUNT = "counts/{id}"
    const val CATEGORIES = "categories"
    const val ATTRIBUTES = "attributes?categoryId={categoryId}&categoryName={categoryName}"
    const val UNITS = "units"
    const val WAREHOUSES = "warehouses"
    const val WAREHOUSE = "warehouses/{id}"
    const val EMPLOYEES = "employees"
    const val EMPLOYEE = "employees/{id}"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val BUSINESS = "business"
    const val APPEARANCE = "appearance"
    const val BACKUP = "backup"
    const val PENDING_SYNC = "pending-sync"

    const val NETWORK = "network"
    const val TRADE_INVOICES = "trade-invoices/{direction}"
    const val TRADE_NEW = "trade-new?buyerId={buyerId}"
    const val TRADE_INVOICE = "trade/{id}"
    const val PRODUCT_LINKS = "product-links"

    fun products(status: String? = null) = if (status == null) "products" else "products?status=$status"
    fun product(id: Long) = "product/$id"
    fun productEdit(id: Long) = "product/$id/edit"
    fun stockOperation(productId: Long? = null, type: String? = null): String {
        val params = listOfNotNull(productId?.let { "productId=$it" }, type?.let { "type=$it" })
        return if (params.isEmpty()) "stock-operation" else "stock-operation?" + params.joinToString("&")
    }
    fun saleNew(productId: Long? = null) = if (productId == null) "sale-new" else "sale-new?productId=$productId"
    fun sale(id: Long, recorded: Boolean = false) = "sale/$id?recorded=$recorded"
    fun saleReturn(id: Long) = "sale/$id/return"
    fun purchaseNew(productId: Long? = null) = if (productId == null) "purchase-new" else "purchase-new?productId=$productId"
    fun purchase(id: Long) = "purchase/$id"
    fun count(id: Long) = "counts/$id"
    /** direction: SALES (invoices sent) or PURCHASES (invoices received). */
    fun tradeInvoices(direction: String) = "trade-invoices/$direction"
    fun tradeNew(buyerId: Long? = null) = if (buyerId == null) "trade-new" else "trade-new?buyerId=$buyerId"
    fun tradeInvoice(id: Long) = "trade/$id"
    fun attributes(categoryId: Long?, categoryName: String?): String {
        val params = listOfNotNull(categoryId?.let { "categoryId=$it" },
            categoryName?.let { "categoryName=" + Uri.encode(it) })
        return if (params.isEmpty()) "attributes" else "attributes?" + params.joinToString("&")
    }
    fun warehouse(id: Long) = "warehouses/$id"
    fun employee(id: Long) = "employees/$id"

    val topLevel = setOf(HOME, PRODUCTS, SALES, REPORTS, MORE)
}
