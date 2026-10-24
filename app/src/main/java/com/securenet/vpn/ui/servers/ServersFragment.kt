package com.securenet.vpn.ui.servers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.securenet.vpn.R
import com.securenet.vpn.databinding.FragmentServersBinding
import kotlinx.coroutines.launch

class ServersFragment : Fragment() {

    private var _binding: FragmentServersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ServersViewModel by viewModels {
        (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer()
            .let { ServersViewModel.Factory(it) }
    }

    private lateinit var adapter: ServerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ServerAdapter(
            selectedIdProvider = { viewModel.selectedId.value },
            onClick = { viewModel.select(it) },
            onLongClick = { showServerOptions(it) }
        )
        binding.serversList.layoutManager = LinearLayoutManager(requireContext())
        binding.serversList.adapter = adapter

        binding.btnRefresh.setOnClickListener { viewModel.refresh(forceRefresh = true) }
        binding.btnSmartConnect.setOnClickListener { viewModel.smartConnect() }
        binding.btnImportConfig.setOnClickListener { showImportDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.servers.collect { list ->
                        adapter.submitList(list)
                        binding.tvServersEmpty.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.selectedId.collect { adapter.notifyDataSetChanged() }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        binding.btnRefresh.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.smartProbing.collect { probing ->
                        binding.btnSmartConnect.isEnabled = !probing
                        binding.btnSmartConnect.setText(
                            if (probing) R.string.server_smart_probing
                            else R.string.server_smart_connect
                        )
                    }
                }
                launch {
                    viewModel.smartPicked.collect { picked ->
                        if (picked != null) {
                            com.google.android.material.snackbar.Snackbar.make(
                                binding.root,
                                getString(R.string.server_smart_picked,
                                    "${picked.city} (${picked.countryCode})"),
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    /** v1.5: import entry point (QR / file / paste bottom sheet). */
    private fun showImportDialog() {
        ConfigImportDialogFragment().show(
            childFragmentManager, ConfigImportDialogFragment::class.java.simpleName
        )
    }

    /** v1.5: long-press options — show QR for any profile, delete imported ones. */
    private fun showServerOptions(server: com.securenet.vpn.data.model.VpnServer) {
        val isCustom = server.id.startsWith(ServerAdapter.CUSTOM_PREFIX)
        val options = mutableListOf(getString(R.string.server_option_show_qr))
        if (isCustom) options += getString(R.string.server_option_delete)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(server.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showQrFor(server)
                    1 -> confirmDelete(server)
                }
            }
            .show()
    }

    private fun showQrFor(server: com.securenet.vpn.data.model.VpnServer) {
        val payload = viewModel.qrPayloadFor(server)
        if (payload == null) {
            com.google.android.material.snackbar.Snackbar.make(
                binding.root, R.string.qr_error_no_key,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).show()
            return
        }
        QrShareDialogFragment.newInstance(payload, server.name).show(
            childFragmentManager, QrShareDialogFragment::class.java.simpleName
        )
    }

    private fun confirmDelete(server: com.securenet.vpn.data.model.VpnServer) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_profile_title)
            .setMessage(getString(R.string.delete_profile_message, server.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.server_option_delete) { _, _ ->
                viewModel.deleteCustom(server.id)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
