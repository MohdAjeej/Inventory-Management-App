package com.b2binventory.app.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.*
import org.junit.Test

/**
 * Decodes real backend responses (captured from a running server into test resources) with the
 * app's DTOs and JSON settings, so a field rename or type change on either side fails here.
 */
class ApiContractTest {

    private fun <T> decode(name: String, serializer: KSerializer<T>): T {
        val text = javaClass.classLoader!!.getResource("samples/$name.json")!!.readText()
        return JsonConfig.json.decodeFromString(serializer, text)
    }

    @Test
    fun authAndMembership() {
        val auth = decode("auth", AuthResponse.serializer())
        assertTrue(auth.accessToken.isNotBlank())
        assertTrue(auth.refreshToken.isNotBlank())
        assertEquals("ADMIN", auth.user.role)
        assertTrue(auth.user.can("EMPLOYEE_MANAGE"))
        assertEquals(1, auth.user.memberships.size)

        val me = decode("me", Me.serializer())
        assertEquals(auth.user.businessId, me.businessId)
        assertEquals("Green Leaf Pharmacy", me.businessName)
    }

    @Test
    fun businessAndSettings() {
        assertEquals("Pharmacy", decode("business", Business.serializer()).businessType)
        val s = decode("settings", BusinessSettings.serializer())
        assertTrue(s.trackBatches && s.trackExpiry && s.trackSerials && s.trackDamagedReserved)
        assertEquals(5, s.defaultLowStockThreshold)
    }

    @Test
    fun catalog() {
        val categories = decode("categories", ListSerializer(CategoryNode.serializer()))
        assertEquals("Medicines", categories.single().name)
        assertEquals("Medicines / Tablets", categories.single().children.single().path)

        val schema = decode("form_schema", FormSchema.serializer())
        assertEquals(setOf("otc", "strength", "form"), schema.fields.map { it.key }.toSet())
        assertEquals(listOf("Tablet", "Capsule", "Syrup"), schema.fields.first { it.key == "form" }.options)
        assertTrue(schema.trackBatches)

        val attributes = decode("attributes", ListSerializer(AttributeDef.serializer()))
        assertEquals(3, attributes.size)
        assertTrue(decode("units", ListSerializer(UnitDto.serializer())).any { it.code == "kg" && it.decimals == 3 })
    }

    @Test
    fun products() {
        val page = decode("products", Page.serializer(ProductSummary.serializer()))
        assertTrue(page.totalElements >= 2)
        val paracetamol = page.content.first { it.name == "Paracetamol 500" }
        assertEquals("PCM-500", paracetamol.sku)
        assertEquals("Tablets", paracetamol.categoryName)
        assertEquals("pcs", paracetamol.unitCode)

        val detail = decode("product_detail", ProductDetail.serializer())
        assertNotNull(detail.defaultVariant)
        assertEquals("pcs", detail.baseUnit.code)
        assertTrue(detail.trackBatches)
        assertEquals("500 mg", Formatting.attr(detail.attributes["strength"]))
        assertTrue(detail.balances.any { it.lotNumber == "L1" && it.expiryDate == "2027-03-31" })
        assertTrue(detail.stock.onHand > 0)
    }

    @Test
    fun stock() {
        val result = decode("operation_result", OperationResult.serializer())
        val m = result.movements.single()
        assertEquals("REDUCE", m.type)
        assertEquals(-15.0, m.quantity, 0.0)
        assertEquals("Asha Admin", m.actorName)
        assertTrue(result.balances.isNotEmpty())

        val history = decode("movements", Page.serializer(Movement.serializer()))
        assertTrue(history.content.any { it.type == "TRANSFER_OUT" && it.transferGroupId != null })
        assertTrue(history.content.any { it.type == "OPENING" })

        val batches = decode("batches", ListSerializer(BatchDto.serializer()))
        assertEquals(setOf("L1", "L2"), batches.map { it.lotNumber }.toSet())
        val serials = decode("serials", ListSerializer(SerialDto.serializer()))
        assertEquals(listOf("G-001", "G-002"), serials.map { it.serial })

        val warehouses = decode("warehouses", ListSerializer(Warehouse.serializer()))
        assertEquals(1, warehouses.count { it.isDefault })
        assertTrue(warehouses.any { it.branch && it.code == "CITY" })

        val count = decode("stock_count", StockCountDetail.serializer())
        assertEquals("OPEN", count.summary.status)
        assertTrue(count.lines.any { (it.difference ?: 0.0) == 1.0 })
        assertEquals("RECONCILED", decode("stock_counts", Page.serializer(StockCountSummary.serializer())).content.first().status)
    }

    @Test
    fun movementSummary() {
        val s = decode("movement_summary", MovementSummary.serializer())
        assertEquals(82.0, s.totalOut, 0.0)
        assertEquals("Ply 18mm", s.fastMoving.first().productName)
        assertEquals("Flush door", s.slowMoving.single().productName)
        assertTrue(s.byType.any { it.type == "REDUCE" && it.count == 4L })
    }

    @Test
    fun dashboardReportsNotifications() {
        val d = decode("dashboard", Dashboard.serializer())
        assertTrue(d.activeProducts >= 2)
        assertTrue(d.lowStockCount + d.outOfStockCount >= 1)
        assertTrue(d.recentMovements.isNotEmpty())
        assertTrue(d.warehouses.size >= 2)

        val inventory = decode("report_inventory", Page.serializer(InventoryRow.serializer()))
        assertTrue(inventory.content.isNotEmpty())
        assertTrue(inventory.content.all { it.status in setOf("IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK") })
        assertTrue(decode("report_movements", Page.serializer(Movement.serializer())).totalElements > 0)

        val notes = decode("notifications", Page.serializer(NotificationDto.serializer()))
        assertTrue(notes.content.any { it.type == "LOW_STOCK" || it.type == "OUT_OF_STOCK" })
        assertTrue(notes.content.all { it.payload.containsKey("productId") || it.type.startsWith("EMPLOYEE") })
    }

