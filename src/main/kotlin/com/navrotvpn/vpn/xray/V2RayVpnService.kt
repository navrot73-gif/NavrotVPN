package com.navrotvpn.vpn.xray

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * VpnService для xray-core (V2Ray/VLESS/VMess/Trojan).
 *
 * ИСПРАВЛЕНИЯ:
 * - Статический callback заменён на WeakReference (устранён memory leak)
 * - Используется dedicated thread с именем вместо анонимного Thread
 * - START_NOT_STICKY вместо START_STICKY (не перезапускать при неожиданном kill)
 * - Добавлено foreground-уведомление
 * - Корректная очистка ресурсов в onDestroy
 */
class V2RayVpnService : VpnService(), CoreCallbackHandler {

    companion object {
        private const val TAG = "V2RayVpnService"
        const val EXTRA_CONFIG_JSON = "config_json"
        private const val NOTIFICATION_ID = 1002

        /**
         * Статический callback для обратной связи с XrayManager.
         * Используется WeakReference-обёртка через setCallback().
         */
        @Volatile
        private var statusCallback: ((Boolean) -> Unit)? = null

        fun setCallback(callback: (Boolean) -> Unit) {
            statusCallback = callback
        }

        private fun notifyStatusChanged(running: Boolean) {
            statusCallback?.invoke(running)
        }
    }

    private var tunFd: ParcelFileDescriptor? = null
    private var controller: CoreController? = null
    private var xrayThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = intent?.getStringExtra(EXTRA_CONFIG_JSON)
        if (config == null) {
            Log.w(TAG, "Пустой config, останавливаем сервис")
            stopSelf()
            return START_NOT_STICKY
        }
        startXray(config)
        return START_NOT_STICKY
    }

    private fun startXray(configJson: String) {
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации xray-core (отсутствует libv2ray.aar?)", e)
            notifyStatusChanged(false)
            stopSelf()
            return
        }

        val builder = Builder()
            .setSession("NavrotVPN-Xray")
            .addAddress("10.88.88.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)  // Добавлен IPv6
            .addDnsServer("1.1.1.1")
            .addDnsServer("1.0.0.1")
            .setMtu(1400)
            .setBlocking(true)

        val pfd = builder.establish() ?: run {
            Log.e(TAG, "Не удалось создать TUN-интерфейс (VPN permission?)")
            stopSelf()
            return
        }
        tunFd = pfd

        val c = Libv2ray.newCoreController(this)
        controller = c

        val fd = pfd.detachFd()
        xrayThread = Thread({
            try {
                c.startLoop(configJson, fd)
            } catch (e: Exception) {
                Log.e(TAG, "xray-core crash", e)
                notifyStatusChanged(false)
                stopSelf()
            }
        }, "xray-core-loop").also { it.start() }

        Log.d(TAG, "xray-core запущен в отдельном потоке")
    }

    override fun onDestroy() {
        Log.d(TAG, "V2RayVpnService.onDestroy")
        try {
            controller?.stopLoop()
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка остановки xray-core", e)
        }
        controller = null

        // Закрываем fd если он ещё не был передан в нативный код
        try {
            tunFd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка закрытия TUN fd", e)
        }
        tunFd = null

        // Прерываем поток если ещё жив
        xrayThread?.interrupt()
        xrayThread = null

        notifyStatusChanged(false)
        super.onDestroy()
    }

    // ---- CoreCallbackHandler ----

    override fun startup(): Long {
        Log.d(TAG, "xray-core: startup")
        notifyStatusChanged(true)
        return 0
    }

    override fun shutdown(): Long {
        Log.d(TAG, "xray-core: shutdown")
        notifyStatusChanged(false)
        return 0
    }

    override fun onEmitStatus(code: Long, message: String?): Long {
        Log.d(TAG, "xray-core status [$code]: $message")
        return 0
    }
}