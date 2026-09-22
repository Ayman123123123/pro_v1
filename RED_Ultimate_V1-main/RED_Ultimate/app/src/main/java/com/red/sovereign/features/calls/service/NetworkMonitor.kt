package com.red.sovereign.features.calls.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkType {
    WIFI, CELLULAR, NONE, UNKNOWN
}

data class NetworkState(
    val isConnected: Boolean,
    val type: NetworkType,
    val bandwidthKbps: Int = 0 // Optional, for advanced BWE estimation hints
)

/**
 * يراقب حالة الشبكة (Wi-Fi / 4G) وينبه SfuClient أو RedVoipMaster
 * لإجراء ICE Restart عند تبديل الشبكة لتجنب انقطاع المكالمة.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _networkState = MutableStateFlow(NetworkState(false, NetworkType.UNKNOWN))
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    fun startMonitoring() {
        if (networkCallback != null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkState(network, true)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost: $network")
                _networkState.value = NetworkState(false, NetworkType.NONE)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateNetworkState(network, true, networkCapabilities)
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        
        // Initial state
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            updateNetworkState(activeNetwork, true)
        } else {
            _networkState.value = NetworkState(false, NetworkType.NONE)
        }
        
        Log.d(TAG, "Network monitoring started")
    }

    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null
        Log.d(TAG, "Network monitoring stopped")
    }

    private fun updateNetworkState(network: Network, isConnected: Boolean, capabilities: NetworkCapabilities? = null) {
        val caps = capabilities ?: connectivityManager.getNetworkCapabilities(network)
        if (caps == null) {
            _networkState.value = NetworkState(isConnected, NetworkType.UNKNOWN)
            return
        }

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.UNKNOWN
        }

        // Downstream bandwidth in Kbps
        val downstreamBandwidth = caps.linkDownstreamBandwidthKbps
        
        Log.d(TAG, "Network changed - Type: $type, Connected: $isConnected, Bandwidth: $downstreamBandwidth kbps")

        _networkState.value = NetworkState(isConnected, type, downstreamBandwidth)
    }
}
