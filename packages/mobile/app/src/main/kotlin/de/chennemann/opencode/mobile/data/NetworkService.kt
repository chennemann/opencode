package de.chennemann.opencode.mobile.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import de.chennemann.opencode.mobile.domain.session.ConnectivityGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkService(context: Context) : ConnectivityGateway {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val connected = MutableStateFlow(isConnected())
    private val change = MutableStateFlow(0L)

    override val online: StateFlow<Boolean> = connected.asStateFlow()
    override val changed: StateFlow<Long> = change.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mark()
        }

        override fun onLost(network: Network) {
            mark()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            mark()
        }

        override fun onUnavailable() {
            mark()
        }
    }

    init {
        manager.registerDefaultNetworkCallback(callback)
    }

    private fun mark() {
        connected.value = isConnected()
        change.value = change.value + 1
    }

    private fun isConnected(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
