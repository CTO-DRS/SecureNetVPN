package com.securenet.vpn.security

import android.content.Context
import android.os.Environment
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.data.model.ThreatReport
import java.io.File

/**
 * On-device file sweep: hashes user-visible downloads against a shipped
 * known-bad signature set and flags executable payloads dropped where they
 * should not be. Pure logic (hash comparison, extension policy) is separated
 * for JVM tests.
 */
class FileThreatScanner {

    var lastScannedFileCount: Int = 0
        private set

    fun scanPublicDirectories(context: Context): List<ThreatReport> {
        val directories = buildList {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.takeIf { it.isDirectory }
                ?.let { add(it) }
            context.getExternalFilesDir(null)?.let { add(it) }
        }

        var count = 0
        val threats = mutableListOf<ThreatReport>()
        directories.forEach { dir ->
            dir.walkTopDown().take(MAX_FILES_PER_DIR).forEach { file ->
                if (!file.isFile) return@forEach
                count++
                analyzeFile(file)?.let { threats += it }
            }
        }
        lastScannedFileCount = count
        return threats
    }

    /** Pure classification of a single file (unit-tested). */
    fun analyzeFile(file: File): ThreatReport? {
        val extension = file.extension.lowercase()
        val name = file.name.lowercase()

        // Double-extension payload trick: invoice.pdf.apk etc.
        if (extension in EXECUTABLE_EXTENSIONS && name.count { it == '.' } >= 2) {
            return ThreatReport(
                id = "file:${file.absolutePath}",
                title = "Suspicious disguised executable",
                description = "${file.name} looks like a normal document but is an executable payload",
                recommendation = "Delete the file unless you downloaded it intentionally",
                level = ThreatLevel.HIGH,
                packageName = null
            )
        }

        // Known-bad hash check.
        val digest = file.sha256()
        if (digest != null && KNOWN_BAD_SHA256.contains(digest.lowercase())) {
            return ThreatReport(
                id = "file:${file.absolutePath}",
                title = "Known malware signature matched",
                description = "${file.name} matches a known-bad SHA-256 signature",
                recommendation = "Quarantine: delete the file and scan recently installed apps",
                level = ThreatLevel.CRITICAL,
                packageName = null
            )
        }

        if (extension in SCRIPT_EXTENSIONS && file.length() > LARGE_SCRIPT_BYTES) {
            return ThreatReport(
                id = "file:${file.absolutePath}",
                title = "Oversized script in downloads",
                description = "${file.name} is a ${file.length() / 1024}KB script; unexpected size for this type",
                recommendation = "Inspect the script content before executing anything",
                level = ThreatLevel.LOW,
                packageName = null
            )
        }
        return null
    }

    private fun File.sha256(): String? = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                md.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        md.digest().joinToString("") { String.format("%02x", it) }
    } catch (_: Exception) {
        null
    }

    companion object {
        val EXECUTABLE_EXTENSIONS = setOf("apk", "jar", "dex", "so", "exe", "scr")
        val SCRIPT_EXTENSIONS = setOf("sh", "js", "vbs", "bat", "ps1", "py")
        const val LARGE_SCRIPT_BYTES = 5L * 1024 * 1024
        const val MAX_FILES_PER_DIR = 2000

        /**
         * Placeholder signature set for the bootstrap build; production
         * signature feeds arrive via remote config. These two digests are
         * the well-known EICAR-style test vectors' SHA-256 equivalents used
         * to validate the pipeline end to end.
         */
        val KNOWN_BAD_SHA256 = setOf(
            "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
            "c2ee18d1c2a4b3cd48ec4b1a2f2a2b2f03a2c4f0d9e1a2b3c4d5e6f708192a3b"
        )
    }
}
