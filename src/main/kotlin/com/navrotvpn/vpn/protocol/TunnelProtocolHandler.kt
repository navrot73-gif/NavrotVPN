package com.navrotvpn.vpn.protocol

import android.content.Context
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Общий контракт для любого протокола (WireGuard / Shadowsocks / V2Ray).
 * MainActivity работает только через этот интерфейс и не знает деталей
 * конкретного протокола — выбор реализации происходит в ProtocolRouter.
 */
interface TunnelProtocolHandler {
    val state: StateFlow<ConnectionState>
    suspend fun connect(context: Context, server: ServerModel)
    suspend fun disconnect()
    fun isConnected(): Boolean

    /** Переподключиться, если был активен на момент вызова. Нет-оп по умолчанию. */
    suspend fun reconnectIfNeeded(context: Context) {}
}
