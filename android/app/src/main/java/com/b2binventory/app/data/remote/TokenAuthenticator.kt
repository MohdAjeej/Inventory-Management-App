package com.b2binventory.app.data.remote

import com.b2binventory.app.data.session.SessionStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On a 401, exchanges the refresh token for a new pair and retries once. If refreshing fails,
 * the session is cleared and the UI returns to the login screen.
 */
class TokenAuthenticator(
    private val sessionStore: SessionStore,
    private val authApi: AuthApi,
) : Authenticator {

    private val publicAuthPaths = setOf(
        "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/auth/accept-invitation",
    )

    override fun authenticate(route: Route?, response: Response): Request? {
        // A 401 from sign-in endpoints means bad credentials, not an expired token.
        if (response.request.url.encodedPath in publicAuthPaths) return null
        if (responseCount(response) >= 2) return null

        synchronized(this) {
            val session = sessionStore.current ?: return null
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            // Another request may already have refreshed the token.
            if (sentToken != null && sentToken != session.accessToken) {
                return response.request.newBuilder().header("Authorization", "Bearer ${session.accessToken}").build()
            }
            val refreshed = try {
                authApi.refresh(RefreshRequest(session.refreshToken)).execute()
            } catch (e: Exception) {
                return null // network problem: keep the session, surface the error
            }
            val body = refreshed.body()
            if (!refreshed.isSuccessful || body == null) {
                sessionStore.clearBlocking()
                return null
            }
            sessionStore.saveBlocking(body)
            return response.request.newBuilder().header("Authorization", "Bearer ${body.accessToken}").build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
