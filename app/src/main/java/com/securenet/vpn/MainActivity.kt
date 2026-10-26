package com.securenet.vpn

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.securenet.vpn.databinding.ActivityMainBinding
import com.securenet.vpn.service.SecureNetVpnService
import com.securenet.vpn.ui.dashboard.DashboardFragment
import com.securenet.vpn.ui.guide.GuideDetailFragment
import com.securenet.vpn.ui.guide.GuideFragment
import com.securenet.vpn.ui.history.HistoryFragment
import com.securenet.vpn.ui.permissions.PermissionManager
import com.securenet.vpn.ui.scanner.ScannerFragment
import com.securenet.vpn.ui.servers.ServersFragment
import com.securenet.vpn.ui.settings.SettingsFragment

/**
 * Single-activity shell: bottom navigation + fragment hosting. Owns the
 * permission pipeline and the system VPN-consent round trip so fragments
 * stay dumb.
 */
/**
 * Contract implemented by MainActivity so fragments can trigger the
 * permission pipeline and the system VPN-consent round trip.
 */
interface PermissionRequestHost {
    fun requestVpnConsent(onGranted: () -> Unit, onDenied: () -> Unit)
    fun requestEssentialPermissions(onAllResolved: () -> Unit)
    fun hasEssentialPermissions(): Boolean
}

class MainActivity : AppCompatActivity(), PermissionRequestHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager

    /** Callback parked while the system VPN dialog is on screen. */
    private var vpnConsentCallback: ((Boolean) -> Unit)? = null

    private val vpnConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted = result.resultCode == RESULT_OK
            vpnConsentCallback?.invoke(granted)
            vpnConsentCallback = null
            if (!granted) {
                Snackbar.make(binding.root, R.string.error_vpn_permission, Snackbar.LENGTH_LONG)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_servers -> ServersFragment()
                R.id.nav_scanner -> ScannerFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commitAllowingStateLoss()
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_dashboard
            maybeAutoConnectOnLaunch()
        }
    }

    /**
     * v1.1: auto-connect on app start. Only fires when the user enabled it,
     * the essential permission pipeline is already done, and no tunnel is
     * up. The service reconnects from the cached server config.
     */
    private fun maybeAutoConnectOnLaunch() {
        val container = (application as com.securenet.vpn.di.AppProvider).provideContainer()
        // v1.6: Always-on VPN implies connect-on-launch as well.
        if (!container.securePrefs.autoConnectOnLaunch &&
            !container.securePrefs.alwaysOnEnabled
        ) {
            return
        }
        if (!hasEssentialPermissions()) return
        if (SecureNetVpnService.currentState() !=
            com.securenet.vpn.data.model.ConnectionState.DISCONNECTED
        ) return
        if (container.securePrefs.cachedSelectedServer == null) return
        SecureNetVpnService.requestReconnect(this)
    }

    /**
     * System VPN consent round trip. When the service already resolved
     * consent (pendingConsentIntent set), that intent is reused.
     */
    override fun requestVpnConsent(onGranted: () -> Unit, onDenied: () -> Unit) {
        val consentIntent: Intent? = SecureNetVpnService.consumeConsentIntent()
            ?: VpnService.prepare(this)

        if (consentIntent == null) {
            onGranted()
            return
        }
        vpnConsentCallback = { granted -> if (granted) onGranted() else onDenied() }
        vpnConsentLauncher.launch(consentIntent)
    }

    override fun requestEssentialPermissions(onAllResolved: () -> Unit) {
        permissionManager.requestEssentialPermissions(onAllResolved)
    }

    override fun hasEssentialPermissions(): Boolean =
        permissionManager.hasEssentialPermissions()

    /** Exposed for the dashboard power button. */
    fun bottomNav(): BottomNavigationView = binding.bottomNav

    fun root(): View = binding.root

    /** v1.8: show/hide the bottom bar (hidden while the full guide is open). */
    fun setBottomNavVisible(visible: Boolean) {
        binding.bottomNav.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * v1.8: full-screen WireGuard config-sources guide from Settings (or the
     * quick bottom sheet). Pushed on the back stack; [GuideFragment] restores
     * the bottom bar when it is popped by any path.
     */
    fun openConfigGuide() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, GuideFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
        setBottomNavVisible(false)
    }

    /**
     * v1.8.7 — per-source detail page for one WireGuard config source,
     * opened by tapping a card in the guide (list or statistics ranking) or
     * a related row on the detail screen itself. Same hosting contract as
     * [openConfigGuide]: bottom nav hidden, restored on any pop path.
     */
    fun openGuideDetail(sourceId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, GuideDetailFragment.newInstance(sourceId))
            .addToBackStack(null)
            .commitAllowingStateLoss()
        setBottomNavVisible(false)
    }

    companion object {
        /** Permissions explained + requested one at a time, in this order. */
        val ESSENTIAL_PERMISSIONS = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        /** POST_NOTIFICATIONS only exists on API 33+; older devices skip it. */
        fun essentialPermissions(): List<String> =
            if (android.os.Build.VERSION.SDK_INT >= 33) ESSENTIAL_PERMISSIONS
            else ESSENTIAL_PERMISSIONS - Manifest.permission.POST_NOTIFICATIONS

        fun start(context: android.content.Context) {
            context.startActivity(Intent(context, MainActivity::class.java))
        }
    }
}
