package com.securenet.vpn.ui.settings

import androidx.appcompat.app.AppCompatDelegate
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
        val logsCleared: Boolean = false
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
            nativeInfo = nativeInfo
        )
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
