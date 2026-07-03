package com.navrotvpn.network

/**
 * Описание одного сервера, полученного с backend'а или заданного вручную.
 * Поля, специфичные для конкретного протокола, заполняются только для
 * него — остальные остаются null/дефолтными.
 */
data class ServerModel(
    val id: String,
    val name: String,
    val countryCode: String,
    val endpoint: String,          // host:port
    val protocol: VpnProtocol = VpnProtocol.WIREGUARD,

    // --- WireGuard / AmneziaWG ---
    val serverPublicKey: String = "",
    val presharedKey: String? = null,
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val dns: String = "1.1.1.1, 1.0.0.1",
    val clientAddress: String = "10.0.0.2/32",

    // --- AmneziaWG: параметры обфускации против DPI ---
    // Jc — число мусорных пакетов перед хендшейком (обычно 3-10).
    // Jmin/Jmax — размер мусорных пакетов в байтах (обычно 50-1000 или 10-50).
    // S1/S2 — доп. случайные байты перед данными в Init/Response пакетах.
    // H1-H4 — "магические" заголовки, маскирующие тип пакета WireGuard
    //         под случайный UDP-трафик (должны не пересекаться друг с другом).
    // Если jc == null — сервер работает как обычный WireGuard без обфускации.
    val jc: Int? = null,
    val jMin: Int? = null,
    val jMax: Int? = null,
    val s1: Int? = null,
    val s2: Int? = null,
    val h1: Long? = null,
    val h2: Long? = null,
    val h3: Long? = null,
    val h4: Long? = null,

    // --- Shadowsocks ---
    val shadowsocksPassword: String? = null,
    val shadowsocksMethod: String? = null,

    // --- V2Ray / Xray (vmess/vless/trojan через xray-core) ---
    val v2rayConfigJson: String? = null,

    // --- Trojan ---
    val trojanPassword: String? = null,
    val trojanSni: String? = null,

    // --- Hysteria2 ---
    val hysteriaPassword: String? = null,
    val hysteriaObfsPassword: String? = null,

    // --- OpenVPN ---
    val openVpnConfigText: String? = null,   // содержимое .ovpn файла целиком

    // --- IKEv2/IPsec ---
    val ikev2Identity: String? = null,
    val ikev2PresharedKey: String? = null
)
