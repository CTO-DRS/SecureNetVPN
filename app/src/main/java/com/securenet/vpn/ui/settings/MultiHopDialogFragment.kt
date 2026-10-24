package com.securenet.vpn.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.securenet.vpn.R
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.databinding.DialogMultihopBinding
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.multihop.MultiHopChain
import com.securenet.vpn.multihop.MultiHopEngine
import kotlinx.coroutines.launch

/**
 * v1.4: Multi-hop chain editor — pick an ENTRY relay and an EXIT server.
 * The chain persists self-contained (both server payloads serialized) so
 * the live connect path and the service's cached-config path can rebuild
 * the plan offline.
 */
class MultiHopDialogFragment : DialogFragment() {

    private var _binding: DialogMultihopBinding? = null
    private val binding get() = _binding!!

    private var servers: List<VpnServer> = emptyList()
    private var entryId: String? = null
    private var exitId: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMultihopBinding.inflate(layoutInflater)
        val container = (requireActivity().application as AppProvider).provideContainer()

        val current = MultiHopChain.fromJson(container.securePrefs.multiHopChain)
        binding.switchMultihop.isChecked = current.enabled

        lifecycleScope.launch {
            servers = runCatching {
                container.serverRepository.getServers(forceRefresh = false)
            }.getOrDefault(emptyList())
            populateSpinners(current)
        }

        binding.spinnerEntry.onItemSelectedListener = picker(true)
        binding.spinnerExit.onItemSelectedListener = picker(false)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.multihop_dialog_title)
            .setView(binding.root)
            .setPositiveButton(R.string.schedule_save) { _, _ -> save(container) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun populateSpinners(current: MultiHopChain) {
        if (servers.isEmpty() || _binding == null) return
        val labels = servers.map { "${it.name} · ${it.city}" }
        val entryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        entryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerEntry.adapter = entryAdapter
        binding.spinnerExit.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        current.entryServer()?.let { entry ->
            servers.indexOfFirst { it.id == entry.id }.takeIf { it >= 0 }
                ?.let { binding.spinnerEntry.setSelection(it, false) }
        }
        current.exitServer()?.let { exit ->
            servers.indexOfFirst { it.id == exit.id }.takeIf { it >= 0 }
                ?.let { binding.spinnerExit.setSelection(it, false) }
        }
    }

    private fun picker(isEntry: Boolean) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
            if (isEntry) entryId = servers.getOrNull(pos)?.id
            else exitId = servers.getOrNull(pos)?.id
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    private fun save(container: com.securenet.vpn.di.AppContainer) {
        val enabled = binding.switchMultihop.isChecked
        val entry = servers.firstOrNull { it.id == entryId }
            ?: servers.firstOrNull()
        val exit = servers.firstOrNull { it.id == exitId }
            ?: servers.getOrNull(1)
            ?: entry

        if (enabled && (entry == null || exit == null)) {
            Toast.makeText(requireContext(), R.string.multihop_no_servers, Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (enabled && entry != null && exit != null &&
            MultiHopEngine.validate(entry, exit) != MultiHopEngine.Validation.OK
        ) {
            Toast.makeText(requireContext(), R.string.multihop_same_server, Toast.LENGTH_SHORT)
                .show()
            return
        }

        val chain = MultiHopChain(
            enabled = enabled,
            entryJson = entry?.toVpnServerJson() ?: "",
            exitJson = exit?.toVpnServerJson() ?: ""
        )
        container.securePrefs.multiHopChain = chain.toJson()
        Toast.makeText(requireContext(), R.string.schedule_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
