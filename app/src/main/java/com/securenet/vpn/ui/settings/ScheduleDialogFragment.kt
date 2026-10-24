package com.securenet.vpn.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.securenet.vpn.R
import com.securenet.vpn.data.local.SecurePrefs
import com.securenet.vpn.databinding.DialogScheduleBinding
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.schedule.ConnectionSchedule
import com.securenet.vpn.schedule.ConnectionScheduleEngine
import com.securenet.vpn.work.ScheduleWorker
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * v1.3: Connection schedule editor — connect time, disconnect time and the
 * active weekdays. Changes are persisted to the encrypted prefs and the
 * WorkManager tick is re-armed on save.
 */
class ScheduleDialogFragment : DialogFragment() {

    private var _binding: DialogScheduleBinding? = null
    private val binding get() = _binding!!

    private val prefs: SecurePrefs by lazy {
        (requireActivity().application as AppProvider).provideContainer().securePrefs
    }

    private var connectAt: String = "22:00"
    private var disconnectAt: String = "07:00"

    private val dayButtons by lazy {
        listOf(
            binding.chipMon to 1, binding.chipTue to 2, binding.chipWed to 3,
            binding.chipThu to 4, binding.chipFri to 5, binding.chipSat to 6,
            binding.chipSun to 7
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogScheduleBinding.inflate(layoutInflater)
        val current = ConnectionSchedule.fromJson(prefs.connectionSchedule)

        connectAt = current.connectAt
        disconnectAt = current.disconnectAt
        binding.tvConnectTime.text = connectAt
        binding.tvDisconnectTime.text = disconnectAt

        dayButtons.forEach { (button, day) ->
            button.isChecked = (current.daysMask and (1 shl (day - 1))) != 0
        }

        binding.btnPickConnect.setOnClickListener { pickTime(true) }
        binding.btnPickDisconnect.setOnClickListener { pickTime(false) }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.schedule_dialog_title)
            .setView(binding.root)
            .setPositiveButton(R.string.schedule_save) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun pickTime(isConnect: Boolean) {
        val initial = runCatching {
            LocalTime.parse(if (isConnect) connectAt else disconnectAt)
        }.getOrElse { LocalTime.of(22, 0) }
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(initial.hour)
            .setMinute(initial.minute)
            .setTitleText(
                if (isConnect) R.string.schedule_connect_at else R.string.schedule_disconnect_at
            )
            .build()
        picker.addOnPositiveButtonClickListener {
            val picked = String.format("%02d:%02d", picker.hour, picker.minute)
            if (isConnect) {
                connectAt = picked
                binding.tvConnectTime.text = picked
            } else {
                disconnectAt = picked
                binding.tvDisconnectTime.text = picked
            }
        }
        picker.show(parentFragmentManager, "time_picker")
    }

    private fun save() {
        if (!ConnectionScheduleEngine.isValid(connectAt) ||
            !ConnectionScheduleEngine.isValid(disconnectAt)
        ) {
            Toast.makeText(requireContext(), R.string.schedule_invalid_time, Toast.LENGTH_SHORT)
                .show()
            return
        }
        var mask = 0
        dayButtons.forEach { (button, day) ->
            if (button.isChecked) mask = mask or (1 shl (day - 1))
        }
        val schedule = ConnectionSchedule(
            enabled = true,
            connectAt = connectAt,
            disconnectAt = disconnectAt,
            daysMask = if (mask == 0) ConnectionSchedule.ALL_DAYS else mask
        )
        prefs.connectionSchedule = schedule.toJson()
        ScheduleWorker.syncFromPrefs(requireContext())
        Toast.makeText(requireContext(), R.string.schedule_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
