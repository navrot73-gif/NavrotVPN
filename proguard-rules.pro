# NavrotVPN — ProGuard правила для release-сборки

# ── WireGuard / AmneziaWG ──────────────────────────────────────────────
# Нативные Go-классы вызываются по имени из JNI — не обрезать
-keep class com.wireguard.android.backend.** { *; }
-keep class com.wireguard.config.** { *; }
-keep class com.wireguard.crypto.** { *; }
-keep class org.amnezia.awg.backend.** { *; }
-keep class org.amnezia.awg.config.** { *; }
-keep class org.amnezia.awg.crypto.** { *; }

# ── Xray / libv2ray ────────────────────────────────────────────────────
-keep class libv2ray.** { *; }

# ── OkHttp ─────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── JSON (org.json) ────────────────────────────────────────────────────
# Стандартная библиотека Android — не трогаем

# ── Модели данных ──────────────────────────────────────────────────────
-keep class com.navrotvpn.network.ServerModel { *; }
-keep class com.navrotvpn.network.VpnProtocol { *; }
-keep class com.navrotvpn.core.ConnectionState { *; }

# ── EncryptedSharedPreferences ─────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Убрать логи в release ──────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
# Оставляем warn и error для диагностики в проде