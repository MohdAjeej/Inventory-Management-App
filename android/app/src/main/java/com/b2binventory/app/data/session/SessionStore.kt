package com.b2binventory.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.b2binventory.app.data.remote.AuthResponse
import com.b2binventory.app.data.remote.JsonConfig
import com.b2binventory.app.data.remote.Me
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "session")

data class Session(val accessToken: String, val refreshToken: String, val me: Me)

/**
 * Persists tokens and the signed-in member in DataStore and mirrors them in memory so the
 * OkHttp interceptor can read them synchronously.
 */
class SessionStore(private val context: Context) {

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val meKey = stringPreferencesKey("me")

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    val current: Session? get() = _session.value

    /** Called before a different business (or nobody) becomes active, e.g. to drop cached data. */
    var onBusinessChanged: (suspend () -> Unit)? = null

    init {
        _session.value = runBlocking { read() }
    }

    private suspend fun read(): Session? {
        val prefs = context.dataStore.data.first()
        val access = prefs[accessKey] ?: return null
        val refresh = prefs[refreshKey] ?: return null
        val me = prefs[meKey]?.let { runCatching { JsonConfig.json.decodeFromString(Me.serializer(), it) }.getOrNull() }
            ?: return null
        return Session(access, refresh, me)
    }

    suspend fun save(auth: AuthResponse) {
        if (_session.value?.me?.businessId != auth.user.businessId) onBusinessChanged?.invoke()
        context.dataStore.edit {
            it[accessKey] = auth.accessToken
            it[refreshKey] = auth.refreshToken
            it[meKey] = JsonConfig.json.encodeToString(Me.serializer(), auth.user)
        }
        _session.value = Session(auth.accessToken, auth.refreshToken, auth.user)
    }

    suspend fun updateMe(me: Me) {
        val s = _session.value ?: return
        context.dataStore.edit { it[meKey] = JsonConfig.json.encodeToString(Me.serializer(), me) }
        _session.value = s.copy(me = me)
    }

    suspend fun clear() {
        onBusinessChanged?.invoke()
        context.dataStore.edit { it.clear() }
        _session.value = null
    }

    fun saveBlocking(auth: AuthResponse) = runBlocking { save(auth) }

    fun clearBlocking() = runBlocking { clear() }
}
