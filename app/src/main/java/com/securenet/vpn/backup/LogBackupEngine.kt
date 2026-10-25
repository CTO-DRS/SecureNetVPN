package com.securenet.vpn.backup

import com.securenet.vpn.guide.GuideSignalsCodec
import com.securenet.vpn.guide.GuideSignalsData
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * v1.6 — Passphrase-protected backup of the encrypted session log.
 *
 * Container layout (binary):
 *
 * ```
 * magic "SNBK" (4 bytes) | format version (1 byte)
 * | PBKDF2 salt (16 bytes) | AES-GCM nonce (12 bytes)
 * | ciphertext length (4 bytes, big-endian) | AES-256-GCM ciphertext
 * ```
 *
 * The decrypted plaintext is UTF-8 text: the first line is a JSON header
 * `{"format":"securenet-log-backup","version":1,"created_at":…,"count":…}`
 * followed by the raw JSONL event lines exactly as ConnectionLogger
 * stores them, so a restore round-trips the log byte-for-byte.
 *
 * v1.8.2 — the header may additionally carry a `"signals":{…}` member: a
 * serialised snapshot of the guide's user signals (star ratings, favorite
 * bookmarks, personal notes — [GuideSignalsData]). Older app versions
 * ignore unknown header members, so signal-bearing backups still restore
 * their events there; backups created before v1.8.2 simply decode with a
 * null [Payload.signals]. The signals JSON escapes control characters, so
 * even a multi-line personal note keeps the header on one line.
 *
 * Crypto: PBKDF2WithHmacSHA256 (120 000 iterations, 256-bit key) from the
 * user passphrase + AES-256-GCM (128-bit tag). Both primitives ship with
 * the platform — no third-party dependency, fully JVM-unit-testable.
 */
object LogBackupEngine {

    const val FILE_EXTENSION = ".snback"
    const val MIME_TYPE = "application/octet-stream"
    const val HEADER_PREFIX = "{\"format\":\"securenet-log-backup\""

    private val MAGIC = byteArrayOf(0x53, 0x4E, 0x42, 0x4B) // "SNBK"
    private const val FORMAT_VERSION = 1
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private const val GCM_TAG_BITS = 128

    /** Wrong passphrase / truncated file / unknown magic all land here. */
    class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Payload(
        val createdAtMillis: Long,
        val eventLines: List<String>,
        /** Guide signals snapshot when the backup carries one (v1.8.2+). */
        val signals: GuideSignalsData? = null
    )

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    fun encode(
        lines: List<String>,
        passphrase: CharArray,
        nowMillis: Long = System.currentTimeMillis(),
        signals: GuideSignalsData? = null
    ): ByteArray {
        val clean = lines.map { it.trimEnd('\n', '\r') }.filter { it.isNotBlank() }
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val header = buildString {
            append(HEADER_PREFIX)
            append(",\"version\":").append(FORMAT_VERSION)
            append(",\"created_at\":").append(nowMillis)
            append(",\"count\":").append(clean.size)
            // v1.8.2 — guide ratings/favorites/notes ride the same container.
            if (signals != null && !signals.isEmpty()) {
                append(",\"signals\":").append(GuideSignalsCodec.encode(signals))
            }
            append('}')
        }
        val plaintext = (listOf(header) + clean)
            .joinToString(separator = "\n", postfix = "\n")
            .toByteArray(Charsets.UTF_8)

        val ciphertext = encrypt(plaintext, passphrase, salt, nonce)

        val out = ByteArray(MAGIC.size + 1 + SALT_LEN + NONCE_LEN + 4 + ciphertext.size)
        var p = 0
        MAGIC.copyInto(out, p); p += MAGIC.size
        out[p] = FORMAT_VERSION.toByte(); p += 1
        salt.copyInto(out, p); p += SALT_LEN
        nonce.copyInto(out, p); p += NONCE_LEN
        writeInt(out, p, ciphertext.size); p += 4
        ciphertext.copyInto(out, p)
        return out
    }

    // ------------------------------------------------------------------
    // Decode
    // ------------------------------------------------------------------

