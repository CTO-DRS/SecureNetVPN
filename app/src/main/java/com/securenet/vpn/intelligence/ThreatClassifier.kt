package com.securenet.vpn.intelligence

import com.securenet.vpn.data.model.AppProfile
import com.securenet.vpn.data.model.InstallerSource
import com.securenet.vpn.data.model.ThreatLevel
import kotlin.math.exp

/**
 * On-device logistic-regression threat classifier.
 *
 * Runs a small, fully offline model over a feature vector extracted from an
 * [AppProfile]. Weights are embedded at build time (no network, no model
 * download, no telemetry) and chosen to mirror the heuristics that mobile
 * security analysts apply when triaging permission sets:
 *
 *   - invasive permission groups (SMS, contacts, location, mic, camera)
 *   - accessibility + device-admin abuse (the classic spyware pattern)
 *   - sideloaded origin (no store review)
 *   - stale targetSdk (escapes modern privacy enforcement)
 *   - permission-to-feature size ratio (tiny app, huge permissions)
 *
 * Output: P(malicious | features) in [0,1] mapped onto the app's ThreatLevel
 * ladder so existing UI keeps working unchanged.
 */
class ThreatClassifier {

    data class Features(
        val smsCount: Int,
        val contactCount: Int,
        val locationCount: Int,
        val microphoneCount: Int,
        val cameraCount: Int,
        val storageCount: Int,
        val calendarCount: Int,
        val callLogCount: Int,
        val hasAccessibility: Boolean,
        val usesDeviceAdmin: Boolean,
        val isSideloaded: Boolean,
        val staleTargetSdk: Boolean,
        val totalPermissions: Int
    ) {
        /** Feature vector in fixed order; index-matched to [WEIGHTS]. */
        fun toVector(): DoubleArray = doubleArrayOf(
            smsCount.toDouble(),
            contactCount.toDouble(),
            locationCount.toDouble(),
            microphoneCount.toDouble(),
            cameraCount.toDouble(),
            storageCount.toDouble(),
            calendarCount.toDouble(),
            callLogCount.toDouble(),
            if (hasAccessibility) 1.0 else 0.0,
            if (usesDeviceAdmin) 1.0 else 0.0,
            if (isSideloaded) 1.0 else 0.0,
            if (staleTargetSdk) 1.0 else 0.0,
            totalPermissions.toDouble()
        )
    }

    data class Prediction(
        val probability: Double,
        val level: ThreatLevel,
        val topFactors: List<String>
    )

    fun extract(profile: AppProfile): Features {
        val p = profile.requestedPermissions
        fun countOf(vararg keys: String): Int =
            p.count { perm -> keys.any { perm.contains(it, ignoreCase = true) } }

        return Features(
            smsCount = countOf("android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
                "android.permission.READ_SMS"),
            contactCount = countOf("android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS"),
            locationCount = countOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
                "ACCESS_BACKGROUND_LOCATION"),
            microphoneCount = countOf("android.permission.RECORD_AUDIO"),
            cameraCount = countOf("android.permission.CAMERA"),
            storageCount = countOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
                "READ_MEDIA_"),
            calendarCount = countOf("READ_CALENDAR", "WRITE_CALENDAR"),
            callLogCount = countOf("READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS"),
            hasAccessibility = profile.hasAccessibilityService,
            usesDeviceAdmin = profile.usesDeviceAdmin,
            isSideloaded = profile.installerSource == InstallerSource.SIDELOAD,
            staleTargetSdk = profile.targetSdk in 1..29,
            totalPermissions = p.size
        )
    }

    /** P(malicious | features) via sigmoid(w·x + b), then clamped ladder mapping. */
    fun predict(profile: AppProfile): Prediction {
        val features = extract(profile)
        val x = features.toVector()
        require(x.size == WEIGHTS.size) { "feature/weight size mismatch" }
        var z = BIAS
        val contributions = ArrayList<Pair<String, Double>>(x.size)
        for (i in x.indices) {
            val c = WEIGHTS[i] * x[i]
            z += c
            if (c > 0) contributions += FEATURE_NAMES[i] to c
        }
        val probability = sigmoid(z)
        val level = when {
            probability >= 0.85 -> ThreatLevel.CRITICAL
            probability >= 0.60 -> ThreatLevel.HIGH
            probability >= 0.35 -> ThreatLevel.MEDIUM
            probability >= 0.15 -> ThreatLevel.LOW
            else -> ThreatLevel.SAFE
        }
        val top = contributions.sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        return Prediction(probability, level, top)
    }

    private fun sigmoid(z: Double): Double =
        1.0 / (1.0 + exp(-z.coerceIn(-30.0, 30.0)))

    companion object {
        // Order: sms, contacts, location, mic, camera, storage, calendar,
        //        callLog, accessibility, deviceAdmin, sideload, staleSdk, totalPerms
        internal val WEIGHTS = doubleArrayOf(
            1.30, 0.70, 0.55, 0.80, 0.45, 0.25, 0.60, 0.95,
            1.60, 1.70, 0.90, 0.75, 0.05
        )
        internal const val BIAS = -2.20

        internal val FEATURE_NAMES = listOf(
            "SMS access", "Contacts access", "Location access", "Microphone",
            "Camera", "Storage access", "Calendar access", "Call logs",
            "Accessibility service", "Device admin", "Sideloaded APK",
            "Outdated target SDK", "Permission-heavy app"
        )
    }
}
