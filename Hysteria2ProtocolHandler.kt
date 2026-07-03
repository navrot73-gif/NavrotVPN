package com.navrotvpn.vpn.protocol

import android.content.Context
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ЗАГЛУШКА. Hysteria2 — протокол поверх QUIC/UDP с активной подделкой
 * профиля трафика (специально проектировался для плохих/фильтруемых
 * каналов и хорошо проходит через агрессивный DPI). Официальная реализация
 * — Go-проект github.com/apernet/hysteria; под Android собирается так же,
 * как V2Ray — через gomobile bind в .aar (в репозитории есть готовый
 * gomobile-таргет `app/` с примером сборки под Android). Никакого maven-
 * артефакта в открытом доступе нет — только сборка из исходников.
 */
class Hysteria2ProtocolHandler : TunnelProtocolHandler {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun connect(context: Context, server: ServerModel) {
        _state.value = ConnectionState.ERROR
        throw NotImplementedError(
            "Hysteria2 ещё не подключён — требует сборки hysteria-core под " +
                "Android через gomobile. См. комментарий в Hysteria2ProtocolHandler.kt."
        )
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean = false
}
