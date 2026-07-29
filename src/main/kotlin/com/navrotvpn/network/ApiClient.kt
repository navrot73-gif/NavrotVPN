package com.navrotvpn.network

import android.content.Context
import android.util.Log
import com.navrotvpn.core.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Загружает список доступных серверов.
 * Смотрит на удалённый JSON, при ошибке — откатывается
 * на локальный assets/servers.json (см. ConfigManager.loadBundledServers).
 *
 * ИСПРАВЛЕНИЯ:
 * - Таймауты подключения/чтения (было: бесконечный hang)
 * - URL настраивается через BuildConfig или хранится в SharedPreferences
 * - Логирование ошибок вместо «тихого» пустого списка
 */
object ApiClient {

    private const val TAG = "ApiClient"

    /**
     * В проде замени на реальный URL своего backend'а.
     * Можно также использовать BuildConfig.SERVERS_URL (добавь в defaultConfig
     * buildTypes поле buildConfigField "String", "SERVERS_URL", "\"https://...\"").
     * Значение "about:blank" означает «не пытаться загружать удалённо».
     */
    private const val SERVERS_URL = "about:blank"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Позволяет переопределить URL динамически (например, из SharedPreferences
     * или настроек приложения) без пересборки.
     */
    @Volatile
    var overrideUrl: String? = null

    suspend fun fetchServerList(): List<ServerModel> = withContext(Dispatchers.IO) {
        val url = overrideUrl?.takeIf { it.isNotBlank() && it != "about:blank" }
            ?: SERVERS_URL.takeIf { it.isNotBlank() && it != "about:blank" }
            ?: return@withContext emptyList()

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} при загрузке серверов с $url")
                    return@withContext emptyList()
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "Пустой ответ от $url")
                    return@withContext emptyList()
                }
                ConfigManager.parseServersJson(body)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Не удалось загрузить серверы с $url: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Непредвиденная ошибка загрузки серверов", e)
            emptyList()
        }
    }
}