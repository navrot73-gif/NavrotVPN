package com.navrotvpn.vpn.protocol

import android.content.Context
import android.util.Log
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ЗАГЛУШКА. У IKEv2/IPsec есть два реалистичных пути:
 *  1) strongSwan — библиотека strongswan/strongswan для Android
 *     (org.strongswan.android), тоже распространяется как приложение с
 *     нативным charon-демоном; для встраивания нужно, как и с OpenVPN,
 *     копировать модуль с NDK-сборкой к себе в проект.
 *  2) Системный android.net.IpSecManager (начиная с API 28 частично
 *     поддерживает IKEv2 через VpnManager/Ikev2VpnProfile) — работает
 *     без стороннего кода, но только на Android 11+ (API 30) и с
 *     заметными ограничениями по типам аутентификации.
 * Для проекта с minSdk 24 более реалистичный путь — (1).
 */
class Ikev2ProtocolHandler : TunnelProtocolHandler {
    private val TAG = "Ikev2Handler"
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun connect(context: Context, server: ServerModel) {
        _state.value = ConnectionState.ERROR
        Log.e(TAG, "IKEv2 еще не подключен в данной версии приложения. Требуется strongSwan-модуль (NDK) или Ikev2VpnProfile.")
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean = false
}
