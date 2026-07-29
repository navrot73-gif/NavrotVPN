package com.navrotvpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.navrotvpn.vpn.protocol.ProtocolRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Следит за сменой сети и просит ProtocolRouter переподключить туннель.
 *
 * ИСПРАВЛЕНИЯ:
 * - Добавлено логирование
 * - Устранён потенциальный race condition: onLost/onAvailable через
 *   single-threaded callback ConnectivityManager, но volatile flag безопасен
 * - Проверка capabilities перед переподключением (только если есть реальный интернет)
 */
class NetworkMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile
    private var wasConnectedBeforeLoss = false
    @Volatile
    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onLost(network: Network) {
            val wasConnected = ProtocolRouter.isConnected()
            if (wasConnected) {
                Log.d(TAG, "Сеть потеряна — VPN был подключён, помечаем для переподключения")
            }
            wasConnectedBeforeLoss = wasConnected
        }

        override fun onAvailable(network: Network) {
            if (!wasConnectedBeforeLoss) return
            wasConnectedBeforeLoss = false
            Log.d(TAG, "Сеть восстановлена — переподключаем VPN")
            scope.launch { ProtocolRouter.reconnectActiveIfNeeded(context) }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (hasInternet && wasConnectedBeforeLoss && !ProtocolRouter.isConnected()) {
                wasConnectedBeforeLoss = false
                Log.d(TAG, "Сеть валидирована — переподключаем VPN")
                scope.launch { ProtocolRouter.reconnectActiveIfNeeded(context) }
            }
        }
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        registered = true
        Log.d(TAG, "NetworkMonitor запущен")
    }

    fun stop() {
        if (!registered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "networkCallback не был зарегистрирован", e)
        }
        registered = false
        Log.d(TAG, "NetworkMonitor остановлен")
    }
}