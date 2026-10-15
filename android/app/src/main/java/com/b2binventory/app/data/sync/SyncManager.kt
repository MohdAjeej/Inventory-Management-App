package com.b2binventory.app.data.sync

import com.b2binventory.app.data.remote.ApiResult
import com.b2binventory.app.data.remote.ApiService
import com.b2binventory.app.data.remote.JsonConfig
import com.b2binventory.app.data.remote.PaymentRequest
import com.b2binventory.app.data.remote.PurchaseRequest
import com.b2binventory.app.data.remote.SaleRequest
import com.b2binventory.app.data.remote.StockOperationRequest
import com.b2binventory.app.data.remote.TransferRequest
import com.b2binventory.app.data.remote.apiCall
import com.b2binventory.app.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sends queued offline changes to the server, oldest first, whenever the connection comes back, the app starts,
 * the business changes or the user asks. Only changes made in the signed-in business are sent.
 *
 * Outcomes per change: accepted → removed; server unreachable → stop and wait; refused (e.g. not enough stock
 * any more) → kept and marked failed with the server's message, and the rest continue.
 */
class SyncManager(
    private val api: ApiService,
    private val store: PendingOpStore,
    private val sessionStore: SessionStore,
    connectivity: ConnectivityMonitor,
    private val scope: CoroutineScope,
) {
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Bumped after each sync that sent something, so open screens can reload. */
    private val _synced = MutableStateFlow(0)
    val synced: StateFlow<Int> = _synced.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch {
            combine(connectivity.online, connectivity.serverReachable, sessionStore.session.map { it?.me?.businessId }) { net, server, business ->
                net && server && business != null
            }.distinctUntilChanged().collect { ready -> if (ready) syncNow() }
        }
    }

    fun syncNow() {
        if (job?.isActive == true) return
        job = scope.launch {
            _syncing.value = true
            try {
                if (runQueue() > 0) _synced.value++
            } finally {
                _syncing.value = false
            }
        }
    }

    private suspend fun runQueue(): Int {
        val businessId = sessionStore.current?.me?.businessId ?: return 0
        var sent = 0
        for (op in store.ops.value.filter { it.businessId == businessId && !it.failed }) {
            when (val r = send(op)) {
                is ApiResult.Ok -> {
                    store.remove(op.id)
                    sent++
                }
                is ApiResult.Err -> {
                    if (r.offline || r.status == 401) return sent
                    store.markFailed(op.id, r.message)
                }
            }
        }
        return sent
    }

    private suspend fun send(op: PendingOp): ApiResult<Unit> {
        val json = JsonConfig.json
        return apiCall {
            when (op.type) {
                PendingType.SALE -> api.recordSale(json.decodeFromJsonElement(SaleRequest.serializer(), op.body))
                PendingType.PURCHASE -> api.recordPurchase(json.decodeFromJsonElement(PurchaseRequest.serializer(), op.body))
                PendingType.SALE_PAYMENT ->
                    api.addSalePayment(requireNotNull(op.targetId), json.decodeFromJsonElement(PaymentRequest.serializer(), op.body))
                PendingType.STOCK_OPERATION ->
                    api.stockOperation(json.decodeFromJsonElement(StockOperationRequest.serializer(), op.body))
                PendingType.TRANSFER -> api.transfer(json.decodeFromJsonElement(TransferRequest.serializer(), op.body))
            }
            Unit
        }
    }

    suspend fun retry(id: String) {
        store.retry(id)
        syncNow()
    }

    suspend fun discard(id: String) = store.remove(id)
}
