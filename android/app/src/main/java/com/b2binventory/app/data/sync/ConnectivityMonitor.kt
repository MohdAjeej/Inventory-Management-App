package com.b2binventory.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the phone has a usable network. This only says a request is worth trying; the server may still be
 * unreachable (e.g. Wi-Fi without the backend), which [com.b2binventory.app.data.remote.apiCall] reports as offline.
 */
class ConnectivityMonitor(context: Context) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(isConnected())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _serverReachable = MutableStateFlow(true)
    /** False after a request failed to reach the server; true again after the next successful one. */
    val serverReachable: StateFlow<Boolean> = _serverReachable.asStateFlow()

    fun reportServer(reachable: Boolean) {
        _serverReachable.value = reachable
    }

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _online.value = true
                // A new network: assume the server is reachable until a request says otherwise.
                _serverReachable.value = true
            }

            override fun onLost(network: Network) {
                _online.value = isConnected()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }

    private fun isConnected(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
