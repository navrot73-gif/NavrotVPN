package com.navrotvpn.vpn.protocol

import android.content.Context
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.network.ServerModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ЗАГЛУШКА. У Shadowsocks, в отличие от WireGuard, нет лёгкой библиотеки
 * "добавь gradle-зависимость и работай". Официальный клиент
 * shadowsocks/shadowsocks-android распространяется как ОТДЕЛЬНОЕ приложение
 * с нативным core (shadowsocks-rust) и общается с другими приложениями
 * через AIDL (ShadowsocksConnection/IShadowsocksService), а не как
 * встраиваемая библиотека.
 *
 * Реальные варианты интеграции (в порядке сложности):
 *
 * 1. Bind к уже установленному официальному приложению shadowsocks-android
 *    через его AIDL-интерфейс (com.github.shadowsocks.aidl.IShadowsocksService).
 *    Плюc: не нужно собирать нативный код. Минус: требует, чтобы у
 *    пользователя было установлено приложение shadowsocks-android — то есть
 *    твой апп превращается в "пульт управления" чужим VPN-сервисом.
 *
 * 2. Форкнуть shadowsocks-android целиком и переиспользовать его core-модуль
 *    (shadowsocks-rust, скомпилированный под Android) внутри своего проекта.
 *    Это единственный путь к полноценному standalone-приложению с SS "из
 *    коробки", но требует NDK-сборки Rust-биндингов — отдельная задача,
 *    выходящая за рамки одного файла.
 *
 * 3. Использовать собственный tun2socks + shadowsocks-libev, скомпилированные
 *    под Android NDK вручную (как это делают многие независимые клиенты).
 *
 * Ниже — интерфейс-заглушка, чтобы ProtocolRouter уже сейчас мог
 * маршрутизировать на Shadowsocks, когда реализация появится.
 */
class ShadowsocksProtocolHandler : TunnelProtocolHandler {

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun connect(context: Context, server: ServerModel) {
        _state.value = ConnectionState.ERROR
        throw NotImplementedError(
            "Shadowsocks ещё не подключён. См. комментарий в " +
                "ShadowsocksProtocolHandler.kt — нужен либо AIDL-бридж к " +
                "официальному приложению, либо форк shadowsocks-rust core."
        )
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean = false
}
