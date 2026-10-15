package com.b2binventory.app.di

import android.content.Context
import com.b2binventory.app.BuildConfig
import com.b2binventory.app.data.remote.ApiService
import com.b2binventory.app.data.remote.AuthInterceptor
import com.b2binventory.app.data.remote.TokenAuthenticator
import com.b2binventory.app.data.remote.AuthApi
import com.b2binventory.app.data.remote.JsonConfig
import com.b2binventory.app.data.repository.AuthRepository
import com.b2binventory.app.data.prefs.AppPreferences
import com.b2binventory.app.data.session.SessionStore
import com.b2binventory.app.data.sync.ConnectivityMonitor
import com.b2binventory.app.data.sync.OfflineCache
import com.b2binventory.app.data.sync.PendingOpStore
import com.b2binventory.app.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual dependency container, created once by [com.b2binventory.app.InventoryApp]. */
class AppContainer(context: Context) {

    /** Lives as long as the app; used for background sync. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val baseUrl: String = BuildConfig.API_BASE_URL

    val sessionStore = SessionStore(context.applicationContext)

    val appPreferences = AppPreferences(context.applicationContext)

    val connectivity = ConnectivityMonitor(context.applicationContext)

    /** Last answers to GET requests, used when the server cannot be reached. */
    private val httpCache = Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024)

    val pendingOps = PendingOpStore(File(context.filesDir, "pending_ops.json"))

    private val converter = JsonConfig.json.asConverterFactory("application/json".toMediaType())

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logging)

    /** Used only for token refresh, so it never recurses into the authenticator. */
    private val authApi: AuthApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(baseClient().build())
        .addConverterFactory(converter)
        .build()
        .create(AuthApi::class.java)

    private val client = baseClient()
        .cache(httpCache)
        .addInterceptor(OfflineCache.fallback(connectivity))
        .addInterceptor(AuthInterceptor(sessionStore))
        .addNetworkInterceptor(OfflineCache.storable)
        .authenticator(TokenAuthenticator(sessionStore, authApi))
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(converter)
        .build()
        .create(ApiService::class.java)

    val authRepository = AuthRepository(api, sessionStore, pendingOps)

    val syncManager = SyncManager(api, pendingOps, sessionStore, connectivity, appScope)

    init {
        // Never show one business's cached data in another.
        sessionStore.onBusinessChanged = { withContext(Dispatchers.IO) { runCatching { httpCache.evictAll() } } }
    }

    /** Absolute URL for a server-relative path such as /api/files/abc.jpg. */
    fun fileUrl(path: String?): String? = path?.let { baseUrl.trimEnd('/') + it }
}
