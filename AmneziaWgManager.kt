package com.navrotvpn.vpn

import android.content.Context
import android.util.Log
import com.navrotvpn.core.KeyManager
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import com.navrotvpn.vpn.protocol.TunnelProtocolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.Backend as AwgBackend
import org.amnezia.awg.backend.GoBackend as AwgGoBackend
import org.amnezia.awg.backend.Tunnel as AwgTunnel
import org.amnezia.awg.config.Config as AwgConfig
import org.amnezia.awg.config.Interface as AwgInterface
import org.amnezia.awg.config.Peer as AwgPeer

/**
 * Обёртка над AmneziaWG (форк WireGuard с обфускацией DPI).
 *
 * ИСПРАВЛЕНИЯ:
 * - Безопасная инициализация backend (synchronized + volatile)
 * - Убрано дублирование state-перехода
 * - Логирование
 * - AwgWgTunnelAdapter как вложенный класс
 */
object AmneziaWgManager : TunnelProtocolHandler {

    private const val TAG = "AmneziaWgManager"
    private const val TUNNEL_NAME = "navrotvpn_awg"

    @Volatile
    private var backend: AwgBackend? = null
    private var tunnel: AwgWgTunnelAdapter? = null
    private var currentServer: ServerModel? = null
    @Volatile
    private var initialized = false

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            backend = AwgGoBackend(context.applicationContext)
            initialized = true
            Log.d(TAG, "AmneziaWG GoBackend инициализирован")
        }
    }

    override suspend fun connect(context: Context, server: ServerModel) = withContext(Dispatchers.IO) {
        init(context)
        val b = backend ?: throw IllegalStateException("AmneziaWG backend не инициализирован")

        _state.value = ConnectionState.CONNECTING
        try {
            val config = buildAwgConfig(context, server)
            val t = tunnel ?: AwgWgTunnelAdapter(TUNNEL_NAME) { newState ->
                _state.value = when (newState) {
                    AwgTunnel.State.UP -> ConnectionState.CONNECTED
                    AwgTunnel.State.DOWN -> ConnectionState.DISCONNECTED
                    else -> ConnectionState.CONNECTING
                }
            }.also { tunnel = it }

            b.setState(t, AwgTunnel.State.UP, config)
            currentServer = server
            Log.d(TAG, "AmneziaWG туннель запущен: ${server.name}")
        } catch (e: Exception) {
            _state.value = ConnectionState.ERROR
            Log.e(TAG, "Ошибка подключения AmneziaWG", e)
            throw e
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val t = tunnel ?: return@withContext
        val b = backend ?: return@withContext
        try {
            b.setState(t, AwgTunnel.State.DOWN, null)
            Log.d(TAG, "AmneziaWG туннель остановлен")
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка при остановке AmneziaWG", e)
        } finally {
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun reconnectIfNeeded(context: Context) {
        val server = currentServer ?: return
        if (_state.value == ConnectionState.CONNECTED || _state.value == ConnectionState.ERROR) {
            Log.d(TAG, "Переподключение AmneziaWG (текущее состояние: ${_state.value})")
            connect(context, server)
        }
    }

    override fun isConnected(): Boolean = _state.value == ConnectionState.CONNECTED

    /**
     * Собирает AmneziaWG-конфиг.
     * Если у сервера не заданы jc/jMin/jMax — обфускация выключена и
     * туннель ведёт себя как обычный WireGuard (обратная совместимость).
     */
    private fun buildAwgConfig(context: Context, server: ServerModel): AwgConfig {
        val keyPair = KeyManager.getOrCreateKeyPair(context)

        val ifaceBuilder = AwgInterface.Builder()
            .setKeyPair(
                org.amnezia.awg.crypto.KeyPair(
                    org.amnezia.awg.crypto.PrivateKey.fromBase64(keyPair.privateKey.toBase64())
                )
            )
            .parseAddresses(server.clientAddress)
            .parseDnsServers(server.dns)

        // Параметры обфускации — только если заданы
        server.jc?.let { ifaceBuilder.setJunkPacketCount(it) }
        server.jMin?.let { ifaceBuilder.setJunkPacketMinSize(it) }
        server.jMax?.let { ifaceBuilder.setJunkPacketMaxSize(it) }
        server.s1?.let { ifaceBuilder.setInitPacketJunkSize(it) }
        server.s2?.let { ifaceBuilder.setResponsePacketJunkSize(it) }
        server.h1?.let { ifaceBuilder.setInitPacketMagicHeader(it) }
        server.h2?.let { ifaceBuilder.setResponsePacketMagicHeader(it) }
        server.h3?.let { ifaceBuilder.setUnderloadPacketMagicHeader(it) }
        server.h4?.let { ifaceBuilder.setTransportPacketMagicHeader(it) }

        val peerBuilder = AwgPeer.Builder()
            .parsePublicKey(server.serverPublicKey)
            .parseEndpoint(server.endpoint)
            .parseAllowedIPs(server.allowedIps)
            .setPersistentKeepalive(25)

        server.presharedKey?.let { psk -> peerBuilder.parsePreSharedKey(psk) }

        return AwgConfig.Builder()
            .setInterface(ifaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }

    /**
     * Вложенный адаптер Tunnel для AmneziaWG — аналогия WgTunnel.
     * Вложенный, т.к. используется только здесь.
     */
    private class AwgWgTunnelAdapter(
        private val tunnelName: String,
        private val onStateChanged: (AwgTunnel.State) -> Unit
    ) : AwgTunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: AwgTunnel.State) = onStateChanged(newState)
    }
}