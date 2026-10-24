package com.securenet.vpn.data.local

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.securenet.vpn.data.model.ConnectionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only, at-rest encrypted session log (JSONL inside an
 * [EncryptedFile]). Workers read the file, aggregate it and can wipe it.
 */
class ConnectionLogger(context: Context) : VpnEventLogger {

    private val appContext: Context = context.applicationContext
    private val logDir: File = File(context.filesDir, "logs").apply { mkdirs() }
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    private val timestampFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    private val lock = Any()

    override fun appendEvent(state: ConnectionState, serverId: String?, detail: String?) {
        appendRaw(buildEventJson(state.name.lowercase(Locale.US), serverId, detail))
    }

    /** Free-form event (scanner findings, usage markers, etc.). */
    override fun appendCustom(event: String, detail: String?) {
        appendRaw(buildEventJson(event, null, detail))
    }

    private fun buildEventJson(event: String, serverId: String?, detail: String?): String =
        buildString {
            append("{\"ts\":\"").append(timestampFormat.format(Date())).append('"')
            append(",\"event\":\"").append(event).append('"')
            if (serverId != null) append(",\"server\":\"").append(serverId).append('"')
            if (detail != null) {
                append(",\"detail\":\"")
                append(detail.replace("\\", "\\\\").replace("\"", "\\\""))
                append('"')
            }
            append("}\n")
        }

    override fun appendUsage(dayEpoch: Long, rxBytes: Long, txBytes: Long, connectedSeconds: Long) {
        val entry = "{\"ts\":\"" + timestampFormat.format(Date()) +
            "\",\"event\":\"usage\",\"day\":" + dayEpoch +
            ",\"rx\":" + rxBytes +
            ",\"tx\":" + txBytes +
            ",\"connected_s\":" + connectedSeconds + "}\n"
        appendRaw(entry)
    }

    private fun appendRaw(entry: String) {
        synchronized(lock) {
            try {
                val file = currentFile()
                val encrypted = EncryptedFile.Builder(
                    appContext, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encrypted.openFileOutput().use { out ->
                    out.write(entry.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // Logging must never crash the VPN path.
            }
        }
    }

    /** Best-effort plaintext read for workers; returns empty list on failure. */
    fun readAllLines(): List<String> {
        synchronized(lock) {
            return try {
                val file = currentFile()
                val encrypted = EncryptedFile.Builder(
                    appContext, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encrypted.openFileInput().bufferedReader().readLines()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            try {
                currentFile().delete()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    /**
     * v1.6 — Rewrites the whole encrypted log (backup restore path).
     * Writes to a temp EncryptedFile first and swaps it in only on success
     * so a failure mid-restore never destroys the existing history.
     *
     * @return true when the replacement log is in place.
     */
    fun replaceAllLines(lines: List<String>): Boolean {
        synchronized(lock) {
            val tmp = File(logDir, "session_log_restore.enc")
            return try {
                if (tmp.exists()) tmp.delete()
                val encrypted = EncryptedFile.Builder(
                    appContext, tmp, masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encrypted.openFileOutput().use { out ->
                    lines.forEach { line ->
                        out.write((line.trimEnd('\n', '\r') + "\n").toByteArray(Charsets.UTF_8))
                    }
                }
                currentFile().delete()
                if (!tmp.renameTo(currentFile())) {
                    // Rename can fail across odd filesystems; fall back to copy.
                    tmp.copyTo(currentFile(), overwrite = true)
                    tmp.delete()
                }
                true
            } catch (_: Exception) {
                try {
                    tmp.delete()
                } catch (_: Exception) {
                    // ignore
                }
                false
            }
        }
    }

    fun logFileSizeBytes(): Long = currentFile().takeIf { it.exists() }?.length() ?: 0L

    private fun currentFile(): File = File(logDir, "session_log.enc")
}
