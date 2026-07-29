package com.navrotvpn.core

import android.content.Context
import com.navrotvpn.network.ServerModel
import com.navrotvpn.network.VpnProtocol
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import org.json.JSONArray
import org.json.JSONObject

object ConfigManager {

    /**
     * Собирает объект Config библиотеки WireGuard (используется и для
     * обычного WireGuard-сервера). Приватный ключ клиента берётся из
     * KeyManager — он генерируется и хранится на устройстве, не в JSON.
     */
    fun buildConfig(context: Context, server: ServerModel): Config {
        val keyPair = KeyManager.getOrCreateKeyPair(context)

        val iface = Interface.Builder()
            .setKeyPair(keyPair)
            .parseAddresses(server.clientAddress)
            .parseDnsServers(server.dns)
            .build()

        val peerBuilder = Peer.Builder()
            .parsePublicKey(server.serverPublicKey)
            .parseEndpoint(server.endpoint)
            .parseAllowedIPs(server.allowedIps)
            .setPersistentKeepalive(25)

        server.presharedKey?.let { psk -> peerBuilder.parsePreSharedKey(psk) }

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peerBuilder.build())
            .build()
    }

    fun loadBundledServers(context: Context): List<ServerModel> {
        val json = context.assets.open("servers.json").bufferedReader().use { it.readText() }
        return parseServersJson(json)
    }

    fun parseServersJson(json: String): List<ServerModel> {
        val array = JSONArray(json)
        val result = mutableListOf<ServerModel>()
        for (i in 0 until array.length()) {
            result.add(parseServer(array.getJSONObject(i)))
        }
        return result
    }

    private fun parseServer(obj: JSONObject): ServerModel {
        val protocol = when (obj.optString("protocol", "WIREGUARD").uppercase()) {
            "AMNEZIAWG" -> VpnProtocol.AMNEZIAWG
            "SHADOWSOCKS" -> VpnProtocol.SHADOWSOCKS
            "V2RAY" -> VpnProtocol.V2RAY
            "TROJAN" -> VpnProtocol.TROJAN
            "HYSTERIA2" -> VpnProtocol.HYSTERIA2
            "OPENVPN" -> VpnProtocol.OPENVPN
            "IKEV2" -> VpnProtocol.IKEV2
            else -> VpnProtocol.WIREGUARD
        }
        fun optIntOrNull(key: String) = if (obj.has(key) && !obj.isNull(key)) obj.optInt(key) else null
        fun optLongOrNull(key: String) = if (obj.has(key) && !obj.isNull(key)) obj.optLong(key) else null
        fun optStrOrNull(key: String) = obj.optString(key).ifBlank { null }

        return ServerModel(
            id = obj.optString("id"),
            name = obj.optString("name"),
            countryCode = obj.optString("countryCode", "??"),
            endpoint = obj.optString("endpoint"),
            protocol = protocol,
            serverPublicKey = obj.optString("serverPublicKey"),
            presharedKey = optStrOrNull("presharedKey"),
            allowedIps = obj.optString("allowedIps", "0.0.0.0/0, ::/0"),
            dns = obj.optString("dns", "1.1.1.1, 1.0.0.1"),
            clientAddress = obj.optString("clientAddress", "10.0.0.2/32"),
            jc = optIntOrNull("jc"),
            jMin = optIntOrNull("jMin"),
            jMax = optIntOrNull("jMax"),
            s1 = optIntOrNull("s1"),
            s2 = optIntOrNull("s2"),
            h1 = optLongOrNull("h1"),
            h2 = optLongOrNull("h2"),
            h3 = optLongOrNull("h3"),
            h4 = optLongOrNull("h4"),
            shadowsocksPassword = optStrOrNull("shadowsocksPassword"),
            shadowsocksMethod = optStrOrNull("shadowsocksMethod"),
            v2rayConfigJson = optStrOrNull("v2rayConfigJson"),
            trojanPassword = optStrOrNull("trojanPassword"),
            trojanSni = optStrOrNull("trojanSni"),
            hysteriaPassword = optStrOrNull("hysteriaPassword"),
            hysteriaObfsPassword = optStrOrNull("hysteriaObfsPassword"),
            openVpnConfigText = optStrOrNull("openVpnConfigText"),
            ikev2Identity = optStrOrNull("ikev2Identity"),
            ikev2PresharedKey = optStrOrNull("ikev2PresharedKey")
        )
    }
}
