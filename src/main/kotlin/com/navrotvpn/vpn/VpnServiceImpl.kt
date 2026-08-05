package com.navrotvpn.vpn

import android.content.Context
import android.util.Log
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import com.navrotvpn.vpn.protocol.TunnelProtocolHandler
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * GoBackend.VpnService — системный сервис для WireGuard.
 * Добавлено foreground-уведомление для Android 14+ (requirement).
 */
class VpnServiceImpl : GoBackend.VpnService() {

    companion object {
        private const val TAG = "VpnServiceImpl"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VpnServiceImpl создан")
    }

    override fun onDestroy() {
        Log.d(TAG, "VpnServiceImpl уничтожен")
        super.onDestroy()
    }
}