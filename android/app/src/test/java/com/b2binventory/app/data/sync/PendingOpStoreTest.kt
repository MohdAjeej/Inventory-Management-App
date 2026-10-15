package com.b2binventory.app.data.sync

import com.b2binventory.app.data.remote.JsonConfig
import com.b2binventory.app.data.remote.PaymentInput
import com.b2binventory.app.data.remote.SaleLineRequest
import com.b2binventory.app.data.remote.SaleRequest
import com.b2binventory.app.data.remote.StockOperationRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PendingOpStoreTest {

    private fun tempFile(): File = File(Files.createTempDirectory("pending").toFile(), "pending_ops.json")

    private val sale = SaleRequest(
        items = listOf(SaleLineRequest(variantId = 7, warehouseId = 1, quantity = 2.0, unitPrice = 12.25)),
        customerName = "Asha",
        payment = PaymentInput("CASH", 24.5),
        clientRequestId = "4b1f6f0e-1a52-4c8e-9a8e-0f5f0b2b3c11",
    )

    @Test
    fun queuedRequestsSurviveARestartAndDecodeUnchanged() = runBlocking {
        val file = tempFile()
        val store = PendingOpStore(file)
        store.enqueue(PendingOp.sale(10, sale, "Cola × 2"))
        store.enqueue(PendingOp.stockOperation(10,
            StockOperationRequest("ADD", 7, 1, 5.0, clientRequestId = "9d0c1a40-2f7e-4c9b-8a55-3b8f0c6f1e22"), "Add · Cola × 5"))

        val reopened = PendingOpStore(file)
        assertEquals(2, reopened.ops.value.size)
        val op = reopened.ops.value.first()
        assertEquals(PendingType.SALE, op.type)
        assertEquals(sale.clientRequestId, op.id)
        assertEquals(sale, JsonConfig.json.decodeFromJsonElement(SaleRequest.serializer(), op.body))
        assertEquals(2, reopened.countFor(10))
        assertEquals(0, reopened.countFor(11))
    }

    @Test
    fun sameRequestIsQueuedOnceAndFailuresCanBeRetried() = runBlocking {
        val store = PendingOpStore(tempFile())
        store.enqueue(PendingOp.sale(10, sale, "Cola × 2"))
        store.enqueue(PendingOp.sale(10, sale, "Cola × 2"))
        assertEquals(1, store.ops.value.size)

        store.markFailed(sale.clientRequestId, "Only 1 in stock")
        assertTrue(store.ops.value.single().failed)
        assertEquals("Only 1 in stock", store.ops.value.single().error)

        store.retry(sale.clientRequestId)
        assertFalse(store.ops.value.single().failed)

        store.remove(sale.clientRequestId)
        assertTrue(store.ops.value.isEmpty())
    }

    @Test
    fun aDamagedQueueFileStartsEmpty() {
        val file = tempFile().apply { writeText("{not json") }
        assertTrue(PendingOpStore(file).ops.value.isEmpty())
    }
}
