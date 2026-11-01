package com.securenet.vpn

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.securenet.vpn.databinding.ActivityMainBinding
import com.securenet.vpn.service.SecureNetVpnService
import com.securenet.vpn.ui.dashboard.DashboardFragment
import com.securenet.vpn.guide.WireGuideCatalog
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

    // ------------------------------------------------------------------
    // v1.8.24 — app lock («قفل التطبيق»)
    // ------------------------------------------------------------------

    /** Opaque cover added over the whole shell while the lock is engaged. */
    private var lockOverlay: View? = null

    /**
     * True while a device-credential round trip (platform BiometricPrompt
     * or the legacy confirm-credential screen) is in flight. The legacy
     * screen backgrounds this activity — without the flag, onStop would
     * record a fresh pause and onStart would immediately re-lock, looping
     * forever. BiometricPrompt itself is an in-place overlay, but the flag
     * shields both paths uniformly.
     */
    private var unlockInFlight = false

    private val appLockCredentialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            unlockInFlight = false
            if (result.resultCode == RESULT_OK) hideLockOverlay()
            // Anything else: the overlay stays with its Unlock button.
        }

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
            consumeWidgetDetailIntent(intent)
        }
    }

    /**
     * v1.8.11 — smart-picks widget taps land here with the source id to
     * open. singleTask delivers relaunches via [onNewIntent]; a cold start
     * carries the extra on the launch intent. The extra is consumed so a
     * configuration recreation does not re-open the detail page.
     */
    private fun consumeWidgetDetailIntent(launchIntent: Intent?) {
        val sourceId = launchIntent?.getStringExtra(EXTRA_OPEN_GUIDE_DETAIL) ?: return
        launchIntent.removeExtra(EXTRA_OPEN_GUIDE_DETAIL)
        if (WireGuideCatalog.byId(sourceId) != null) {
            openGuideDetail(sourceId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeWidgetDetailIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        maybeShowAppLock()
    }

    override fun onStop() {
        super.onStop()
        // Record the backgrounding instant the lock decision will weigh —
        // but never during an in-flight credential screen (see the flag).
        if (!unlockInFlight) {
            (application as com.securenet.vpn.di.AppProvider).provideContainer()
                .securePrefs.appLockLastPausedAt = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        // Any signal change (stars, favorites, notes, restore, deletes)
        // repaints the smart-picks widget the moment the app is foreground.
        com.securenet.vpn.widget.GuideSmartWidgetProvider.pushUpdate(this)
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

    // ------------------------------------------------------------------
    // v1.8.24 — app lock: gate, overlay, credential round trip
    // ------------------------------------------------------------------

    /** The pure [AppLockPolicy] decision, evaluated on every foregrounding. */
    private fun maybeShowAppLock() {
        if (unlockInFlight) return // a credential screen is mid-round-trip
        val prefs = (application as com.securenet.vpn.di.AppProvider)
            .provideContainer().securePrefs
        val locked = com.securenet.vpn.security.AppLockPolicy.shouldLock(
            enabled = prefs.appLockEnabled,
            timeout = com.securenet.vpn.security.AppLockPolicy
                .Timeout.from(prefs.appLockTimeout),
            lastPausedAt = prefs.appLockLastPausedAt,
            nowMillis = System.currentTimeMillis()
        )
        if (!locked) return
        showLockOverlay()
        authenticateUnlock()
    }

    /**
     * Opaque full-screen cover: while it is up, no dashboard content is
     * visible or touchable — the app lock is a privacy gate, not a joke.
     */
    private fun showLockOverlay() {
        if (lockOverlay != null) return
        val overlay = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(resolveBackgroundArgb())
            isClickable = true
            isFocusable = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val icon = ImageView(this).apply {
            setImageResource(com.securenet.vpn.R.drawable.ic_lock)
            setColorFilter(resolveColorAttr(com.google.android.material.R.attr.colorPrimary))
        }
        val iconParams = LinearLayout.LayoutParams(dp(56), dp(56))
        iconParams.setMargins(0, 0, 0, dp(16))
        icon.layoutParams = iconParams
        column.addView(icon)

        column.addView(TextView(this).apply {
            text = getString(com.securenet.vpn.R.string.applock_overlay_title)
            setTextColor(resolveColorAttr(android.R.attr.textColorPrimary))
            textSize = 18f
            gravity = Gravity.CENTER
        })
        column.addView(TextView(this).apply {
            text = getString(com.securenet.vpn.R.string.applock_overlay_desc)
            setTextColor(resolveColorAttr(android.R.attr.textColorSecondary))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(6), dp(24), 0)
        })
        column.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = getString(com.securenet.vpn.R.string.applock_unlock)
            setOnClickListener { authenticateUnlock() }
        }.also { button ->
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, dp(24), 0, 0)
            button.layoutParams = params
        })

        overlay.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        (binding.root as? ViewGroup)?.addView(overlay)
        lockOverlay = overlay
    }

    private fun hideLockOverlay() {
        lockOverlay?.let { cover ->
            (cover.parent as? ViewGroup)?.removeView(cover)
        }
        lockOverlay = null
    }

    /**
     * Platform BiometricPrompt (API 28+, minSdk 30) with a biometric-or-
     * credential authenticator set: fingerprint first, device PIN/pattern
     * as the equal fallback. Devices without either land on the legacy
     * confirm-credential screen; OEMs that throw land there too.
     */
    private fun authenticateUnlock() {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            // The device lock disappeared under us — an impossible gate
            // must never trap the user.
            hideLockOverlay()
            return
        }
        unlockInFlight = true
        val cancellation = CancellationSignal()
        try {
            val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
                .setTitle(getString(com.securenet.vpn.R.string.applock_prompt_title))
                .setSubtitle(getString(com.securenet.vpn.R.string.applock_prompt_sub))
                .setDescription(getString(com.securenet.vpn.R.string.applock_prompt_desc))
                .setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                        or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(
                cancellation,
                mainExecutor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult
                    ) {
                        unlockInFlight = false
                        runOnUiThread { hideLockOverlay() }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            // Platform constants (BiometricConstants, inherited):
                            // no enrolled biometrics / no device credential at all
                            // → the prompt cannot run here, try the legacy screen.
                            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
                            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL ->
                                legacyUnlock(keyguard)
                            else -> {
                                // User cancel / timeout / lockout: the cover
                                // stays; the Unlock button retries at will.
                                unlockInFlight = false
                            }
                        }
                    }
                }
            )
        } catch (_: Exception) {
            legacyUnlock(keyguard)
        }
    }

    /** Confirm-credential fallback for devices where the prompt path is unusable. */
    private fun legacyUnlock(keyguard: KeyguardManager) {
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            getString(com.securenet.vpn.R.string.applock_prompt_title),
            getString(com.securenet.vpn.R.string.applock_prompt_desc)
        )
        if (intent == null) {
            unlockInFlight = false
            hideLockOverlay()
            return
        }
        appLockCredentialLauncher.launch(intent)
    }

    private fun resolveColorAttr(attr: Int): Int {
        val value = android.util.TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun resolveBackgroundArgb(): Int {
        val value = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        /**
         * v1.8.11 — the smart-picks widget puts a guide source id under
         * this extra; the activity opens that source's detail page.
         */
        const val EXTRA_OPEN_GUIDE_DETAIL = "open_guide_detail"

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
