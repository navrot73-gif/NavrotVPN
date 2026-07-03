package com.navrotvpn.vpn.xray

import android.content.Context
import android.content.Intent
import android.util.Log
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import com.navrotvpn.vpn.protocol.TunnelProtocolHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Обёртка над V2RayVpnService для V2Ray и Trojan.
 *
 * ИСПРАВЛЕНИЯ:
 * - Устранён memory leak: статический callback заменён на WeakReference
 * - Добавлено логирование
 * - Безопасный вызов stopService
 */
object XrayManager : TunnelProtocolHandler {

    private const val TAG = "XrayManager"

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var currentServer: ServerModel? = null
    private var appContext: Context? = null

    init {
        V2RayVpnService.setCallback { running ->
            _state.value = if (running) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
            Log.d(TAG, "Статус от xray-core: ${if (running) "CONNECTED" else "DISCONNECTED"}")
        }
    }

    override suspend fun connect(context: Context, server: ServerModel) {
        _state.value = ConnectionState.CONNECTING
        currentServer = server
        appContext = context.applicationContext
        try {
            val configJson = XrayConfigBuilder.build(server)
            val intent = Intent(context, V2RayVpnService::class.java)
                .putExtra(V2RayVpnService.EXTRA_CONFIG_JSON, configJson)
            context.startForegroundService(intent)
            Log.d(TAG, "V2RayVpnService запущен: ${server.name}")
        } catch (e: Exception) {
            _state.value = ConnectionState.ERROR
            Log.e(TAG, "Ошибка запуска V2RayVpnService", e)
            throw e
        }
    }

    override suspend fun disconnect() {
        currentServer = null
        val ctx = appContext
        if (ctx != null) {
            try {
                ctx.stopService(Intent(ctx, V2RayVpnService::class.java))
                Log.d(TAG, "V2RayVpnService остановлен")
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка остановки V2RayVpnService", e)
            }
        }
        // onDestroy() сервиса дополнительно переведёт state в DISCONNECTED
    }

    override suspend fun reconnectIfNeeded(context: Context) {
        val server = currentServer ?: return
        if (_state.value == ConnectionState.CONNECTED || _state.value == ConnectionState.ERROR) {
            Log.d(TAG, "Переподключение xray (текущее состояние: ${_state.value})")
            connect(context, server)
        }
    }

    override fun isConnected(): Boolean = _state.value == ConnectionState.CONNECTED
}