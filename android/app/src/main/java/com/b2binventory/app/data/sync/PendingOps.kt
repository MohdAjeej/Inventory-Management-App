package com.b2binventory.app.data.sync

import com.b2binventory.app.data.remote.JsonConfig
import com.b2binventory.app.data.remote.PaymentRequest
import com.b2binventory.app.data.remote.PurchaseRequest
import com.b2binventory.app.data.remote.SaleRequest
import com.b2binventory.app.data.remote.StockOperationRequest
import com.b2binventory.app.data.remote.TransferRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.time.Instant

/** Changes that can be recorded without a connection. Every one carries a clientRequestId, so replays are safe. */
@Serializable
enum class PendingType { SALE, PURCHASE, SALE_PAYMENT, STOCK_OPERATION, TRANSFER }

/**
 * A change recorded while the server could not be reached. [id] is the request's clientRequestId; [detail] is a
 * short description written when it was recorded (product names, amount) for the pending-sync list.
 */
@Serializable
data class PendingOp(
    val id: String,
    val businessId: Long,
    val type: PendingType,
    val body: JsonElement,
    val detail: String,
    val createdAt: String = Instant.now().toString(),
    /** The sale a payment belongs to. */
    val targetId: Long? = null,
    /** Set when the server refused the change; it then waits for the user to retry or discard it. */
    val error: String? = null,
) {
    val failed: Boolean get() = error != null

    companion object {
        private val json = JsonConfig.json

        fun sale(businessId: Long, r: SaleRequest, detail: String) =
            PendingOp(r.clientRequestId, businessId, PendingType.SALE, json.encodeToJsonElement(SaleRequest.serializer(), r), detail)

        fun purchase(businessId: Long, r: PurchaseRequest, detail: String) =
            PendingOp(r.clientRequestId, businessId, PendingType.PURCHASE,
                json.encodeToJsonElement(PurchaseRequest.serializer(), r), detail)

        fun payment(businessId: Long, saleId: Long, r: PaymentRequest, detail: String) =
            PendingOp(r.clientRequestId, businessId, PendingType.SALE_PAYMENT,
                json.encodeToJsonElement(PaymentRequest.serializer(), r), detail, targetId = saleId)

        fun stockOperation(businessId: Long, r: StockOperationRequest, detail: String) =
            PendingOp(requireNotNull(r.clientRequestId), businessId, PendingType.STOCK_OPERATION,
                json.encodeToJsonElement(StockOperationRequest.serializer(), r), detail)

        fun transfer(businessId: Long, r: TransferRequest, detail: String) =
            PendingOp(requireNotNull(r.clientRequestId), businessId, PendingType.TRANSFER,
                json.encodeToJsonElement(TransferRequest.serializer(), r), detail)
    }
}

/** The queue of [PendingOp]s, kept in a small JSON file so it survives the app being closed. */
class PendingOpStore(private val file: File) {

    private val mutex = Mutex()
    private val serializer = ListSerializer(PendingOp.serializer())

    private val _ops = MutableStateFlow(load())
    val ops: StateFlow<List<PendingOp>> = _ops.asStateFlow()

    private fun load(): List<PendingOp> = runCatching {
        if (file.exists()) JsonConfig.json.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private suspend fun update(change: (List<PendingOp>) -> List<PendingOp>) = mutex.withLock {
        val next = change(_ops.value)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JsonConfig.json.encodeToString(serializer, next))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        _ops.value = next
    }

    /** Adds an op; recording the same request twice keeps one copy. */
    suspend fun enqueue(op: PendingOp) = update { list -> list.filterNot { it.id == op.id } + op }

    suspend fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    suspend fun markFailed(id: String, error: String) =
        update { list -> list.map { if (it.id == id) it.copy(error = error) else it } }

    suspend fun retry(id: String) = update { list -> list.map { if (it.id == id) it.copy(error = null) else it } }

    suspend fun clear() = update { emptyList() }

    fun countFor(businessId: Long?): Int = _ops.value.count { it.businessId == businessId }
}
