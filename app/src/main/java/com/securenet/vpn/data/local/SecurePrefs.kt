package com.securenet.vpn.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardened key-value storage backed by EncryptedSharedPreferences
 * (AES-256 GCM values, AES-256 SIV keys, master key in Android Keystore).
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** AppCompatDelegate.MODE_NIGHT_* constant. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, DEFAULT_THEME)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    var batterySaverMode: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_SAVER, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_SAVER, value).apply()

    var autoConnectOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOT, value).apply()

    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()

    var dnsCacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_DNS_CACHE, true)
        set(value) = prefs.edit().putBoolean(KEY_DNS_CACHE, value).apply()

    var autoMtu: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MTU, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_MTU, value).apply()

    var selectedServerId: String?
        get() = prefs.getString(KEY_SELECTED_SERVER, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_SERVER, value).apply()

    /** Last selected server cached as JSON so dashboard works offline. */
    var cachedSelectedServer: String?
        get() = prefs.getString(KEY_CACHED_SERVER, null)
        set(value) = prefs.edit().putString(KEY_CACHED_SERVER, value).apply()

    /** Raw remote-config JSON cache (servers, pins, feature flags). */
    var cachedRemoteConfig: String?
        get() = prefs.getString(KEY_REMOTE_CONFIG, null)
        set(value) = prefs.edit().putString(KEY_REMOTE_CONFIG, value).apply()

    /**
     * WireGuard private key for the device tunnel identity (base64).
     * Stored encrypted; generated once on first activation.
     */
    var devicePrivateKey: String?
        get() = prefs.getString(KEY_DEVICE_PRIVATE_KEY, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_PRIVATE_KEY, value).apply()

    var remoteConfigFetchedAt: Long
        get() = prefs.getLong(KEY_REMOTE_CONFIG_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_REMOTE_CONFIG_AT, value).apply()

    /** True when the tunnel was up before the process died (auto-restore). */
    var wasConnectedBeforeShutdown: Boolean
        get() = prefs.getBoolean(KEY_WAS_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAS_CONNECTED, value).apply()

    var essentialPermissionsGranted: Boolean
        get() = prefs.getBoolean(KEY_ESSENTIAL_PERMS, false)
        set(value) = prefs.edit().putBoolean(KEY_ESSENTIAL_PERMS, value).apply()

    // ------------------------------------------------------------------
    // v1.1: local intelligence + new features
    // ------------------------------------------------------------------

    /** App language tag: "" (system), "en" or "ar". */
    var languageTag: String
        get() = prefs.getString(KEY_LANGUAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Kill switch: aggressive reconnect + system always-on hand-off. */
    var killSwitchEnabled: Boolean
        get() = prefs.getBoolean(KEY_KILL_SWITCH, false)
        set(value) = prefs.edit().putBoolean(KEY_KILL_SWITCH, value).apply()

    /** Connect automatically when the app opens. */
    var autoConnectOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    /** 0 = off, 1 = allow only listed apps, 2 = exclude listed apps. */
    var splitTunnelMode: Int
        get() = prefs.getInt(KEY_SPLIT_MODE, SPLIT_OFF)
        set(value) = prefs.edit().putInt(KEY_SPLIT_MODE, value).apply()

    /** Raw JSON array of package names for the split tunnel policy. */
    var splitTunnelApps: String
        get() = prefs.getString(KEY_SPLIT_APPS, "[]") ?: "[]"
        set(value) = prefs.edit().putString(KEY_SPLIT_APPS, value).apply()

    /** Last speed-test result JSON + history ring buffer JSON. */
    var lastSpeedTest: String?
        get() = prefs.getString(KEY_SPEED_LAST, null)
        set(value) = prefs.edit().putString(KEY_SPEED_LAST, value).apply()

    var speedTestHistory: String
        get() = prefs.getString(KEY_SPEED_HISTORY, "[]") ?: "[]"
        set(value) = prefs.edit().putString(KEY_SPEED_HISTORY, value).apply()

    /** Live latency cache: {serverId: ms} JSON, refreshed by Smart Connect. */
    var latencyCache: String
        get() = prefs.getString(KEY_LATENCY_CACHE, "{}") ?: "{}"
        set(value) = prefs.edit().putString(KEY_LATENCY_CACHE, value).apply()

    var lastScanAt: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN, value).apply()

    var lastScanSummary: String?
        get() = prefs.getString(KEY_LAST_SCAN_SUMMARY, null)
        set(value) = prefs.edit().putString(KEY_LAST_SCAN_SUMMARY, value).apply()

    var lastScanJson: String?
        get() = prefs.getString(KEY_LAST_SCAN_JSON, null)
        set(value) = prefs.edit().putString(KEY_LAST_SCAN_JSON, value).apply()

    fun clearLogsMarker(): Boolean {
        prefs.getString(KEY_LOG_FILE, null)?.let { return true }
        return false
    }

    fun markLogFile(name: String) = prefs.edit().putString(KEY_LOG_FILE, name).apply()

    fun logFileName(): String? = prefs.getString(KEY_LOG_FILE, null)

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "securenet_vault"
        private const val DEFAULT_THEME = -1 // MODE_NIGHT_FOLLOW_SYSTEM

        private const val KEY_ONBOARDING = "onboarding_completed"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BATTERY_SAVER = "battery_saver"
        private const val KEY_AUTO_BOOT = "auto_connect_boot"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_DNS_CACHE = "dns_cache_enabled"
        private const val KEY_AUTO_MTU = "auto_mtu"
        private const val KEY_SELECTED_SERVER = "selected_server_id"
        private const val KEY_CACHED_SERVER = "cached_selected_server_json"
        private const val KEY_REMOTE_CONFIG = "remote_config_json"
        private const val KEY_DEVICE_PRIVATE_KEY = "device_private_key"
        private const val KEY_REMOTE_CONFIG_AT = "remote_config_fetched_at"
        private const val KEY_WAS_CONNECTED = "was_connected_before_shutdown"
        private const val KEY_ESSENTIAL_PERMS = "essential_permissions_granted"
        private const val KEY_LAST_SCAN = "last_scan_at"
        private const val KEY_LAST_SCAN_SUMMARY = "last_scan_summary"
        private const val KEY_LAST_SCAN_JSON = "last_scan_json"
        private const val KEY_LOG_FILE = "log_file_name"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_KILL_SWITCH = "kill_switch"
        private const val KEY_AUTO_LAUNCH = "auto_connect_launch"
        private const val KEY_SPLIT_MODE = "split_tunnel_mode"
        private const val KEY_SPLIT_APPS = "split_tunnel_apps"
        private const val KEY_SPEED_LAST = "speed_test_last"
        private const val KEY_SPEED_HISTORY = "speed_test_history"
        private const val KEY_LATENCY_CACHE = "latency_cache"

        const val SPLIT_OFF = 0
        const val SPLIT_ALLOW_ONLY = 1
        const val SPLIT_EXCLUDE = 2
    }
}
