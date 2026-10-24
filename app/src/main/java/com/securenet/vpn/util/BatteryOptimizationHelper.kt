package com.securenet.vpn.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization management: detection of the OEM throttle state and
 * the settings deep-link that keeps the tunnel alive under aggressive
 * battery savers. Handles the OEM quirk where the direct-optimize intent is
 * disallowed by falling back to the generic battery settings screen.
 */
object BatteryOptimizationHelper {

    fun isIgnoringOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Builds the intent; caller should startActivity and catch failures. */
    fun requestIgnoreOptimizationsIntent(context: Context): Intent {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        if (direct.resolveActivity(context.packageManager) != null) {
            return direct
        }
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    fun openBatterySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Some OEM builds ship neither screen; nothing else to do.
        }
    }
}
