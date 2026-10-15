package com.b2binventory.app.data.sync

import com.b2binventory.app.data.remote.PaymentRequest
import com.b2binventory.app.data.remote.PurchaseRequest
import com.b2binventory.app.data.remote.SaleRequest
import com.b2binventory.app.data.remote.StockOperationRequest
import com.b2binventory.app.data.remote.TransferRequest
import com.b2binventory.app.data.session.SessionStore
import com.b2binventory.app.di.AppContainer

/**
 * What screens use to keep a change on the phone when the server cannot be reached. Each call returns false
 * when nobody is signed in (nothing is queued then and the screen shows the original error).
 */
class OfflineQueue(private val store: PendingOpStore, private val session: SessionStore) {

    constructor(container: AppContainer) : this(container.pendingOps, container.sessionStore)

    private suspend fun add(build: (Long) -> PendingOp): Boolean {
        val businessId = session.current?.me?.businessId ?: return false
        store.enqueue(build(businessId))
        return true
    }

    suspend fun sale(r: SaleRequest, detail: String) = add { PendingOp.sale(it, r, detail) }

    suspend fun purchase(r: PurchaseRequest, detail: String) = add { PendingOp.purchase(it, r, detail) }

    suspend fun payment(saleId: Long, r: PaymentRequest, detail: String) = add { PendingOp.payment(it, saleId, r, detail) }

    suspend fun stockOperation(r: StockOperationRequest, detail: String) = add { PendingOp.stockOperation(it, r, detail) }

    suspend fun transfer(r: TransferRequest, detail: String) = add { PendingOp.transfer(it, r, detail) }

    companion object {
        /** Shown by screens whose messages are plain strings; translated where they are displayed. */
        const val OFFLINE_MESSAGE = "@offline_saved"
    }
}
