package com.securenet.vpn.backup

import com.securenet.vpn.guide.GuideSignalsData
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.8.3 — multiple encrypted backups kept INSIDE the app ("دعم نسخ
 * احتياطية متعددة داخل التطبيق"). The existing .snback flow exports exactly
 * one user-chosen file through the system picker; the vault instead keeps
 * several named restore points in the app's private storage, so the user
 * can snapshot the log + guide signals before risky changes and roll back
 * to any of them without leaving SecureNet.
 *
 * Layout: one directory holding, per slot —
 *  - `<id>.snback`  the standard [LogBackupEngine] container (payload fully
 *    encrypted with the user's passphrase, identical wire format to the
 *    SAF export, so a slot can be re-exported later if ever needed);
 *  - `<id>.meta`    a tiny one-line JSON sidecar with the non-sensitive
 *    display metadata (user label, creation time, event/signal counts) so
 *    the list renders without decrypting anything.
 *
 * Pure java.io + the engine — no Android imports, fully unit-testable on
 * the JVM. Housekeeping: the vault prunes itself to [MAX_SLOTS] newest
 * slots on every create, and [list] silently skips corrupt sidecars or
 * slots whose payload vanished, so a damaged file never breaks the list.
 */
class BackupVault(private val dir: File) {

    /** Display metadata of one stored backup slot. */
    data class SlotMeta(
        /** Unique slot id (also the file stem, e.g. `20261026_141533-a3f2`). */
        val id: String,
        /** User-facing label (auto-generated timestamp label for now). */
        val name: String,
        val createdAtMillis: Long,
        /** Event lines encrypted inside the payload. */
        val eventCount: Int,
        /** Guide signal values (ratings + favorites + notes) included. */
        val signalCount: Int,
        /** Payload file size in bytes, read live from disk. */
        val sizeBytes: Long
    )

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    /**
     * Encrypts [lines] (+ optional guide [signals]) into a new slot and
     * prunes the vault back to [MAX_SLOTS] entries. Returns the created
     * slot's metadata.
     */
    fun create(
        name: String,
        lines: List<String>,
        passphrase: CharArray,
        signals: GuideSignalsData? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): SlotMeta {
        dir.mkdirs()
        val id = newId(nowMillis)
        val bytes = LogBackupEngine.encode(lines, passphrase, nowMillis, signals)
        val payload = File(dir, id + PAYLOAD_EXT)
        val meta = File(dir, id + META_EXT)
        payload.writeBytes(bytes)
        try {
            meta.writeText(
                encodeMeta(
                    id = id,
                    name = sanitizeName(name),
                    createdAtMillis = nowMillis,
                    eventCount = lines.count { it.isNotBlank() },
                    signalCount = signals?.signalCount() ?: 0
                ),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            payload.delete() // never leave a payload without its sidecar
            throw e
        }
        prune()
        return list().firstOrNull { it.id == id }
            ?: SlotMeta(
                id = id, name = sanitizeName(name), createdAtMillis = nowMillis,
                eventCount = lines.count { it.isNotBlank() },
                signalCount = signals?.signalCount() ?: 0,
                sizeBytes = payload.length()
            )
    }

    // ------------------------------------------------------------------
    // List / read / delete
    // ------------------------------------------------------------------

    /** All healthy slots, newest first. Corrupt entries are skipped. */
    fun list(): List<SlotMeta> = dir.listFiles()
        ?.filter { it.name.endsWith(META_EXT) }
        ?.mapNotNull { metaFile ->
            val meta = decodeMeta(metaFile.readText(Charsets.UTF_8)) ?: return@mapNotNull null
            val payload = File(dir, meta.id + PAYLOAD_EXT)
            if (!payload.isFile) return@mapNotNull null // orphaned sidecar
            meta.copy(sizeBytes = payload.length())
        }
        ?.sortedWith(compareByDescending<SlotMeta> { it.createdAtMillis }.thenByDescending { it.id })
        ?: emptyList()

    fun hasSlot(id: String): Boolean = isSafeId(id) &&
        File(dir, id + PAYLOAD_EXT).isFile &&
        File(dir, id + META_EXT).isFile

    /** Decrypts one slot through [LogBackupEngine] (wrong passphrase throws). */
    fun read(id: String, passphrase: CharArray): LogBackupEngine.Payload {
        require(isSafeId(id)) { "illegal slot id" }
        val payload = File(dir, id + PAYLOAD_EXT)
        if (!payload.isFile) throw LogBackupEngine.BackupException("slot missing")
        return LogBackupEngine.decode(payload.readBytes(), passphrase)
    }

    /** Removes the slot (payload + sidecar). True when the payload existed. */
    fun delete(id: String): Boolean {
        if (!isSafeId(id)) return false
        val payload = File(dir, id + PAYLOAD_EXT)
        val meta = File(dir, id + META_EXT)
        val existed = payload.isFile
        payload.delete()
        meta.delete()
        return existed
    }

    // ------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------

    /** Keeps only the [MAX_SLOTS] newest slots. */
    private fun prune() {
        list().drop(MAX_SLOTS).forEach { delete(it.id) }
    }

    private fun newId(nowMillis: Long): String {
        // per-call instance: SimpleDateFormat is not thread-safe
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        val suffix = ByteArray(2).also { random.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        return "$stamp-$suffix"
    }

    // ------------------------------------------------------------------
    // Name + id hygiene
    // ------------------------------------------------------------------

    /**
     * Display labels are free text (Arabic, quotes, emoji all welcome);
     * control characters and newlines are collapsed to spaces and the
     * label is capped so the sidecar line stays single-line.
     */
    fun sanitizeName(raw: String): String = buildString {
        raw.forEach { c ->
            append(if (c < ' ' || c == '\u007F') ' ' else c)
        }
    }.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }
        ?.take(MAX_NAME_LEN) ?: DEFAULT_NAME

    /** Slot ids are generated here and must never traverse paths. */
    private fun isSafeId(id: String): Boolean =
        id.isNotEmpty() && !id.contains('/') && !id.contains('\\') &&
            id.none { it < ' ' } && id != "." && id != ".."

    // ------------------------------------------------------------------
    // Meta sidecar codec (hand-rolled JSON, single line, escaped strings)
    // ------------------------------------------------------------------

    private fun encodeMeta(
        id: String,
        name: String,
        createdAtMillis: Long,
        eventCount: Int,
        signalCount: Int
    ): String = buildString {
        append("{\"v\":1")
        append(",\"id\":\"").append(escape(id)).append('"')
        append(",\"name\":\"").append(escape(name)).append('"')
        append(",\"created\":").append(createdAtMillis)
        append(",\"events\":").append(eventCount.coerceAtLeast(0))
        append(",\"signals\":").append(signalCount.coerceAtLeast(0))
        append('}')
    }

    /** Tolerant parse: null on any malformed input, so a damaged sidecar
     *  only hides its own slot instead of breaking the vault list. */
    fun decodeMeta(json: String): SlotMeta? {
        var id: String? = null
        var name: String? = null
        var created = 0L
        var events = 0
        var signals = 0
        val p = MetaParser(json)
        if (!p.expect('{')) return null
        if (!p.expect('}')) {
            while (true) {
                val key = p.string() ?: return null
                if (!p.expect(':')) return null
                when (key) {
                    "id" -> id = p.string() ?: return null
                    "name" -> name = p.string() ?: return null
                    "created" -> created = p.long() ?: return null
                    "events" -> events = p.int() ?: return null
                    "signals" -> signals = p.int() ?: return null
                    "v" -> if (p.int() == null) return null
                    else -> if (!p.skipValue()) return null
                }
                when {
                    p.expect(',') -> continue
                    p.expect('}') -> break
                    else -> return null
                }
            }
        }
        if (p.hasMore()) return null
        val slotId = id ?: return null
        if (!isSafeId(slotId)) return null
        return SlotMeta(
            id = slotId,
            name = name?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
            createdAtMillis = created,
            eventCount = events.coerceAtLeast(0),
            signalCount = signals.coerceAtLeast(0),
            sizeBytes = 0L // filled in by list()
        )
    }

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u").append(
                    Integer.toHexString(c.code).padStart(4, '0')
                )
                else -> append(c)
            }
        }
    }

    /** Minimal JSON scanner (mirrors GuideSignalsCodec.Parser). */
    private class MetaParser(private val src: String) {
        var i = 0

        fun skipWs() {
            while (i < src.length && src[i].isWhitespace()) i++
        }

        fun hasMore(): Boolean {
            skipWs()
            return i < src.length
        }

        fun expect(c: Char): Boolean {
            skipWs()
            if (src.getOrNull(i) == c) { i++; return true }
            return false
        }

        fun string(): String? {
            skipWs()
            if (src.getOrNull(i) != '"') return null
            i++
            val sb = StringBuilder()
            while (i < src.length) {
                val c = src[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        when (val e = src.getOrNull(i++)) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > src.length) return null
                                val code = src.substring(i, i + 4)
                                    .toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> return null
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return null
        }

        fun int(): Int? {
            skipWs()
            val start = i
            if (src.getOrNull(i) == '-') i++
            while (i < src.length && src[i].isDigit()) i++
            if (i == start) return null
            return src.substring(start, i).toIntOrNull()
        }

        fun long(): Long? {
            skipWs()
            val start = i
            if (src.getOrNull(i) == '-') i++
            while (i < src.length && src[i].isDigit()) i++
            if (i == start) return null
            return src.substring(start, i).toLongOrNull()
        }

        fun skipValue(): Boolean {
            skipWs()
            return when (src.getOrNull(i)) {
                '"' -> string() != null
                '{', '[' -> skipStruct()
                null -> false
                else -> {
                    val start = i
                    while (i < src.length && src[i] !in ",}]") i++
                    i > start
                }
            }
        }

        private fun skipStruct(): Boolean {
            val open = src[i]
            val close = if (open == '{') '}' else ']'
            var depth = 0
            var inStr = false
            var esc = false
            while (i < src.length) {
                val c = src[i++]
                if (inStr) {
                    when {
                        esc -> esc = false
                        c == '\\' -> esc = true
                        c == '"' -> inStr = false
                    }
                } else {
                    when (c) {
                        '"' -> inStr = true
                        open -> depth++
                        close -> { depth--; if (depth == 0) return true }
                    }
                }
            }
            return false
        }
    }

    companion object {
        /** Vault capacity: oldest slots are pruned beyond this. */
        const val MAX_SLOTS = 10
        const val PAYLOAD_EXT = LogBackupEngine.FILE_EXTENSION // ".snback"
        const val META_EXT = ".meta"
        const val DEFAULT_NAME = "Backup"
        const val MAX_NAME_LEN = 48
        val DIRNAME = "backup_vault"

        private val random = SecureRandom()
    }
}