    @Test
    fun team() {
        val employees = decode("employees", Page.serializer(Employee.serializer()))
        assertEquals(setOf("ADMIN", "STAFF"), employees.content.map { it.role }.toSet())
        val catalog = decode("permissions", PermissionCatalog.serializer())
        assertEquals(19, catalog.permissions.size)
        assertTrue("STOCK_ADD" in catalog.roleDefaults.getValue("STAFF"))

        val invitation = decode("invitation", Invitation.serializer())
        assertEquals(8, invitation.code.length)
        assertEquals("Green Leaf Pharmacy", decode("invitation_preview", InvitationPreview.serializer()).businessName)
    }

    @Test
    fun errorBodyParses() {
        val text = """{"status":400,"code":"VALIDATION_FAILED","message":"Validation failed",
            "fieldErrors":{"attributes.strength":"Strength is required"},"timestamp":"2026-10-14T10:00:00Z"}"""
        val body = JsonConfig.json.decodeFromString(ErrorBody.serializer(), text)
        assertEquals("Strength is required", body.fieldErrors["attributes.strength"])
    }

    @Test
    fun salesAndAnalytics() {
        // Captured from a live server: 7 x Widget at 120 (cost 80) + 2 x Gadget at 60 - 10 discount + 18% tax, 500 paid by UPI.
        val sale = decode("sale", Sale.serializer())
        assertEquals(2, sale.items.size)
        assertEquals(969.8, sale.total, 0.001)
        assertEquals(469.8, sale.outstanding, 0.001)
        assertEquals("PARTIALLY_PAID", sale.paymentStatus)
        assertEquals(310.0, sale.grossProfit!!, 0.001)
        assertEquals(80.0, sale.items.first().unitCost!!, 0.001)
        assertEquals(93.0, sale.stock.first { it.productName == "Widget" }.available, 0.001)
        assertEquals("UPI", sale.payments.single().method)

        val returned = decode("sale_with_return", Sale.serializer())
        assertEquals(2.0, returned.items.first().returnedQuantity, 0.001)
        assertEquals(100.0, returned.returns.single().refundAmount, 0.001)
        assertEquals(230.0, returned.grossProfit!!, 0.001)

        // Members who cannot see costs get no cost or profit fields at all.
        val staffView = decode("sale_staff", Sale.serializer())
        assertNull(staffView.grossProfit)
        assertNull(staffView.items.single().unitCost)
        assertEquals("CANCELLED", staffView.status)

        val purchase = decode("purchase", Purchase.serializer())
        assertEquals(5500.0, purchase.outstanding, 0.001)
        assertEquals(110.0, purchase.items.single().landedUnitCost, 0.001)

        val product = decode("product_priced", ProductDetail.serializer())
        assertEquals(110.0, product.purchasePrice!!, 0.001)
        assertEquals("AVERAGE", product.pricing!!.costSource)

        val overview = decode("analytics_overview", AnalyticsOverview.serializer())
        assertEquals(710.0, overview.revenue.value, 0.001)
        assertNull("missing growth means not enough data", overview.revenue.growthPercent)
        assertEquals("Widget", overview.bestSeller?.name)
        assertEquals("INR", overview.period.currencyCode)
        assertEquals(230.0, overview.financials.grossProfit!!, 0.001)
        assertTrue(overview.financials.profitAvailable)

        val dashboard = decode("dashboard_sales", Dashboard.serializer())
        assertEquals(1L, dashboard.sales!!.today.sales)

        val products = decode("analytics_products", ProductAnalytics.serializer()).products
        assertEquals(listOf("TOP_SELLER"), products.first().labels)
        assertNull(products.first().growthPercent)

        val trend = decode("analytics_trend", SalesTrend.serializer())
        assertEquals(30, trend.points.size)
        assertEquals(7.0, trend.points.sumOf { it.units }, 0.001)

        val insights = decode("analytics_insights", Insights.serializer()).insights
        assertEquals("BEST_SELLER", insights.first().kind)
        assertEquals(1L, decode("sales_page", Page.serializer(SaleSummary.serializer())).totalElements)
    }

    @Test
    fun requestsCarryIdempotencyKeys() {
        val sale = JsonConfig.json.encodeToString(SaleRequest.serializer(),
            SaleRequest(items = listOf(SaleLineRequest(variantId = 1, quantity = 2.0, unitPrice = 10.0)), clientRequestId = "abc"))
        assertTrue(sale.contains("\"clientRequestId\":\"abc\""))
        assertFalse("nulls are omitted", sale.contains("null"))
        val op = JsonConfig.json.encodeToString(StockOperationRequest.serializer(),
            StockOperationRequest(type = "ADD", variantId = 1, quantity = 1.0, clientRequestId = "xyz"))
        assertTrue(op.contains("\"clientRequestId\":\"xyz\""))
    }

    @Test
    fun productRequestEncodesAttributesAsJson() {
        val request = ProductRequest(name = "X", attributes = mapOf("sizes" to kotlinx.serialization.json.JsonArray(
            listOf(kotlinx.serialization.json.JsonPrimitive("M")))))
        val json = JsonConfig.json.encodeToString(ProductRequest.serializer(), request)
        assertTrue(json.contains("\"sizes\":[\"M\"]"))
        assertFalse("nulls are omitted", json.contains("null"))
    }
}

private object Formatting {
    fun attr(element: kotlinx.serialization.json.JsonElement?): String? =
        (element as? kotlinx.serialization.json.JsonPrimitive)?.content
}
