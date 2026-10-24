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
            onClick = { viewModel.select(it) }
        )
        binding.serversList.layoutManager = LinearLayoutManager(requireContext())
        binding.serversList.adapter = adapter

        binding.btnRefresh.setOnClickListener { viewModel.refresh(forceRefresh = true) }
        binding.btnSmartConnect.setOnClickListener { viewModel.smartConnect() }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
