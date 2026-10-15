package com.b2binventory.app.data.repository

import com.b2binventory.app.data.remote.*
import com.b2binventory.app.data.session.SessionStore
import com.b2binventory.app.data.sync.PendingOpStore

class AuthRepository(
    private val api: ApiService,
    private val sessionStore: SessionStore,
    private val pendingOps: PendingOpStore,
) {

    suspend fun login(email: String, password: String): ApiResult<Me> =
        apiCall { api.login(LoginRequest(email.trim(), password)).also { sessionStore.save(it) }.user }

    suspend fun register(request: RegisterRequest): ApiResult<Me> =
        apiCall { api.register(request).also { sessionStore.save(it) }.user }

    suspend fun acceptInvitation(request: AcceptInvitationRequest): ApiResult<Me> =
        apiCall { api.acceptInvitation(request).also { sessionStore.save(it) }.user }

    suspend fun switchBusiness(businessId: Long): ApiResult<Me> =
        apiCall { api.switchBusiness(SwitchBusinessRequest(businessId)).also { sessionStore.save(it) }.user }

    /** Restores a backup file into a new business and switches to it. */
    suspend fun restoreBackup(body: okhttp3.RequestBody): ApiResult<Me> =
        apiCall { api.restoreBackup(body).also { sessionStore.save(it) }.user }

    /** Re-reads the member (role, permissions) from the server. */
    suspend fun refreshMe(): ApiResult<Me> = apiCall { api.me().also { sessionStore.updateMe(it) } }

    suspend fun logout() {
        sessionStore.current?.let { s -> apiCall { api.logout(RefreshRequest(s.refreshToken)) } }
        // Unsynced changes belong to this person's session; they are dropped (the user is warned first).
        pendingOps.clear()
        sessionStore.clear()
    }
}
