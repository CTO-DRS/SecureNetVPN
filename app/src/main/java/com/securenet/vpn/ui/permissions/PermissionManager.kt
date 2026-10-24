package com.securenet.vpn.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.databinding.DialogPermissionRationaleBinding
import android.view.LayoutInflater
import java.util.ArrayDeque

/**
 * Sequential, best-practice permission flow:
 *
 *   rationale dialog (why) -> system dialog (what) -> next permission
 *
 * Never requests everything at once. Already-granted permissions are
 * skipped; permanently-denied permissions offer a deep link into app
 * settings instead of a dead-end dialog. "Not now" defers without nagging.
 */
class PermissionManager(private val activity: AppCompatActivity) {

    private data class QueuedRequest(
        val permission: String,
        val titleRes: Int,
        val descRes: Int,
        val iconRes: Int
    )

    private var queue: ArrayDeque<QueuedRequest> = ArrayDeque()
    private var completionCallback: (() -> Unit)? = null
    private var dialog: com.google.android.material.dialog.MaterialAlertDialogBuilder? = null
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult(granted)
    }

    private val settingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Resolved (or not) via settings; either way, move to the next item.
        advance()
    }

    fun hasEssentialPermissions(): Boolean =
        MainActivity.essentialPermissions().all { isGranted(it) }

    /**
     * Kicks off the sequential flow. [onAllResolved] fires when every
     * permission is either granted or explicitly skipped.
     */
    fun requestEssentialPermissions(onAllResolved: () -> Unit) {
        completionCallback = onAllResolved
        queue = ArrayDeque(buildQueue())
        advance()
    }

    private fun buildQueue(): List<QueuedRequest> = MainActivity.essentialPermissions().mapNotNull { perm ->
        if (isGranted(perm)) return@mapNotNull null
        when (perm) {
            Manifest.permission.POST_NOTIFICATIONS -> QueuedRequest(
                perm, R.string.perm_notif_title, R.string.perm_notif_desc, R.drawable.ic_notification_shield
            )
            Manifest.permission.CAMERA -> QueuedRequest(
                perm, R.string.perm_camera_title, R.string.perm_camera_desc, R.drawable.ic_scan
            )
            Manifest.permission.READ_CONTACTS -> QueuedRequest(
                perm, R.string.perm_contacts_title, R.string.perm_contacts_desc, R.drawable.ic_check
            )
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> QueuedRequest(
                perm, R.string.perm_location_title, R.string.perm_location_desc, R.drawable.ic_globe
            )
            else -> QueuedRequest(
                perm, R.string.perm_storage_title, R.string.perm_storage_desc, R.drawable.ic_lock
            )
        }
    }

    private fun advance() {
        val next = queue.peekFirst()
        if (next == null) {
            activeDialog?.dismiss()
            markEssentialGrantedIfPossible()
            val callback = completionCallback
            completionCallback = null
            callback?.invoke()
            return
        }
        showRationale(next)
    }

    private fun showRationale(request: QueuedRequest) {
        val binding = DialogPermissionRationaleBinding.inflate(
            LayoutInflater.from(activity)
        )
        binding.tvPermissionTitle.setText(request.titleRes)
        binding.tvPermissionDescription.setText(request.descRes)
        binding.imgPermissionIcon.setImageResource(request.iconRes)

        activeDialog?.dismiss()
        activeDialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setCancelable(false)
            .create()
            .apply {
                binding.btnRationaleContinue.setOnClickListener {
                    dismiss()
                    markRequested(request.permission)
                    launchSystemDialog(request)
                }
                binding.btnRationaleNotNow.setOnClickListener {
                    dismiss()
                    queue.pollFirst() // skip this one
                    advance()
                }
                show()
            }
    }

    private fun launchSystemDialog(request: QueuedRequest) {
        if (shouldOfferSettingsFallback(request.permission)) {
            offerSettingsFallback(request)
        } else {
            permissionLauncher.launch(request.permission)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        val request = queue.pollFirst()
        if (!granted && request != null && shouldOfferSettingsFallback(request.permission)) {
            offerSettingsFallback(request)
            return
        }
        advance()
    }

    /**
     * Android reports "permanently denied" when shouldShowRequestPermissionRationale
     * returns false after a prior ask. Route to app settings with context.
     */
    private fun shouldOfferSettingsFallback(permission: String): Boolean =
        !isGranted(permission) &&
            hasRequestedBefore(permission) &&
            !activity.shouldShowRequestPermissionRationale(permission)

    private fun offerSettingsFallback(request: QueuedRequest) {
        queue.pollFirst()
        activeDialog?.dismiss()
        MaterialAlertDialogBuilder(activity)
            .setTitle(request.titleRes)
            .setMessage(R.string.permission_denied_hint)
            .setPositiveButton(R.string.ok) { d, _ ->
                d.dismiss()
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                settingsLauncher.launch(intent)
            }
            .setNegativeButton(R.string.rationale_not_now) { d, _ ->
                d.dismiss()
                advance()
            }
            .show()
    }

    private fun markEssentialGrantedIfPossible() {
        if (hasEssentialPermissions()) {
            val app = activity.applicationContext as com.securenet.vpn.di.AppProvider
            app.provideContainer().securePrefs.essentialPermissionsGranted = true
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasRequestedBefore(permission: String): Boolean =
        activity.getPreferences(AppCompatActivity.MODE_PRIVATE)
            .getBoolean("asked_$permission", false)

    private fun markRequested(permission: String) {
        activity.getPreferences(AppCompatActivity.MODE_PRIVATE)
            .edit()
            .putBoolean("asked_$permission", true)
            .apply()
    }
}
