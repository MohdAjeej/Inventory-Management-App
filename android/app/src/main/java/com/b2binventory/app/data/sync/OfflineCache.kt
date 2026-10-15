package com.b2binventory.app.data.sync

import okhttp3.CacheControl
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Keeps the last server answer to every GET so screens still show data offline.
 *
 * Online, every request still goes to the server (responses are stored as immediately stale). Only when the
 * server cannot be reached is the stored copy used. The cache is emptied whenever the business changes, so one
 * business's data is never shown in another (see [com.b2binventory.app.data.session.SessionStore]).
 */
object OfflineCache {

    const val SERVED_FROM_CACHE = "X-Offline-Cache"
    private const val MAX_STALE_DAYS = 30

    /** Paths whose responses must never be stored: downloads and backups. */
    private fun skip(path: String) = path.endsWith("/export") || path.startsWith("/api/backup")

    /** Network interceptor: makes API responses storable (the server sends no-store by default). */
    val storable = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val path = request.url.encodedPath
        if (request.method != "GET" || !response.isSuccessful || !path.startsWith("/api/") ||
            path.startsWith("/api/files/") || skip(path)) {
            response
        } else {
            response.newBuilder()
                .header("Cache-Control", "private, max-age=0")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }
    }

    /**
     * Application interceptor: records whether the server answered and, when it cannot be reached,
     * answers a GET from the stored copy.
     */
    fun fallback(connectivity: ConnectivityMonitor) = Interceptor { chain ->
        val request = chain.request()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            connectivity.reportServer(reachable = false)
            if (request.method != "GET" || skip(request.url.encodedPath)) throw e
            val cached = chain.proceed(
                request.newBuilder()
                    .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(MAX_STALE_DAYS, TimeUnit.DAYS).build())
                    .build()
            )
            if (cached.code == 504) { // nothing stored for this request
                cached.close()
                throw e
            }
            return@Interceptor cached.newBuilder().header(SERVED_FROM_CACHE, "1").build()
        }
        connectivity.reportServer(reachable = true)
        response
    }
}
