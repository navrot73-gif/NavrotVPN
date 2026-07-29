package com.navrotvpn.network

/**
 * Поддерживаемые протоколы.
 *
 * Полностью рабочие (нативная реализация, никаких сборок вручную):
 *  - WIREGUARD   — com.wireguard.android:tunnel
 *  - AMNEZIAWG   — com.zaneschepke:amneziawg-android (форк WireGuard с
 *                  обфускацией трафика против DPI — см. AmneziaWgManager.kt)
 *
 * Заглушки с точной инструкцией по реальной интеграции (см. соответствующие
 * *ProtocolHandler.kt — там подробно расписано, что именно нужно собрать):
 *  - SHADOWSOCKS, V2RAY, TROJAN, HYSTERIA2, OPENVPN, IKEV2
 */
enum class VpnProtocol {
    WIREGUARD,
    AMNEZIAWG,
    SHADOWSOCKS,
    V2RAY,
    TROJAN,
    HYSTERIA2,
    OPENVPN,
    IKEV2
}
