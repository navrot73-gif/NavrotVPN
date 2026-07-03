package com.navrotvpn.vpn.protocol

import android.content.Context
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ЗАГЛУШКА. OpenVPN для Android не публикуется как чистая gradle-
 * зависимость: официальный клиент ics-openvpn (github.com/schwabe/
 * ics-openvpn) собирается как полноценное приложение с нативным
 * openvpn-core (C, через JNI). Реалистичный путь встраивания —
 * скопировать модуль `main`/`openvpn` из ics-openvpn в свой проект как
 * :openvpn gradle-модуль (там уже настроен NDK-билд) и дергать его
 * OpenVPNService/ProfileManager из своего UI вместо родного. Полностью
 * автономная пересборка ядра "с нуля" не нужна — но и простого
 * `implementation` тут, увы, нет.
 */
class OpenVpnProtocolHandler : TunnelProtocolHandler {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun connect(context: Context, server: ServerModel) {
        _state.value = ConnectionState.ERROR
        throw NotImplementedError(
            "OpenVPN ещё не подключён — требует встраивания :openvpn-модуля " +
                "из ics-openvpn (NDK). См. комментарий в OpenVpnProtocolHandler.kt."
        )
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean = false
}
