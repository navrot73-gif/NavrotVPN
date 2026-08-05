package com.navrotvpn.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.crypto.KeyPair
import com.wireguard.crypto.Key

/**
 * Генерирует пару ключей Curve25519 прямо на устройстве и хранит приватный
 * ключ в EncryptedSharedPreferences (AES-256, ключ шифрования — в Android
 * Keystore). Приватный ключ никогда не покидает устройство и не должен
 * передаваться на сервер — на сервер копируется только публичный.
 */
object KeyManager {

    private const val PREFS_NAME = "navrotvpn_secure_prefs"
    private const val KEY_PRIVATE = "wg_private_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Возвращает уже сохранённую пару ключей, либо генерирует новую при
     * первом запуске. Вызывать один раз при первом старте приложения либо
     * по нажатию кнопки "Сгенерировать новые ключи" в настройках.
     */
    fun getOrCreateKeyPair(context: Context): KeyPair {
        val store = prefs(context)
        val stored = store.getString(KEY_PRIVATE, null)
        if (stored != null) {
            return KeyPair(Key.fromBase64(stored))
        }
        val fresh = KeyPair()
        store.edit().putString(KEY_PRIVATE, fresh.privateKey.toBase64()).apply()
        return fresh
    }

    /**
     * Принудительно генерирует новую пару ключей и затирает старую.
     * После вызова публичный ключ нужно заново добавить в конфигурацию
     * сервера — иначе подключение перестанет проходить handshake.
     */
    fun regenerateKeyPair(context: Context): KeyPair {
        val fresh = KeyPair()
        prefs(context).edit().putString(KEY_PRIVATE, fresh.privateKey.toBase64()).apply()
        return fresh
    }

    fun publicKeyBase64(context: Context): String =
        getOrCreateKeyPair(context).publicKey.toBase64()

    fun hasKeyPair(context: Context): Boolean =
        prefs(context).contains(KEY_PRIVATE)
}
