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
 * v1.8.3 — VAULT modes for the in-app backup vault:
 *  - VAULT_CREATE behaves like export (passphrase + confirmation) but the
 *    slot is written into the app's private vault instead of a picker;
 *  - VAULT_RESTORE behaves like import for one existing slot (its display
 *    name is shown in the message via [argSlotName]).
 *
 * The passphrase never goes through an Intent extra — it is delivered to
 * the host through [Host] while the fragment is attached. The host is the
 * parent fragment first (v1.8.3: both Settings and the vault dialog host
 * this prompt through their childFragmentManager, which also fixes the
 * pre-v1.8.3 latent dead-button bug where the activity-level lookup never
 * found the fragment host), falling back to the activity.
 */
class BackupDialogFragment : DialogFragment() {

    interface Host {
        fun onBackupPassphraseConfirmed(mode: String, passphrase: CharArray)
    }

    private var _binding: DialogBackupPassphraseBinding? = null
    private val binding get() = _binding!!

    private val mode: String
        get() = arguments?.getString(ARG_MODE) ?: MODE_EXPORT

    private val slotName: String?
        get() = arguments?.getString(ARG_SLOT_NAME)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBackupPassphraseBinding.inflate(layoutInflater)

        val isExport = mode == MODE_EXPORT || mode == MODE_VAULT_CREATE
        when (mode) {
            MODE_EXPORT -> {
                binding.tvBackupMessage.setText(R.string.backup_export_message)
                binding.tilConfirm.visibility = View.VISIBLE
            }
            MODE_VAULT_CREATE -> {
                binding.tvBackupMessage.setText(R.string.backup_vault_create_message)
                binding.tilConfirm.visibility = View.VISIBLE
            }
            MODE_VAULT_RESTORE -> {
                binding.tvBackupMessage.text = getString(
                    R.string.backup_vault_restore_message,
                    slotName ?: getString(R.string.backup_vault_default_slot)
                )
                binding.tilConfirm.visibility = View.GONE
            }
            else -> {
                binding.tvBackupMessage.setText(R.string.backup_import_message)
                binding.tilConfirm.visibility = View.GONE
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(titleRes())
            .setView(binding.root)
            .setPositiveButton(R.string.backup_action_continue, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (validate(isExport)) {
                            val pass = binding.etBackupPassphrase.text?.toString() ?: ""
                            val host = (parentFragment as? Host) ?: (activity as? Host)
                            host?.onBackupPassphraseConfirmed(mode, pass.toCharArray())
                            dismiss()
                        }
                    }
                }
            }
    }

    private fun titleRes(): Int = when (mode) {
        MODE_EXPORT -> R.string.backup_export_title
        MODE_IMPORT -> R.string.backup_import_title
        MODE_VAULT_CREATE -> R.string.backup_vault_create_title
        else -> R.string.backup_vault_restore_title
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
        const val MODE_VAULT_CREATE = "vault_create"
        const val MODE_VAULT_RESTORE = "vault_restore"
        private const val ARG_MODE = "mode"
        private const val ARG_SLOT_NAME = "slot_name"
        private const val MIN_PASSPHRASE_LEN = 8

        fun newInstance(mode: String) = BackupDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode) }
        }

        /** v1.8.3 — restore prompt for one vault slot, name shown in the message. */
        fun newVaultRestore(slotName: String) = BackupDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MODE, MODE_VAULT_RESTORE)
                putString(ARG_SLOT_NAME, slotName)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
