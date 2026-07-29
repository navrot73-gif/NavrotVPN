package com.navrotvpn.vpn.protocol

import android.content.Context
import android.util.Log
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import com.navrotvpn.network.VpnProtocol
import com.navrotvpn.vpn.AmneziaWgManager
import com.navrotvpn.vpn.WireGuardManager
import com.navrotvpn.vpn.xray.XrayManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Единая точка входа для UI: выбирает нужный handler по протоколу.
 *
 * ИСПРАВЛЕНИЯ:
 * - Логирование при переключении протоколов
 * - Защита от двойного disconnect
 * - Ленивая инициализация заглушек (не создаём экземпляры до обращения)
 */
object ProtocolRouter {

    private const val TAG = "ProtocolRouter"

    private val handlers = mutableMapOf<VpnProtocol, TunnelProtocolHandler>()
    private var activeProtocol: VpnProtocol = VpnProtocol.WIREGUARD

    private fun handlerFor(protocol: VpnProtocol): TunnelProtocolHandler =
        handlers.getOrPut(protocol) {
            when (protocol) {
                VpnProtocol.WIREGUARD -> WireGuardManager
                VpnProtocol.AMNEZIAWG -> AmneziaWgManager
                VpnProtocol.V2RAY, VpnProtocol.TROJAN -> XrayManager
                VpnProtocol.SHADOWSOCKS -> ShadowsocksProtocolHandler()
                VpnProtocol.HYSTERIA2 -> Hysteria2ProtocolHandler()
                VpnProtocol.OPENVPN -> OpenVpnProtocolHandler()
                VpnProtocol.IKEV2 -> Ikev2ProtocolHandler()
            }
        }

    fun isImplemented(protocol: VpnProtocol): Boolean = when (protocol) {
        VpnProtocol.WIREGUARD, VpnProtocol.AMNEZIAWG -> true
        VpnProtocol.V2RAY, VpnProtocol.TROJAN -> {
            // Реально работающие, но зависят от libv2ray.aar
            try {
                Class.forName("libv2ray.CoreController")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
        else -> false
    }

    fun stateFor(protocol: VpnProtocol): StateFlow<ConnectionState> =
        handlerFor(protocol).state

    suspend fun connect(context: Context, server: ServerModel) {
        val nextProtocol = server.protocol
        val nextHandler = handlerFor(nextProtocol)

        if (nextProtocol != activeProtocol) {
            val activeHandler = handlers[activeProtocol]
            if (activeHandler != null && activeHandler.isConnected()) {
                Log.d(TAG, "Переключение протокола: ${activeProtocol.name} → ${nextProtocol.name}, отключаем предыдущий")
                activeHandler.disconnect()
            }
        }

        activeProtocol = nextProtocol
        Log.d(TAG, "Подключение через ${nextProtocol.name} к ${server.name}")
        nextHandler.connect(context, server)
    }

    suspend fun disconnect() {
        val handler = handlers[activeProtocol]
        if (handler != null) {
            handler.disconnect()
            Log.d(TAG, "Отключен протокол ${activeProtocol.name}")
        }
    }

    suspend fun reconnectActiveIfNeeded(context: Context) {
        val handler = handlers[activeProtocol] ?: return
        if (handler.isConnected() || handler.state.value == ConnectionState.ERROR) {
            handler.reconnectIfNeeded(context)
        }
    }

    fun isConnected(): Boolean {
        val handler = handlers[activeProtocol] ?: return false
        return handler.isConnected()
    }
}