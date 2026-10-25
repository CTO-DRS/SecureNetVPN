package com.securenet.vpn.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.securenet.vpn.R
import com.securenet.vpn.backup.BackupVault
import com.securenet.vpn.backup.LogBackupEngine
import com.securenet.vpn.di.AppProvider
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.GuideSignalsMerge
import com.securenet.vpn.guide.GuideStoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.8.3 — the in-app backup vault screen: multiple encrypted restore
 * points kept in the app's private storage ([BackupVault], app files dir).
 * From here the user can — without ever leaving SecureNet:
 *  - create a new encrypted slot (passphrase + confirmation, same SNBK
 *    container as the SAF export, guide signals included);
 *  - restore any slot: events merge non-destructively into the live log
 *    and guide signals merge the same way the file import does;
 *  - delete a slot after an explicit confirmation.
 *
 * The vault dialog itself hosts the [BackupDialogFragment] passphrase
 * prompts (childFragmentManager), so the passphrase comes straight back
 * here through [BackupDialogFragment.Host] and never rides an Intent.
 * All vault I/O runs on Dispatchers.IO; the list re-renders after every
 * mutation so the slots stay truthful while the dialog is open.
 */
class BackupVaultDialogFragment : DialogFragment(), BackupDialogFragment.Host {

    private var slotsHost: LinearLayout? = null
    private var emptyView: TextView? = null
    private var vault: BackupVault? = null

