package com.securenet.vpn.guide

/**
 * v1.8.2 — serialisable snapshot of the guide's user signals (1–5 star
 * ratings, favorite bookmarks, personal notes) that travels INSIDE the
 * encrypted `.snback` backup next to the session log, so switching or
 * restoring a device carries the user's curated guide state with it.
 *
 * Pure Kotlin, zero Android imports, and the JSON is hand-rolled rather
 * than org.json — the project's unit tests run on the plain JVM without
 * Android stubs, so both the codec and the non-destructive merge below
 * are fully unit-testable.
 *
 * Wire format (embedded as an extra `"signals":{…}` member of the backup
 * header line, see [com.securenet.vpn.backup.LogBackupEngine]):
 * ```
 * {"v":1,"ratings":{"mullvad":5},"favorites":["mullvad"],"notes":{"ivpn":"fast"}}
 * ```
 * Strings are escaped (quotes, backslashes, control characters) so a
 * multi-line personal note can never break the single-line header
 * structure of the container.
 */
data class GuideSignalsData(
    /** Source id → star rating, every value already normalised to 1..5. */
    val ratings: Map<String, Int> = emptyMap(),
    /** Source ids the user bookmarked. */
    val favorites: Set<String> = emptySet(),
    /** Source id → personal note (trimmed, non-blank). */
    val notes: Map<String, String> = emptyMap()
) {
    fun isEmpty(): Boolean =
        ratings.isEmpty() && favorites.isEmpty() && notes.isEmpty()

    /** Total number of individual signal values (used by the export toast). */
    fun signalCount(): Int = ratings.size + favorites.size + notes.size

    companion object {

        /** Reads [store] into an immutable snapshot (pure, JVM-testable). */
        fun snapshot(store: GuideStore): GuideSignalsData {
            val ratings = store.ratedIds().mapNotNull { id ->
                val stars = store.rating(id)
                if (stars in 1..5) id to stars else null
            }.toMap()
            val notes = store.noteIds().mapNotNull { id ->
                store.note(id)?.let { id to it }
            }.toMap()
            return GuideSignalsData(ratings, store.favoriteIds().toSet(), notes)
        }
    }
}

/**
 * Hand-rolled JSON codec for [GuideSignalsData]. Only the three known
 * member shapes are produced; the parser additionally tolerates unknown
 * members (forward compatibility) and sanitises every value through the
 * same rules [GuideStore] documents: ratings outside 1..5 are dropped,
 * notes are trimmed and blanks removed, blank favourite ids ignored.
 */
object GuideSignalsCodec {

    /** Encodes [signals] as a single-line JSON object (no raw newlines). */
    fun encode(signals: GuideSignalsData): String = buildString {
        append("{\"v\":1")
        append(",\"ratings\":{")
        signals.ratings.entries.forEachIndexed { i, (id, stars) ->
            if (i > 0) append(',')
            append(escape(id)).append(':').append(stars.coerceIn(1, 5))
        }
        append("},\"favorites\":[")
        signals.favorites.forEachIndexed { i, id ->
            if (i > 0) append(',')
            append(escape(id))
        }
        append("],\"notes\":{")
        signals.notes.entries.forEachIndexed { i, (id, note) ->
            if (i > 0) append(',')
            append(escape(id)).append(':').append(escape(note))
        }
        append("}}")
    }

    /**
     * Parses a signals JSON object (exactly the `{…}` text, as extracted
     * from the backup header). Returns null on any malformed input instead
     * of throwing, so a corrupt member never breaks restoring the log.
     */
    fun decode(json: String): GuideSignalsData? {
        val p = Parser(json)
        if (!p.expect('{')) return null
        var ratings: Map<String, Int> = emptyMap()
        var favorites: Set<String> = emptySet()
        var notes: Map<String, String> = emptyMap()
        if (!p.expect('}')) {
            while (true) {
                val key = p.string() ?: return null
                if (!p.expect(':')) return null
                when (key) {
                    "ratings" ->
                        ratings = p.intMap()
                            ?.filterValues { it in 1..5 }
                            ?: return null
                    "favorites" -> favorites = p.stringSet() ?: return null
                    "notes" ->
                        notes = p.stringMap()
                            ?.mapValues { GuideStore.normalizeNote(it.value) ?: "" }
                            ?.filterValues { it.isNotEmpty() }
                            ?: return null
                    "v" -> if (p.int() == null) return null
                    else -> if (!p.skipValue()) return null // unknown → skip
                }
                when {
                    p.expect(',') -> continue
                    p.expect('}') -> break
                    else -> return null
                }
            }
        }
        if (p.hasMore()) return null // trailing garbage after the object
        return GuideSignalsData(ratings, favorites, notes)
    }

