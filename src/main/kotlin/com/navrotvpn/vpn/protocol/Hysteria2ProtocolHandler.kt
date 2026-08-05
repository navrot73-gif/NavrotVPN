package com.navrotvpn.vpn.protocol

import android.content.Context
import android.util.Log
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
    private val TAG = "Hysteria2Handler"
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun connect(context: Context, server: ServerModel) {
        _state.value = ConnectionState.ERROR
        Log.e(TAG, "Hysteria2 еще не подключен в данной версии приложения. Требуется сборка hysteria-core под Android через gomobile.")
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean = false
}
