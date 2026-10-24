package com.securenet.vpn.ui.servers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.securenet.vpn.R
import com.securenet.vpn.databinding.DialogConfigImportBinding
import com.securenet.vpn.util.ConfigImporter

/**
 * v1.5 — WireGuard profile import: scan a QR code, open a .conf file, or
 * paste the text. The parsed profile is previewed (endpoint, address, DNS,
 * PSK presence) before it is saved; [ServersViewModel] owns the storage write.
 */
class ConfigImportDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogConfigImportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ServersViewModel by lazy {
        (requireActivity().application as com.securenet.vpn.di.AppProvider)
            .provideContainer()
            .let { ServersViewModel.Factory(it).create(ServersViewModel::class.java) }
    }

    /** Parsed profile awaiting the user's Save tap. */
    private var pendingEntry:
        com.securenet.vpn.data.local.CustomServerStore.Entry? = null

    private val scanLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val payload = result.contents ?: return@registerForActivityResult
            ingestConfig(payload, source = "qr")
        }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            readConfigFile(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogConfigImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnScanQr.setOnClickListener { launchScanner() }
        binding.btnPickFile.setOnClickListener { launchFilePicker() }
        binding.btnImportSave.setOnClickListener { savePending() }
        binding.btnImportDiscard.setOnClickListener {
            pendingEntry = null
            binding.previewGroup.visibility = View.GONE
        }
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.import_scan_prompt))
            setBeepEnabled(false)
            setOrientationLocked(true)
        }
        scanLauncher.launch(options)
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/*", "application/octet-stream")
            )
        }
        fileLauncher.launch(Intent.createChooser(intent, getString(R.string.import_pick_file)))
    }

    private fun readConfigFile(uri: Uri) {
        val text = runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_FILE_BYTES) return
                bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
        if (text == null) {
            showError(getString(R.string.import_error_read_file))
        } else {
            ingestConfig(text, source = "file")
        }
    }

    private fun ingestConfig(rawConfig: String, source: String) {
        val outcome = ConfigImporter.import(
            ConfigImporter.ImportSpec(rawConfig = rawConfig, displayName = null, source = source)
        )
        when (outcome) {
            is ConfigImporter.Outcome.Error -> showError(
                getString(R.string.import_error_generic) +
                    "\n" + errorDetail(outcome.reason, outcome.detail)
            )
            is ConfigImporter.Outcome.Success -> showPreview(outcome.entry)
        }
    }

    private fun showPreview(entry: com.securenet.vpn.data.local.CustomServerStore.Entry) {
        pendingEntry = entry
        binding.tvImportError.visibility = View.GONE
        binding.previewGroup.visibility = View.VISIBLE

        binding.tvPreviewDetails.text =
            ConfigImporter.summarize(entry).joinToString("\n") { (key, value) ->
                "${labelFor(key)}: $value"
            }
        binding.etProfileName.setText(entry.server.name)
    }

    private fun savePending() {
        val entry = pendingEntry ?: return
        val name = binding.etProfileName.text?.toString()?.trim()
        viewModel.saveImported(entry, name)
        Snackbar.make(binding.root, R.string.import_saved, Snackbar.LENGTH_SHORT).show()
        dismiss()
    }

    private fun showError(message: String) {
        pendingEntry = null
        binding.previewGroup.visibility = View.GONE
        binding.tvImportError.text = message
        binding.tvImportError.visibility = View.VISIBLE
    }

    private fun labelFor(key: String): String = when (key) {
        "name" -> getString(R.string.import_label_name)
        "endpoint" -> getString(R.string.import_label_endpoint)
        "address" -> getString(R.string.import_label_address)
        "dns" -> getString(R.string.import_label_dns)
        "psk" -> getString(R.string.import_label_psk)
        else -> key
    }

    private fun errorDetail(reason: ConfigImporter.Reason, detail: String?): String =
        when (reason) {
            ConfigImporter.Reason.EMPTY -> getString(R.string.import_error_empty)
            ConfigImporter.Reason.PARSE_FAILED ->
                getString(R.string.import_reason_parse) + (detail?.let { "\n$it" } ?: "")
            ConfigImporter.Reason.BAD_PRIVATE_KEY -> getString(R.string.import_reason_private_key)
            ConfigImporter.Reason.BAD_PEER_KEY -> getString(R.string.import_reason_peer_key)
            ConfigImporter.Reason.BAD_PRESHARED_KEY -> getString(R.string.import_reason_psk)
            ConfigImporter.Reason.MISSING_ENDPOINT -> getString(R.string.import_reason_no_endpoint)
            ConfigImporter.Reason.BAD_ENDPOINT ->
                getString(R.string.import_reason_bad_endpoint) + (detail?.let { " ($it)" } ?: "")
        }

    private companion object {
        const val MAX_FILE_BYTES = 128 * 1024
    }
}