    // ------------------------------------------------------------------

    private fun escape(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u").append(
                    Integer.toHexString(c.code).padStart(4, '0')
                )
                else -> append(c)
            }
        }
        append('"')
    }

    /** Minimal JSON scanner: strings, ints, string maps/sets, skip-rest. */
    private class Parser(private val src: String) {

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
            return null // unterminated string
        }

        fun int(): Int? {
            skipWs()
            val start = i
            if (src.getOrNull(i) == '-') i++
            while (i < src.length && src[i].isDigit()) i++
            if (i == start) return null
            return src.substring(start, i).toIntOrNull()
        }

        fun stringSet(): Set<String>? {
            if (!expect('[')) return null
            val set = mutableSetOf<String>()
            if (expect(']')) return set
            while (true) {
                val v = string() ?: return null
                if (v.isNotBlank()) set.add(v)
                when {
                    expect(',') -> continue
                    expect(']') -> return set
                    else -> return null
                }
            }
        }

        fun stringMap(): Map<String, String>? {
            if (!expect('{')) return null
            val map = mutableMapOf<String, String>()
            if (expect('}')) return map
            while (true) {
                val k = string() ?: return null
                if (!expect(':')) return null
                val v = string() ?: return null
                map[k] = v
                when {
                    expect(',') -> continue
                    expect('}') -> return map
                    else -> return null
                }
            }
        }

        fun intMap(): Map<String, Int>? {
            if (!expect('{')) return null
            val map = mutableMapOf<String, Int>()
            if (expect('}')) return map
            while (true) {
                val k = string() ?: return null
                if (!expect(':')) return null
                val v = int() ?: return null
                map[k] = v
                when {
                    expect(',') -> continue
                    expect('}') -> return map
                    else -> return null
                }
            }
        }

        /** Skips one unknown JSON value (string/number/literal/object/array). */
        fun skipValue(): Boolean {
            skipWs()
            return when (src.getOrNull(i)) {
                '"' -> string() != null
                '{' -> skipStruct('{', '}')
                '[' -> skipStruct('[', ']')
                null -> false
                else -> {
                    val start = i
                    while (i < src.length && src[i] !in ",}]") i++
                    i > start
                }
            }
        }

        private fun skipStruct(open: Char, close: Char): Boolean {
            if (src.getOrNull(i) != open) return false
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
}

/**
 * Non-destructive merge of imported signals into the local ones — the
 * restore path never overwrites a value the user already has: a rating
 * they set, a bookmark they made and a note they wrote all win, and the
 * backup only fills the gaps. This mirrors the philosophy of the event
 * log merge in [com.securenet.vpn.backup.LogBackupEngine.merge] (nothing
 * already on the device is ever dropped).
 */
object GuideSignalsMerge {

    data class Result(
        /** The merged signal set to apply to the live store. */
        val applied: GuideSignalsData,
        /** Distinct source ids that received at least one imported value. */
        val touchedIds: Set<String>,
        /** Individual local values preserved instead of the imported ones. */
        val keptLocal: Int
    )

    fun merge(local: GuideSignalsData, imported: GuideSignalsData): Result {
        val ratings = mutableMapOf<String, Int>()
        val favorites = mutableSetOf<String>()
        val notes = mutableMapOf<String, String>()
        val touched = mutableSetOf<String>()
        var kept = 0

        (local.ratings.keys + imported.ratings.keys).forEach { id ->
            val l = local.ratings[id]
            val imp = imported.ratings[id]
            when {
                l != null -> { ratings[id] = l; kept++ }
                imp != null -> { ratings[id] = imp; touched.add(id) }
            }
        }
        (local.favorites + imported.favorites).forEach { id ->
            // Local bookmarks are preserved verbatim; the backup only
            // fills the gaps (ids not bookmarked locally yet).
            favorites.add(id)
            if (id in local.favorites) kept++ else touched.add(id)
        }
        (local.notes.keys + imported.notes.keys).forEach { id ->
            val l = local.notes[id]
            val imp = imported.notes[id]
            when {
                l != null -> { notes[id] = l; kept++ }
                imp != null -> { notes[id] = imp; touched.add(id) }
            }
        }
        return Result(
            GuideSignalsData(ratings, favorites, notes),
            touched,
            kept
        )
    }

    /** Writes [signals] into [store]; returns the number of values written. */
    fun applyTo(store: GuideStore, signals: GuideSignalsData): Int {
        signals.ratings.forEach { (id, stars) -> store.setRating(id, stars) }
        signals.favorites.forEach { id -> store.setFavorite(id, true) }
        signals.notes.forEach { (id, text) -> store.setNote(id, text) }
        return signals.signalCount()
    }
}