    fun decode(bytes: ByteArray, passphrase: CharArray): Payload {
        val minSize = MAGIC.size + 1 + SALT_LEN + NONCE_LEN + 4
        if (bytes.size < minSize) throw BackupException("file truncated")
        var p = 0
        for (i in MAGIC.indices) {
            if (bytes[p + i] != MAGIC[i]) throw BackupException("not a SecureNet backup")
        }
        p += MAGIC.size
        val version = bytes[p].toInt() and 0xFF; p += 1
        if (version != FORMAT_VERSION) throw BackupException("unsupported version $version")
        val salt = bytes.copyOfRange(p, p + SALT_LEN); p += SALT_LEN
        val nonce = bytes.copyOfRange(p, p + NONCE_LEN); p += NONCE_LEN
        val ctLen = readInt(bytes, p); p += 4
        if (ctLen < GCM_TAG_BITS / 8 || p.toLong() + ctLen > bytes.size) {
            throw BackupException("corrupt ciphertext length")
        }
        val ciphertext = bytes.copyOfRange(p, p + ctLen)

        val plaintext = try {
            decrypt(ciphertext, passphrase, salt, nonce)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("wrong passphrase or damaged file", e)
        }

        val text = String(plaintext, Charsets.UTF_8)
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        val header = lines.firstOrNull() ?: throw BackupException("empty payload")
        if (!header.startsWith(HEADER_PREFIX)) throw BackupException("payload header mismatch")
        return Payload(
            createdAtMillis = extractLong(header, "created_at"),
            eventLines = lines.drop(1),
            signals = extractSignalsObject(header)?.let { GuideSignalsCodec.decode(it) }
        )
    }

    /**
     * Extracts the balanced `"signals":{…}` object text from the header
     * line. The scanner is string-aware (a note may contain braces), so the
     * object boundary is found even inside escaped string content.
     */
    private fun extractSignalsObject(header: String): String? {
        val key = "\"signals\":"
        val start = header.indexOf(key)
        if (start < 0) return null
        var i = start + key.length
        while (i < header.length && header[i].isWhitespace()) i++
        if (i >= header.length || header[i] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        val sb = StringBuilder()
        while (i < header.length) {
            val c = header[i++]
            sb.append(c)
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return sb.toString() }
                }
            }
        }
        return null // unbalanced
    }

    // ------------------------------------------------------------------
    // Merge (non-destructive restore)
    // ------------------------------------------------------------------

    data class MergeResult(
        val mergedLines: List<String>,
        val added: Int,
        val duplicates: Int
    )

    /**
     * Merges the imported JSONL events into the live log: exact duplicate
     * lines are dropped, the result is ordered by event timestamp so the
     * history timeline stays chronological. Never drops existing events.
     */
    fun merge(existing: List<String>, imported: List<String>): MergeResult {
        val seen = LinkedHashSet<String>()
        existing.forEach { line ->
            line.trim().takeIf { it.isNotEmpty() }?.let { seen.add(it) }
        }
        var added = 0
        var duplicates = 0
        for (raw in imported) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (seen.add(line)) added++ else duplicates++
        }
        return MergeResult(
            mergedLines = seen.sortedBy { eventTimestamp(it) },
            added = added,
            duplicates = duplicates
        )
    }

    /** Epoch millis of the event encoded in a JSONL line (MAX fallback). */
    fun eventTimestamp(line: String): Long {
        val match = TS_REGEX.find(line) ?: return Long.MAX_VALUE
        return try {
            synchronized(TIMESTAMP_FORMAT) {
                TIMESTAMP_FORMAT.parse(match.groupValues[1])?.time ?: Long.MAX_VALUE
            }
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private val TS_REGEX = Regex("\"ts\":\"([^\"]+)\"")

    // ------------------------------------------------------------------
    // Crypto helpers
    // ------------------------------------------------------------------

    private fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        nonce: ByteArray
    ): ByteArray = runCipher(Cipher.ENCRYPT_MODE, passphrase, salt, nonce)
        .doFinal(plaintext)

    private fun decrypt(
        ciphertext: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        nonce: ByteArray
    ): ByteArray = runCipher(Cipher.DECRYPT_MODE, passphrase, salt, nonce)
        .doFinal(ciphertext)

    private fun runCipher(
        mode: Int,
        passphrase: CharArray,
        salt: ByteArray,
        nonce: ByteArray
    ): Cipher {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
    }

    private val random = SecureRandom()

    private fun writeInt(dest: ByteArray, offset: Int, value: Int) {
        dest[offset] = ((value ushr 24) and 0xFF).toByte()
        dest[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        dest[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        dest[offset + 3] = (value and 0xFF).toByte()
    }

    private fun readInt(src: ByteArray, offset: Int): Int =
        ((src[offset].toInt() and 0xFF) shl 24) or
            ((src[offset + 1].toInt() and 0xFF) shl 16) or
            ((src[offset + 2].toInt() and 0xFF) shl 8) or
            (src[offset + 3].toInt() and 0xFF)

    private fun extractLong(json: String, key: String): Long =
        Regex("\"$key\":(-?\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /** Same instant format ConnectionLogger writes with. */
    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
}
