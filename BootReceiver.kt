package com.navrotvpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Заготовка под автозапуск VPN после перезагрузки.
 *
 * ИСПРАВЛЕНИЯ:
 * - Receiver по умолчанию ОТКЛЮЧЕН в манифесте (android:enabled="false").
 *   Включается программно только после явного согласия пользователя.
 * - Используется SharedPreferences-флаг для хранения настройки.
 * - Добавлено логирование.
 *
 * Для включения (из UI по желанию пользователя):
 *   val pm = context.packageManager
 *   pm.setComponentEnabledSetting(
 *       ComponentName(context, BootReceiver::class.java),
 *       PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
 *       PackageManager.DONT_KILL_APP
 *   )
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val PREF_AUTO_CONNECT = "auto_connect_on_boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoConnect = prefs.getBoolean(PREF_AUTO_CONNECT, false)
        if (!autoConnect) {
            Log.d(TAG, "Автоподключение при загрузке отключено")
            return
        }

        Log.i(TAG, "BOOT_COMPLETED, автоподключение включено — TODO: реализовать VpnService.prepare() flow из receiver")

        // TODO: полная реализация требует:
        // 1. Сохранять последний использованный ServerModel в SharedPreferences
        // 2. Вызвать VpnService.prepare() (если вернёт intent — не получится
        //    из receiver без UI, нужен Notification с PendingIntent)
        // 3. Или использовать Always-on VPN как основной механизм
    }
}