package com.securenet.vpn.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.lottie.LottieAnimationView
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.data.model.ConnectionState
import com.securenet.vpn.databinding.FragmentDashboardBinding
import com.securenet.vpn.nativelib.NativeBridge
import com.securenet.vpn.nativelib.WgNative
import com.securenet.vpn.service.SecureNetVpnService
import com.securenet.vpn.util.BatteryOptimizationHelper
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dashboard: giant activation button, Lottie connection animation, live
 * stats. Implements the spec'd user flow:
 *   Activate -> runtime permissions -> VPN consent -> animation -> connected
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer()
            .let { DashboardViewModel.Factory(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyBatteryModeVisuals()

        binding.btnPower.setOnClickListener { onPowerPressed() }
        binding.cardCurrentServer.setOnClickListener {
            (activity as? MainActivity)?.bottomNav()?.selectedItemId = R.id.nav_servers
        }

        // v1.1: speed test + smart connect interactions
        binding.btnSpeedTest.setOnClickListener {
            viewModel.runSpeedTest(viewModel.selectedServer.value)
        }
        binding.btnSmartApply.setOnClickListener {
            viewModel.smartSuggestion.value?.let { target ->
                viewModel.selectServer(target)
                binding.cardSmartSuggest.visibility = View.GONE
            }
        }

        observeState()
        observeStats()
        observeIntelligence()
    }

    private fun onPowerPressed() {
        val host = activity as? MainActivity ?: return
        when (viewModel.connectionState.value) {
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> viewModel.deactivate()

            else -> {
                // Step 1 of the spec'd flow: runtime permissions, then consent.
                host.requestEssentialPermissions {
                    host.requestVpnConsent(
                        onGranted = { viewModel.activate() },
                        onDenied = { /* snackbar already shown by host */ }
                    )
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionState.collect { state ->
                    render(state)
                    if (state == ConnectionState.REQUESTING_PERMISSION) {
                        (activity as? MainActivity)?.requestVpnConsent(
                            onGranted = { SecureNetVpnService.requestReconnect(requireContext()) },
                            onDenied = { }
                        )
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedServer.collect { server ->
                    binding.tvCurrentServer.text =
                        server?.let { "${it.city} (${it.countryCode})" }
                            ?: getString(R.string.no_server_selected)
                }
            }
        }
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stats.collect { stats ->
                    binding.tvDownload.text = formatSpeed(stats.rxSpeedBytesPerSec)
                    binding.tvUpload.text = formatSpeed(stats.txSpeedBytesPerSec)
                    binding.tvDuration.text = formatDuration(stats.durationMillis)
                }
            }
        }
        // DNS cache badge refreshes on resume only (cheap, non-ticking).
        binding.tvDnsCache.text = viewModel.dnsStats.value.ifEmpty {
            getString(R.string.dns_cache_idle)
        }
    }

    /** v1.1: security score + speed test + smart suggestion rendering. */
    private fun observeIntelligence() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.securityScore.collect { report ->
                    val score = report?.score ?: return@collect
                    binding.tvScoreValue.text = score.total.toString()
                    binding.tvScoreGrade.text = score.grade.name.let { grade ->
                        when (grade) {
                            "EXCELLENT" -> "A+"; "GOOD" -> "B"; "FAIR" -> "C"
                            "POOR" -> "D"; else -> "F"
                        }
                    }
                    binding.progressScore.setProgressCompat(score.total, true)
                    binding.tvScoreAction.setText(when (report.actions.firstOrNull()) {
                        com.securenet.vpn.intelligence.SecurityScoreEngine.Action.RUN_DEVICE_SCAN ->
                            R.string.action_run_scan
                        com.securenet.vpn.intelligence.SecurityScoreEngine.Action.UNINSTALL_HIGH_RISK_APPS ->
                            R.string.action_remove_threats
                        com.securenet.vpn.intelligence.SecurityScoreEngine.Action.CONNECT_VPN ->
                            R.string.action_connect
                        com.securenet.vpn.intelligence.SecurityScoreEngine.Action.ENABLE_DNS_CACHE ->
                            R.string.action_enable_dns
                        com.securenet.vpn.intelligence.SecurityScoreEngine.Action.ENABLE_AUTO_MTU ->
                            R.string.action_enable_mtu
                        com.securenet.vpn.intelligence.SecurityScoreEngine.Action.ENABLE_AUTO_RECONNECT ->
                            R.string.action_enable_reconnect
                        null -> R.string.action_all_good
                        else -> R.string.action_all_good
                    })
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.speedTest.collect { ui ->
                    binding.btnSpeedTest.isEnabled = !ui.running
                    binding.btnSpeedTest.setText(
                        if (ui.running) R.string.speed_btn_running else R.string.speed_btn_run
                    )
                    binding.tvSpeedPing.text = if (ui.pingMs > 0) "${ui.pingMs} ms" else "—"
                    binding.tvSpeedDown.text = if (ui.downMbps > 0)
                        String.format(Locale.US, "%.1f", ui.downMbps) else "—"
                    binding.tvSpeedUp.text = if (ui.upMbps > 0)
                        String.format(Locale.US, "%.1f", ui.upMbps) else "—"
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.smartSuggestion.collect { server ->
                    if (server == null) {
                        binding.cardSmartSuggest.visibility = View.GONE
                    } else {
                        binding.tvSmartSuggest.text = getString(
                            R.string.dashboard_smart_text, "${server.city} (${server.countryCode})"
                        )
                        binding.cardSmartSuggest.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun render(state: ConnectionState) {
        val (statusRes, colorRes) = when (state) {
            ConnectionState.CONNECTED ->
                R.string.dashboard_status_connected to R.color.status_connected
            ConnectionState.CONNECTING ->
                R.string.dashboard_status_connecting to R.color.status_connecting
            ConnectionState.RECONNECTING ->
                R.string.dashboard_status_reconnecting to R.color.status_reconnecting
            ConnectionState.DISCONNECTING ->
                R.string.dashboard_status_disconnecting to R.color.status_connecting
            ConnectionState.REQUESTING_PERMISSION ->
                R.string.dashboard_status_connecting to R.color.status_connecting
            ConnectionState.FAILED ->
                R.string.dashboard_status_failed to R.color.status_failed
            ConnectionState.DISCONNECTED ->
                R.string.dashboard_status_disconnected to R.color.status_disconnected
        }

        binding.tvStatusPill.setText(statusRes)
        binding.tvStatusPill.setTextColor(
            ContextCompat.getColor(requireContext(), colorRes)
        )

        val connected = state == ConnectionState.CONNECTED
        val animating = state == ConnectionState.CONNECTING ||
            state == ConnectionState.RECONNECTING ||
            state == ConnectionState.DISCONNECTING ||
            state == ConnectionState.REQUESTING_PERMISSION

        binding.connectionAnimation.apply {
            if (animating) {
                setAnimation(R.raw.lottie_connecting)
                playAnimation()
            } else if (connected) {
                setAnimation(R.raw.lottie_connected)
                playAnimation()
            } else {
                pauseAnimation()
            }
        }

        binding.btnPower.setImageResource(R.drawable.ic_power)
        binding.btnPower.imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (connected) R.color.power_connected else R.color.power_disconnected
            )
        )

        binding.tvConnectionTip.setText(
            if (connected) R.string.dashboard_tip_connected
            else R.string.dashboard_tip_disconnected
        )

        binding.tvBatteryMode.visibility =
            if (viewModel.batterySaverEnabled() && connected) View.VISIBLE else View.GONE
    }

    private fun applyBatteryModeVisuals() {
        if (viewModel.batterySaverEnabled() &&
            !BatteryOptimizationHelper.isIgnoringOptimizations(requireContext())
        ) {
            // Gently surface the battery optimization request once.
            viewLifecycleOwner.lifecycleScope.launch {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root, R.string.battery_request_desc,
                    com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.ok) {
                    try {
                        startActivity(
                            BatteryOptimizationHelper.requestIgnoreOptimizationsIntent(requireContext())
                        )
                    } catch (_: Exception) {
                    }
                }.show()
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String = when {
        bytesPerSec >= 1_000_000 ->
            String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000.0)
        bytesPerSec >= 1_000 ->
            String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1_000.0)
        else -> "$bytesPerSec B/s"
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    override fun onResume() {
        super.onResume()
        // Show backend capability honestly in logs (About shows it too).
        if (WgNative.isAvailable && NativeBridge.isAvailable) {
            NativeBridge.selfTest()
        }
        // Keep the on-device intelligence fresh.
        viewModel.refreshSecurityScore()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
