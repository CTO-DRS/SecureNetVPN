package com.securenet.vpn.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.securenet.vpn.R
import com.securenet.vpn.MainActivity
import com.securenet.vpn.backup.LogBackupEngine
import com.securenet.vpn.databinding.FragmentSettingsBinding
import com.securenet.vpn.di.AppContainer
import com.securenet.vpn.ui.splittunnel.SplitTunnelDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment(), BackupDialogFragment.Host {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer()
            .let { SettingsViewModel.Factory(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = viewModel.ui.value

        bindTheme(ui.themeMode)
        bindLanguage(ui.languageTag)

        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rbThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            viewModel.setThemeMode(mode)
        }

        binding.switchBatterySaver.isChecked = ui.batterySaver
        binding.switchBatterySaver.setOnCheckedChangeListener { _, checked ->
            viewModel.setBatterySaver(checked)
        }

        binding.switchAutoBoot.isChecked = ui.autoBoot
        binding.switchAutoBoot.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoBoot(checked)
        }

        binding.switchAutoReconnect.isChecked = ui.autoReconnect
        binding.switchAutoReconnect.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoReconnect(checked)
        }

        // v1.1: kill switch + auto-launch + split tunneling + language
        binding.switchKillSwitch.isChecked = ui.killSwitch
        binding.switchKillSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setKillSwitch(checked)
        }

        binding.switchAutoLaunch.isChecked = ui.autoLaunch
        binding.switchAutoLaunch.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoLaunch(checked)
        }

        // v1.2: smart rules + DNS shield
        binding.switchSmartRules.isChecked = ui.smartRules
        binding.switchSmartRules.setOnCheckedChangeListener { _, checked ->
            viewModel.setSmartRules(checked)
        }

        binding.switchDnsShield.isChecked = ui.dnsShield
        binding.switchDnsShield.setOnCheckedChangeListener { _, checked ->
            viewModel.setDnsShield(checked)
        }

        // v1.3: Tor-over-VPN + connection schedule
        binding.switchTor.isChecked = ui.torOverVpn
        binding.switchTor.setOnCheckedChangeListener { _, checked ->
            viewModel.setTorOverVpn(checked)
            binding.tvTorState.text =
                getString(R.string.settings_tor_state, viewModel.torStatusLine())
            if (checked) {
                Toast.makeText(
                    requireContext(), R.string.settings_tor_hint, Toast.LENGTH_LONG
                ).show()
            }
        }
        binding.tvTorState.text =
            getString(R.string.settings_tor_state, viewModel.torStatusLine())

        binding.rowSchedule.setOnClickListener {
            ScheduleDialogFragment().show(parentFragmentManager, "schedule_dialog")
        }
        binding.tvScheduleSummary.text = ui.scheduleSummary

        // v1.8: full-screen WireGuard config-sources guide
        binding.rowGuide.setOnClickListener {
            (activity as? MainActivity)?.openConfigGuide()
        }

        // v1.4: multi-hop + hotspot sharing
        binding.rowMultiHop.setOnClickListener {
            MultiHopDialogFragment().show(parentFragmentManager, "multihop_dialog")
        }
        binding.tvMultiHopState.text = ui.multiHopSummary

        binding.rowHotspot.setOnClickListener {
            val container = (requireActivity().application as
                com.securenet.vpn.di.AppProvider).provideContainer()
            container.hotspotShareManager.openTetheringSettings()
        }
        refreshHotspotState()

        // v1.6: Always-on VPN — tunnel re-arms after reboot / app launch /
        // swipe-away without touching the pre-shutdown condition.
        binding.switchAlwaysOn.isChecked = ui.alwaysOn
        binding.switchAlwaysOn.setOnCheckedChangeListener { _, checked ->
            viewModel.setAlwaysOn(checked)
            if (checked) {
                Toast.makeText(
                    requireContext(), R.string.settings_always_on_hint, Toast.LENGTH_LONG
                ).show()
            }
        }

        // v1.6: encrypted log backup (.snback) — passphrase first, then the
        // system document picker handles storage without any permission.
        binding.rowExportBackup.setOnClickListener {
            BackupDialogFragment.newInstance(BackupDialogFragment.MODE_EXPORT)
                .show(parentFragmentManager, "backup_export")
        }
        binding.rowImportBackup.setOnClickListener {
            BackupDialogFragment.newInstance(BackupDialogFragment.MODE_IMPORT)
                .show(parentFragmentManager, "backup_import")
        }

        binding.rowSplitTunnel.setOnClickListener {
            SplitTunnelDialogFragment().show(parentFragmentManager, "split_tunnel")
        }
        renderSplitTunnelLabel(viewModel.splitTunnelLabel())

        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val tag = when (checkedId) {
                R.id.rbLangEnglish -> "en"
                R.id.rbLangArabic -> "ar"
                else -> ""
            }
            viewModel.setLanguage(tag)
        }

        binding.switchDnsCache.isChecked = ui.dnsCache
        binding.switchDnsCache.setOnCheckedChangeListener { _, checked ->
            viewModel.setDnsCache(checked)
        }

        binding.switchMtuAuto.isChecked = ui.autoMtu
        binding.switchMtuAuto.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoMtu(checked)
        }

        binding.btnClearLogs.setOnClickListener {
            viewModel.clearLogs()
            Toast.makeText(
                requireContext(), R.string.settings_logs_cleared, Toast.LENGTH_SHORT
            ).show()
        }

        binding.tvVersion.text = getString(
            R.string.settings_version,
            try {
                requireContext().packageManager
                    .getPackageInfo(requireContext().packageName, 0).versionName
            } catch (_: Exception) {
                "1.0.0"
            }
        )
        binding.tvNativeInfo.text = getString(R.string.settings_native_lib, ui.nativeInfo)
    }

    private fun bindTheme(mode: Int) {
        val checkId = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.rbThemeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.rbThemeDark
            else -> R.id.rbThemeSystem
        }
        binding.rgTheme.check(checkId)
    }

    private fun bindLanguage(tag: String) {
        val checkId = when (tag) {
            "en" -> R.id.rbLangEnglish
            "ar" -> R.id.rbLangArabic
            else -> R.id.rbLangSystem
        }
        binding.rgLanguage.check(checkId)
    }

    private fun renderSplitTunnelLabel(label: String) {
        val res = when {
            label.startsWith("Allow") -> R.string.split_mode_allow
            label.startsWith("Exclude") -> R.string.split_mode_exclude
            else -> R.string.split_mode_off
        }
        val summary = getString(res) +
            if (label.startsWith("Allow") || label.startsWith("Exclude"))
                " · " + label.substringAfter('(').trim('(', ')') + " apps"
            else ""
        binding.tvSplitTunnelState.text = summary
    }

    override fun onResume() {
        super.onResume()
        // Refresh the split-tunnel summary in case the dialog changed it.
        viewModel.refreshSplitTunnelLabel()
        renderSplitTunnelLabel(viewModel.ui.value.splitTunnelLabel)
        // v1.3: refresh schedule summary after the editor dialog saved.
        viewModel.refreshScheduleSummary()
        binding.tvScheduleSummary.text = viewModel.ui.value.scheduleSummary
        // v1.4: refresh multi-hop summary after the editor dialog saved.
        viewModel.refreshMultiHopSummary()
        binding.tvMultiHopState.text = viewModel.ui.value.multiHopSummary
        refreshHotspotState()
 }

    /** v1.4: live tether state for the hotspot row. */
    private fun refreshHotspotState() {
        val container = (requireActivity().application as
            com.securenet.vpn.di.AppProvider).provideContainer()
        val hotspot = container.hotspotShareManager
        val tetherActive = hotspot.refresh()
        val vpnUp = com.securenet.vpn.service.SecureNetVpnService.currentState() ==
            com.securenet.vpn.data.model.ConnectionState.CONNECTED
        binding.tvHotspotState.text = when {
            !hotspot.isTetheringSupported() -> getString(R.string.hotspot_unsupported)
            tetherActive -> getString(
                R.string.hotspot_summary_active,
                getString(if (vpnUp) R.string.hotspot_protected else R.string.hotspot_unprotected)
            )
            else -> getString(R.string.hotspot_summary_off)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingPassphrase.fill('\u0000')
        _binding = null
    }

    // ----------------------------------------------------------------------
    // v1.6: encrypted log backup (.snback)
    // ----------------------------------------------------------------------

    /** Held only between the passphrase dialog confirming and the picker returning. */
    private var pendingPassphrase: CharArray = charArrayOf()

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(LogBackupEngine.MIME_TYPE)
    ) { uri: Uri? -> uri?.let { writeBackup(it) } }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { restoreBackup(it) } }

    override fun onBackupPassphraseConfirmed(mode: String, passphrase: CharArray) {
        pendingPassphrase = passphrase
        if (mode == BackupDialogFragment.MODE_EXPORT) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            exportBackupLauncher.launch(
                "securenet-log-backup-$stamp${LogBackupEngine.FILE_EXTENSION}"
            )
        } else {
            importBackupLauncher.launch(arrayOf("*/*"))
        }
    }

    private fun writeBackup(uri: Uri) {
        val container = appContainer()
        val appCtx = requireContext().applicationContext
        val passphrase = pendingPassphrase
        pendingPassphrase = charArrayOf()
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val lines = container.connectionLogger.readAllLines()
                    val bytes = LogBackupEngine.encode(lines, passphrase)
                    appCtx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("no output stream")
                    passphrase.fill('\u0000')
                    lines.size
                }
            }
            outcome.onSuccess { count ->
                Toast.makeText(
                    appCtx,
                    appCtx.getString(R.string.backup_export_success, count),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Toast.makeText(
                    appCtx, R.string.backup_export_failed, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restoreBackup(uri: Uri) {
        val container = appContainer()
        val appCtx = requireContext().applicationContext
        val passphrase = pendingPassphrase
        pendingPassphrase = charArrayOf()
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = appCtx.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() } ?: error("no input stream")
                    val payload = LogBackupEngine.decode(bytes, passphrase)
                    val merge = LogBackupEngine.merge(
                        container.connectionLogger.readAllLines(),
                        payload.eventLines
                    )
                    if (!container.connectionLogger.replaceAllLines(merge.mergedLines)) {
                        error("log rewrite failed")
                    }
                    container.connectionLogger.appendCustom(
                        "backup_restored",
                        "added=${merge.added}; duplicates=${merge.duplicates}"
                    )
                    passphrase.fill('\u0000')
                    merge
                }
            }
            outcome.onSuccess { merge ->
                Toast.makeText(
                    appCtx,
                    appCtx.getString(
                        R.string.backup_import_success, merge.added, merge.duplicates
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                val res = if (error is LogBackupEngine.BackupException) {
                    R.string.backup_import_wrong_pass
                } else {
                    R.string.backup_import_failed
                }
                Toast.makeText(appCtx, res, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun appContainer() =
        (requireActivity().application as com.securenet.vpn.di.AppProvider).provideContainer()
}
