package com.securenet.vpn.ui.splittunnel

import android.app.Dialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.securenet.vpn.R
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.databinding.DialogSplitTunnelBinding

/**
 * Split tunneling picker: choose the routing mode (off / whitelist /
 * blacklist) and the apps each mode applies to. Persisted through
 * [SecurePrefs]; applied by the VPN service on the next tunnel establish().
 */
class SplitTunnelDialogFragment : DialogFragment() {

    private var _binding: DialogSplitTunnelBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SplitTunnelAppsAdapter
    private lateinit var prefs: SecurePrefs

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        prefs = (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer().securePrefs

        _binding = DialogSplitTunnelBinding.inflate(layoutInflater)
        adapter = SplitTunnelAppsAdapter { /* live toggle; saved on OK */ }

        val policy = SplitTunnelStore.decode(
            prefs.splitTunnelApps, prefs.splitTunnelMode
        )
        adapter.setSelection(policy.packages)
        binding.recyclerApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApps.adapter = adapter
        binding.recyclerApps.visibility = View.GONE

        binding.chipModeOff.setOnClickListener { onModeChanged(SplitTunnelStore.Mode.OFF) }
        binding.chipModeAllow.setOnClickListener { onModeChanged(SplitTunnelStore.Mode.ALLOW_ONLY) }
        binding.chipModeExclude.setOnClickListener { onModeChanged(SplitTunnelStore.Mode.EXCLUDE) }
        when (policy.mode) {
            SplitTunnelStore.Mode.ALLOW_ONLY -> binding.chipModeAllow.isChecked = true
            SplitTunnelStore.Mode.EXCLUDE -> binding.chipModeExclude.isChecked = true
            else -> binding.chipModeOff.isChecked = true
        }
        if (policy.mode != SplitTunnelStore.Mode.OFF) {
            binding.recyclerApps.visibility = View.VISIBLE
        }

        binding.tvModeHint.setText(
            when (policy.mode) {
                SplitTunnelStore.Mode.ALLOW_ONLY -> R.string.split_hint_allow
                SplitTunnelStore.Mode.EXCLUDE -> R.string.split_hint_exclude
                SplitTunnelStore.Mode.OFF -> R.string.split_hint_off
            }
        )

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.split_title)
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ -> saveSelection() }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun onModeChanged(mode: SplitTunnelStore.Mode) {
        binding.tvModeHint.setText(
            when (mode) {
                SplitTunnelStore.Mode.ALLOW_ONLY -> R.string.split_hint_allow
                SplitTunnelStore.Mode.EXCLUDE -> R.string.split_hint_exclude
                SplitTunnelStore.Mode.OFF -> R.string.split_hint_off
            }
        )
        binding.recyclerApps.visibility =
            if (mode == SplitTunnelStore.Mode.OFF) View.GONE else View.VISIBLE
    }

    private fun saveSelection() {
        val mode = when {
            binding.chipModeAllow.isChecked -> SplitTunnelStore.Mode.ALLOW_ONLY
            binding.chipModeExclude.isChecked -> SplitTunnelStore.Mode.EXCLUDE
            else -> SplitTunnelStore.Mode.OFF
        }
        val packages = adapter.selectedPackages()
        prefs.splitTunnelMode = when (mode) {
            SplitTunnelStore.Mode.ALLOW_ONLY -> SecurePrefs.SPLIT_ALLOW_ONLY
            SplitTunnelStore.Mode.EXCLUDE -> SecurePrefs.SPLIT_EXCLUDE
            SplitTunnelStore.Mode.OFF -> SecurePrefs.SPLIT_OFF
        }
        prefs.splitTunnelApps = SplitTunnelStore.encode(SplitTunnelStore.Policy(mode, packages))
    }

    /** Load launchable user apps off the main thread after the dialog shows. */
    override fun onStart() {
        super.onStart()
        Thread {
            val pm = requireContext().packageManager
            val rows = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                .map { info ->
                    AppRow(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        selected = false
                    )
                }
                .sortedBy { it.label.lowercase() }
            requireActivity().runOnUiThread {
                val selected = SplitTunnelStore.decode(
                    prefs.splitTunnelApps, prefs.splitTunnelMode
                ).packages
                val restored = rows.map { it.copy(selected = it.packageName in selected) }
                adapter.submitList(restored)
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
