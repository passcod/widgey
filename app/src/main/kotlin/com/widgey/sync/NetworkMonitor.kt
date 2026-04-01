package com.widgey.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkMonitor(private val context: Context) {

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var onConnectivityRestored: (() -> Unit)? = null
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            val wasDisconnected = !_isConnected.value
            _isConnected.value = true

            if (wasDisconnected) {
                Log.d(TAG, "Connectivity restored, triggering sync")
                scope.launch {
                    onConnectivityRestored?.invoke()
                }
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            // Check if we still have any network
            _isConnected.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "Network capabilities changed, hasInternet=$hasInternet")
            _isConnected.value = hasInternet
        }

        override fun onUnavailable() {
            Log.d(TAG, "Network unavailable")
            _isConnected.value = false
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Start monitoring network connectivity
     */
    fun startMonitoring(onConnectivityRestored: () -> Unit) {
        if (isRegistered) {
            Log.d(TAG, "Already monitoring")
            return
        }

        this.onConnectivityRestored = onConnectivityRestored

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isRegistered = true
            Log.d(TAG, "Started network monitoring")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Stop monitoring network connectivity
     */
    fun stopMonitoring() {
        if (!isRegistered) {
            return
        }

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
            onConnectivityRestored = null
            Log.d(TAG, "Stopped network monitoring")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    /**
     * Check if currently connected to the internet
     */
    fun isCurrentlyConnected(): Boolean = _isConnected.value
}