    /** Slot awaiting its passphrase for the restore flow. */
    private var pendingRestoreId: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }

        body.addView(MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonStyle).apply {
            text = getString(R.string.vault_new_backup)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            )
            setOnClickListener { startCreate() }
        })

        emptyView = TextView(context).apply {
            text = getString(R.string.vault_empty)
            gravity = Gravity.CENTER
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 12.5f
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(8), dp(18), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        body.addView(emptyView)

        slotsHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        body.addView(slotsHost)

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_vault_title)
            .setView(ScrollView(context).apply {
                addView(body)
            })
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .also { refresh() }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private fun refresh() {
        val host = slotsHost ?: return
        host.removeAllViews()
        val slots = vault().list()
        emptyView?.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        slots.forEach { slot -> host.addView(slotRow(slot)) }
    }

    private fun slotRow(slot: BackupVault.SlotMeta): View {
        val context = requireContext()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(2))
        }
        column.addView(TextView(context).apply {
            text = slot.name
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        column.addView(TextView(context).apply {
            text = getString(
                R.string.vault_slot_meta,
                SLOT_DATE_FORMAT.format(Date(slot.createdAtMillis)),
                slot.eventCount, slot.signalCount,
                formatSize(slot.sizeBytes)
            )
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        })
        column.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            addView(outlinedButton(R.string.vault_restore) { startRestore(slot) }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, dp(40), 1f
                ).apply { marginEnd = dp(6) }
            })
            addView(outlinedButton(R.string.vault_delete) { confirmDelete(slot) }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, dp(40), 1f
                )
                setTextColor(resolveColor(com.google.android.material.R.attr.colorError))
            })
        })
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            radius = dp(10).toFloat()
            alpha = 0.92f
            setContentPadding(dp(10), dp(2), dp(10), dp(4))
            addView(column)
        }
    }

    private fun outlinedButton(labelRes: Int, onClick: () -> Unit) =
        MaterialButton(requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(labelRes)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener { onClick() }
        }

    // ------------------------------------------------------------------
    // Create / restore (passphrase flows) / delete (confirm flow)
    // ------------------------------------------------------------------

    private fun startCreate() {
        BackupDialogFragment.newInstance(BackupDialogFragment.MODE_VAULT_CREATE)
            .show(childFragmentManager, "vault_create")
    }

    private fun startRestore(slot: BackupVault.SlotMeta) {
        pendingRestoreId = slot.id
        BackupDialogFragment.newVaultRestore(slot.name)
            .show(childFragmentManager, "vault_restore")
    }

    override fun onBackupPassphraseConfirmed(mode: String, passphrase: CharArray) {
        when (mode) {
            BackupDialogFragment.MODE_VAULT_CREATE -> createSlot(passphrase)
            BackupDialogFragment.MODE_VAULT_RESTORE -> {
                val id = pendingRestoreId
                pendingRestoreId = null
                if (id != null) restoreSlot(id, passphrase)
            }
        }
    }

    private fun createSlot(passphrase: CharArray) {
        val ctx = requireContext().applicationContext
        val logger = container().connectionLogger
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val lines = logger.readAllLines()
                    val signals = GuideSignalsData.snapshot(
                        GuideStoreProvider.get(ctx)
                    )
                    val meta = vault().create(
                        vaultLabel(), lines, passphrase, signals
                    )
                    passphrase.fill('\u0000')
                    meta
                }
            }
            outcome.onSuccess { meta ->
                refresh()
                val message = ctx.getString(R.string.vault_created, meta.eventCount) +
                    (meta.signalCount.takeIf { it > 0 }?.let {
                        ctx.getString(R.string.backup_export_signals, it)
                    } ?: "")
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            }.onFailure {
                passphrase.fill('\u0000')
                Toast.makeText(ctx, R.string.vault_create_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun restoreSlot(id: String, passphrase: CharArray) {
        val ctx = requireContext().applicationContext
        val logger = container().connectionLogger
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val meta = vault().list().firstOrNull { it.id == id }
                        ?: error("slot vanished")
                    val payload = vault().read(id, passphrase)
                    val merge = LogBackupEngine.merge(
                        logger.readAllLines(), payload.eventLines
                    )
                    if (!logger.replaceAllLines(merge.mergedLines)) {
                        error("log rewrite failed")
                    }
                    logger.appendCustom(
                        "vault_backup_restored",
                        "added=${merge.added}; duplicates=${merge.duplicates}"
                    )
                    val signalsTouched = payload.signals?.let { imported ->
                        val store = GuideStoreProvider.get(ctx)
                        val merged = GuideSignalsMerge.merge(
                            GuideSignalsData.snapshot(store), imported
                        )
                        GuideSignalsMerge.applyTo(store, merged.applied)
                        merged.touchedIds.size
                    }
                    passphrase.fill('\u0000')
                    Triple(meta, merge, signalsTouched)
                }
            }
            outcome.onSuccess { (meta, merge, signalsTouched) ->
                refresh()
                val message = ctx.getString(
                    R.string.vault_restored, meta.name, merge.added, merge.duplicates
                ) + signalsTouched?.takeIf { it > 0 }?.let {
                    ctx.getString(R.string.backup_import_signals, it)
                }.orEmpty()
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                passphrase.fill('\u0000')
                val res = if (error is LogBackupEngine.BackupException) {
                    R.string.backup_import_wrong_pass
                } else {
                    R.string.vault_restore_failed
                }
                Toast.makeText(ctx, res, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(slot: BackupVault.SlotMeta) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vault_delete_title)
            .setMessage(getString(R.string.vault_delete_message, slot.name))
            .setPositiveButton(R.string.vault_delete) { _, _ ->
                if (vault().delete(slot.id)) {
                    refresh()
                    Toast.makeText(
                        requireContext(), R.string.vault_deleted, Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun vault(): BackupVault {
        val existing = vault
        if (existing != null) return existing
        val created = BackupVault(
            File(requireContext().applicationContext.filesDir, BackupVault.DIRNAME)
        )
        vault = created
        return created
    }

    /** Auto label for new slots — the creation moment, locale-formatted. */
    private fun vaultLabel(): String =
        SLOT_DATE_FORMAT.format(Date())

    private fun container() =
        (requireActivity().application as AppProvider).provideContainer()

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        slotsHost = null
        emptyView = null
        vault = null
        pendingRestoreId = null
    }

    companion object {
        private val SLOT_DATE_FORMAT =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}
