package com.navrotvpn.vpn.xray

import android.util.Log
import com.navrotvpn.network.ServerModel
import com.navrotvpn.network.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Собирает полный JSON-конфиг xray-core.
 *
 * ИСПРАВЛЕНИЯ:
 * - Добавлен IPv6 маршрут
 * - Добавлены DNS через туннель (防止 DNS leak)
 * - Логирование при ошибках парсинга
 * - Валидация полей Trojan
 */
object XrayConfigBuilder {

    private const val TAG = "XrayConfigBuilder"

    fun build(server: ServerModel): String {
        val outbound = when (server.protocol) {
            VpnProtocol.V2RAY -> buildV2rayOutbound(server)
            VpnProtocol.TROJAN -> buildTrojanOutbound(server)
            else -> throw IllegalArgumentException("XrayConfigBuilder не поддерживает ${server.protocol}")
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // DNS через туннель — предотвращает утечку DNS-запросов
        val dnsServers = JSONArray()
            .put("1.1.1.1")
            .put("1.0.0.1")
            .put("2606:4700:4700::1111")
            .put("2606:4700:4700::1001")
        root.put("dns", JSONObject().put("servers", dnsServers))

        // CoreController.startLoop() принимает fd TUN напрямую
        root.put("inbounds", JSONArray())

        val outbounds = JSONArray()
        outbound.put("tag", "proxy")
        outbounds.put(outbound)
        outbounds.put(JSONObject().put("protocol", "freedom").put("tag", "direct"))
        outbounds.put(JSONObject().put("protocol", "blackhole").put("tag", "block"))
        root.put("outbounds", outbounds)

        root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", JSONArray()))

        val result = root.toString()
        Log.d(TAG, "Конфиг xray-core собран (${result.length} байт) для ${server.protocol}")
        return result
    }

    private fun buildV2rayOutbound(server: ServerModel): JSONObject {
        val raw = server.v2rayConfigJson
        if (raw.isNullOrBlank()) {
            Log.e(TAG, "У сервера ${server.id} не задан v2rayConfigJson")
            throw IllegalArgumentException("У сервера не задан v2rayConfigJson")
        }
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Невалидный JSON в v2rayConfigJson сервера ${server.id}", e)
            throw IllegalArgumentException("Невалидный JSON в v2rayConfigJson: ${e.message}")
        }
    }

    private fun buildTrojanOutbound(server: ServerModel): JSONObject {
        val password = server.trojanPassword
        if (password.isNullOrBlank()) {
            Log.e(TAG, "У сервера ${server.id} не задан trojanPassword")
            throw IllegalArgumentException("У сервера не задан trojanPassword")
        }

        val parts = server.endpoint.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("endpoint должен быть host:port, получено: ${server.endpoint}")
        }
        val host = parts[0]
        val port = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("Невалидный порт в endpoint: ${server.endpoint}")

        val sni = server.trojanSni?.ifBlank { null } ?: host

        val serverEntry = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("password", password)

        val settings = JSONObject().put("servers", JSONArray().put(serverEntry))

        val streamSettings = JSONObject()
            .put("network", "tcp")
            .put("security", "tls")
            .put("tlsSettings", JSONObject().put("serverName", sni))
            .put("fingerprint", "chrome")  // UTLS-отпечаток для лучшей маскировки

        return JSONObject()
            .put("protocol", "trojan")
            .put("settings", settings)
            .put("streamSettings", streamSettings)
    }
}