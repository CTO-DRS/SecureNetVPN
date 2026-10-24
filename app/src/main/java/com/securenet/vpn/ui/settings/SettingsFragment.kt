package com.securenet.vpn.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.securenet.vpn.R
import com.securenet.vpn.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
