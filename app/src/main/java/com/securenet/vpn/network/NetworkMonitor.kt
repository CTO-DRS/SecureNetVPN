package com.securenet.vpn.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits a snapshot whenever the active network changes (Wi-Fi <-> mobile,
 * loss, regain). Powers auto-reconnect and trusted-network awareness.
 */
class NetworkMonitor(context: Context) {

    data class NetworkSnapshot(
        val hasInternet: Boolean,
        val isMetered: Boolean,
        val isWifi: Boolean,
        val transportDescription: String
    )

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val networkChanges: Flow<NetworkSnapshot> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentSnapshot())
            }

            override fun onLost(network: Network) {
                trySend(currentSnapshot())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(currentSnapshot())
            }
        }

        trySend(currentSnapshot())

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    fun currentSnapshot(): NetworkSnapshot {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return NetworkSnapshot(
            hasInternet = hasInternet,
            isMetered = isMetered,
            isWifi = isWifi,
            transportDescription = when {
                capabilities == null -> "none"
                isWifi -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "other"
            }
        )
    }
}
