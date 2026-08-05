package com.navrotvpn.vpn

import android.content.Context
import android.util.Log
import com.navrotvpn.core.ConfigManager
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
 * Синглтон, оборачивающий GoBackend (нативная реализация WireGuard).
 *
 * ИСПРАВЛЕНИЯ:
 * - Убрано дублирование state-перехода CONNECTED (было: callback + ручная установка)
 * - Добавлено логирование ошибок
 * - Безопасный доступ к backend через isInitialized
 */
object WireGuardManager : TunnelProtocolHandler {

    private const val TAG = "WireGuardManager"
    private const val TUNNEL_NAME = "navrotvpn"

    @Volatile
    private var backend: Backend? = null
    private var tunnel: WgTunnel? = null
    private var currentServer: ServerModel? = null
    @Volatile
    private var initialized = false

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            backend = GoBackend(context.applicationContext)
            initialized = true
            Log.d(TAG, "GoBackend инициализирован")
        }
    }

    override suspend fun connect(context: Context, server: ServerModel) {
        withContext(Dispatchers.IO) {
            init(context)
            val b = backend ?: throw IllegalStateException("WireGuard backend не инициализирован")

            _state.value = ConnectionState.CONNECTING
            try {
                val config = ConfigManager.buildConfig(context, server)
                val t = tunnel ?: WgTunnel(TUNNEL_NAME) { wgState ->
                    // Единое место обновления состояния — через callback от GoBackend
                    _state.value = when (wgState) {
                        Tunnel.State.UP -> ConnectionState.CONNECTED
                        Tunnel.State.DOWN -> ConnectionState.DISCONNECTED
                        else -> ConnectionState.CONNECTING
                    }
                }.also { tunnel = it }

                b.setState(t, Tunnel.State.UP, config)
                currentServer = server
                // НЕ устанавливаем CONNECTED вручную — это делает callback выше
                Log.d(TAG, "WireGuard туннель запущен: ${server.name}")
            } catch (e: Exception) {
                _state.value = ConnectionState.ERROR
                Log.e(TAG, "Ошибка подключения WireGuard", e)
                throw e
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            val t = tunnel ?: return@withContext
            val b = backend ?: return@withContext
            try {
                b.setState(t, Tunnel.State.DOWN, null)
                Log.d(TAG, "WireGuard туннель остановлен")
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка при остановке WireGuard", e)
            } finally {
                _state.value = ConnectionState.DISCONNECTED
            }
        }
    }

    override suspend fun reconnectIfNeeded(context: Context) {
        val server = currentServer ?: return
        if (_state.value == ConnectionState.CONNECTED || _state.value == ConnectionState.ERROR) {
            Log.d(TAG, "Переподключение WireGuard (текущее состояние: ${_state.value})")
            connect(context, server)
        }
    }

    override fun isConnected(): Boolean = _state.value == ConnectionState.CONNECTED
}
