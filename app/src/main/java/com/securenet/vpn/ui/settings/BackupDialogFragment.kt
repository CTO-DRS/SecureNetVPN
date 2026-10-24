package com.securenet.vpn.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.securenet.vpn.R
import com.securenet.vpn.databinding.DialogBackupPassphraseBinding

/**
 * v1.6 — Passphrase prompt in front of the encrypted log backup flow.
 *
 * EXPORT mode: passphrase + confirmation (min 8 chars), then the host
 * opens the system file picker (ACTION_CREATE_DOCUMENT) and the engine
 * encrypts the session log into a `.snback` container.
 *
 * IMPORT mode: passphrase only, then the host opens the document picker,
 * decrypts and merges the events non-destructively into the live log.
 *
 * The passphrase never goes through an Intent extra — it is delivered to
 * the host activity through [Host] while the fragment is attached.
 */
class BackupDialogFragment : DialogFragment() {

    interface Host {
        fun onBackupPassphraseConfirmed(mode: String, passphrase: CharArray)
    }

    private var _binding: DialogBackupPassphraseBinding? = null
    private val binding get() = _binding!!

    private val mode: String
        get() = arguments?.getString(ARG_MODE) ?: MODE_EXPORT

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBackupPassphraseBinding.inflate(layoutInflater)

        val isExport = mode == MODE_EXPORT
        binding.tvBackupMessage.setText(
            if (isExport) R.string.backup_export_message
            else R.string.backup_import_message
        )
        binding.tilConfirm.visibility = if (isExport) View.VISIBLE else View.GONE

        return AlertDialog.Builder(requireContext())
            .setTitle(
                if (isExport) R.string.backup_export_title
                else R.string.backup_import_title
            )
            .setView(binding.root)
            .setPositiveButton(R.string.backup_action_continue, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (validate(isExport)) {
                            val pass = binding.etBackupPassphrase.text?.toString() ?: ""
                            (activity as? Host)?.onBackupPassphraseConfirmed(mode, pass.toCharArray())
                            dismiss()
                        }
                    }
                }
            }
    }

    private fun validate(isExport: Boolean): Boolean {
        val pass = binding.etBackupPassphrase.text?.toString().orEmpty()
        if (pass.length < MIN_PASSPHRASE_LEN) {
            binding.tilPassphrase.error = getString(R.string.backup_passphrase_too_short)
            return false
        }
        binding.tilPassphrase.error = null
        if (!isExport) return true

        val confirm = binding.etBackupConfirm.text?.toString().orEmpty()
        if (confirm != pass) {
            binding.tilConfirm.error = getString(R.string.backup_passphrase_mismatch)
            return false
        }
        binding.tilConfirm.error = null
        return true
    }

    companion object {
        const val MODE_EXPORT = "export"
        const val MODE_IMPORT = "import"
        private const val ARG_MODE = "mode"
        private const val MIN_PASSPHRASE_LEN = 8

        fun newInstance(mode: String) = BackupDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
