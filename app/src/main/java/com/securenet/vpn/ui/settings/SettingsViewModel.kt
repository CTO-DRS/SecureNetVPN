package com.securenet.vpn.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.securenet.vpn.data.local.ConnectionLogger
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.nativelib.NativeBridge
import com.securenet.vpn.nativelib.WgNative
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val securePrefs: SecurePrefs,
    private val logger: ConnectionLogger
) : ViewModel() {

    data class SettingsUi(
        val themeMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        val batterySaver: Boolean = false,
        val autoBoot: Boolean = false,
        val autoReconnect: Boolean = true,
        val dnsCache: Boolean = true,
        val autoMtu: Boolean = true,
        val nativeInfo: String = "",
        val logsCleared: Boolean = false,
        val languageTag: String = "",
        val killSwitch: Boolean = false,
        val autoLaunch: Boolean = false,
        val splitTunnelLabel: String = "",
        val dnsShield: Boolean = false,
        val smartRules: Boolean = false,
        val torOverVpn: Boolean = false,
        val torStatusLine: String = "",
        val scheduleSummary: String = ""
    )

    private val _ui = MutableStateFlow(load())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    fun load(): SettingsUi {
        val nativeInfo = buildString {
            if (WgNative.isAvailable) {
                append("WireGuard core ").append(runCatching { WgNative.wgVersion() }.getOrDefault("?"))
            } else {
                append("simulation mode (Go backend not built)")
            }
            if (NativeBridge.isAvailable) {
                append(" | C++ ").append(runCatching { NativeBridge.nativeVersion() }.getOrDefault("?"))
            }
        }
        return SettingsUi(
            themeMode = securePrefs.themeMode,
            batterySaver = securePrefs.batterySaverMode,
            autoBoot = securePrefs.autoConnectOnBoot,
            autoReconnect = securePrefs.autoReconnect,
            dnsCache = securePrefs.dnsCacheEnabled,
            autoMtu = securePrefs.autoMtu,
            nativeInfo = nativeInfo,
            languageTag = securePrefs.languageTag,
            killSwitch = securePrefs.killSwitchEnabled,
            autoLaunch = securePrefs.autoConnectOnLaunch,
            splitTunnelLabel = splitTunnelLabel(),
            dnsShield = securePrefs.dnsShieldEnabled,
            smartRules = securePrefs.smartRulesEnabled,
            torOverVpn = securePrefs.torOverVpnEnabled,
            torStatusLine = torStatusLine(),
            scheduleSummary = scheduleSummary()
        )
    }

    /** v1.3: human-readable Tor mode state for the settings row. */
    fun torStatusLine(): String =
        if (securePrefs.torOverVpnEnabled) "Armed" else "Off"

    /** v1.3: schedule summary for the settings row. */
    fun scheduleSummary(): String {
        val schedule = com.securenet.vpn.schedule.ConnectionSchedule.fromJson(
            securePrefs.connectionSchedule
        )
        return com.securenet.vpn.schedule.ConnectionScheduleEngine.describe(schedule)
    }

    fun splitTunnelLabel(): String {
        val policy = com.securenet.vpn.ui.splittunnel.SplitTunnelStore.decode(
            securePrefs.splitTunnelApps, securePrefs.splitTunnelMode
        )
        return when (policy.mode) {
            com.securenet.vpn.ui.splittunnel.SplitTunnelStore.Mode.OFF -> "Off"
            com.securenet.vpn.ui.splittunnel.SplitTunnelStore.Mode.ALLOW_ONLY ->
                "Allow (${policy.packages.size})"
            com.securenet.vpn.ui.splittunnel.SplitTunnelStore.Mode.EXCLUDE ->
                "Exclude (${policy.packages.size})"
        }
    }

    fun setThemeMode(mode: Int) {
        securePrefs.themeMode = mode
        AppCompatDelegate.setDefaultNightMode(mode)
        _ui.value = _ui.value.copy(themeMode = mode)
    }

    fun setBatterySaver(enabled: Boolean) {
        securePrefs.batterySaverMode = enabled
        _ui.value = _ui.value.copy(batterySaver = enabled)
    }

    fun setAutoBoot(enabled: Boolean) {
        securePrefs.autoConnectOnBoot = enabled
        _ui.value = _ui.value.copy(autoBoot = enabled)
    }

    fun setAutoReconnect(enabled: Boolean) {
        securePrefs.autoReconnect = enabled
        _ui.value = _ui.value.copy(autoReconnect = enabled)
    }

    fun setDnsCache(enabled: Boolean) {
        securePrefs.dnsCacheEnabled = enabled
        _ui.value = _ui.value.copy(dnsCache = enabled)
    }

    fun setAutoMtu(enabled: Boolean) {
        securePrefs.autoMtu = enabled
        _ui.value = _ui.value.copy(autoMtu = enabled)
    }

    /** Per-app language (works back to API 24 via appcompat storage). */
    fun setLanguage(tag: String) {
        securePrefs.languageTag = tag
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
        _ui.value = _ui.value.copy(languageTag = tag)
    }

    fun setKillSwitch(enabled: Boolean) {
        securePrefs.killSwitchEnabled = enabled
        _ui.value = _ui.value.copy(killSwitch = enabled)
    }

    fun setAutoLaunch(enabled: Boolean) {
        securePrefs.autoConnectOnLaunch = enabled
        _ui.value = _ui.value.copy(autoLaunch = enabled)
    }

    /** v1.2: DNS Shield toggle (blocklist + filtering tunnel resolvers). */
    fun setDnsShield(enabled: Boolean) {
        securePrefs.dnsShieldEnabled = enabled
        _ui.value = _ui.value.copy(dnsShield = enabled)
    }

    /** v1.2: Smart Network Rules (auto-connect on untrusted networks). */
    fun setSmartRules(enabled: Boolean) {
        securePrefs.smartRulesEnabled = enabled
        _ui.value = _ui.value.copy(smartRules = enabled)
    }

    /** v1.3: Tor-over-VPN mode toggle. */
    fun setTorOverVpn(enabled: Boolean) {
        securePrefs.torOverVpnEnabled = enabled
        _ui.value = _ui.value.copy(torOverVpn = enabled, torStatusLine = torStatusLine())
    }

    /** v1.3: refresh the schedule summary after the dialog saved. */
    fun refreshScheduleSummary() {
        _ui.value = _ui.value.copy(scheduleSummary = scheduleSummary())
    }

    fun refreshSplitTunnelLabel() {
        _ui.value = _ui.value.copy(splitTunnelLabel = splitTunnelLabel())
    }

    fun clearLogs() {
        logger.clear()
        logger.appendCustom("logs_cleared")
        _ui.value = _ui.value.copy(logsCleared = true)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
            container.securePrefs,
            container.connectionLogger
        ) as T
    }
}
