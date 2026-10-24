package com.securenet.vpn.ui.scanner

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
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.databinding.FragmentScannerBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScannerViewModel by viewModels {
        (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer()
            .let { ScannerViewModel.Factory(it) }
    }

    private val adapter = ThreatAdapter()
    private val dateFormat =
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.threatsList.layoutManager = LinearLayoutManager(requireContext())
        binding.threatsList.adapter = adapter

        binding.btnScan.setOnClickListener { viewModel.startScan() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ScannerViewModel.ScanUiState.Idle -> renderIdle()
                        ScannerViewModel.ScanUiState.Scanning -> renderScanning()
                        is ScannerViewModel.ScanUiState.Done -> renderDone(state.result)
                        is ScannerViewModel.ScanUiState.Failed -> renderIdle()
                    }
                }
            }
        }
    }

    private fun renderIdle() {
        binding.scanAnimation.pauseAnimation()
        binding.btnScan.isEnabled = true
        binding.btnScan.setText(R.string.scanner_btn_scan)
        binding.tvScanSummary.setText(R.string.scanner_summary_safe)
        binding.tvScanMeta.text = securePrefsLastScanLabel()
    }

    private fun renderScanning() {
        binding.scanAnimation.playAnimation()
        binding.btnScan.isEnabled = false
        binding.btnScan.setText(R.string.scanner_scanning)
        binding.tvScanSummary.setText(R.string.scanner_summary_safe)
        binding.tvScanMeta.text = ""
        adapter.submitList(emptyList())
    }

    private fun renderDone(result: ScanResult) {
        binding.scanAnimation.pauseAnimation()
        binding.btnScan.isEnabled = true
        binding.btnScan.setText(R.string.scanner_btn_scan)

        binding.tvScanSummary.text = if (result.overallLevel == ThreatLevel.SAFE) {
            getString(R.string.scanner_summary_safe)
        } else {
            getString(R.string.scanner_summary_threats, result.threats.size)
        }
        binding.tvScanMeta.text = getString(
            R.string.scanner_apps_scanned, result.appsScanned, result.filesScanned
        ) + " · " + dateFormat.format(Date(result.completedAtMillis))

        val visible = result.threats.filter { it.level.isActionable }
        adapter.submitList(visible)
        binding.tvThreatsEmpty.visibility =
            if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun securePrefsLastScanLabel(): String {
        val prefs = (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer().securePrefs
        return if (prefs.lastScanAt > 0) {
            getString(R.string.scanner_last_scan, dateFormat.format(Date(prefs.lastScanAt)))
        } else {
            ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
