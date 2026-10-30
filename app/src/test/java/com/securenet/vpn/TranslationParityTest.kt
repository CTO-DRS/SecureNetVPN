package com.securenet.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * v1.8.22 — the translation-parity lock (قفل تكافؤ الترجمة).
 *
 * The six v1.8.17 checker languages (de/es/fr/tr/ru/ur) gained the FULL
 * guide UI in v1.8.22 — 195 guide_* keys each — and the checker's wgcheck_*
 * set existed since v1.8.17. Partial translations are fine (standard
 * resource fallback keeps everything else EN), but the keys that DO ship
 * in a language must never drift: a missing key crashes resources not found
 * at worst or silently falls back at best, and a placeholder mismatch
 * (%1$s vs %2$s) crashes String.format at runtime on a user's device in
 * exactly one language — the kind of bug nothing else catches.
 *
 * This test is pure JVM: it locates app/src/main/res on the filesystem,
 * parses every strings.xml with a real XML parser (which also proves the
 * files are well-formed XML, not just regex-friendly) and pins, for each
 * translated language:
 *  1. key parity — every EN guide-family and wgcheck-family key present exactly once
 *  2. placeholder parity — identical format-placeholder multisets
 *  3. non-empty values (a translated key is worth shipping with text)
 * plus a file-level rule: no duplicate string names anywhere in a file.
 */
class TranslationParityTest {

    private val languages = listOf("de", "es", "fr", "tr", "ru", "ur")

    /** Locate the res dir whether tests run from the module dir or the repo root. */
    private fun resDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val d = dir ?: return@repeat
            listOf(File(d, "app/src/main/res"), File(d, "src/main/res")).forEach { candidate ->
                if (candidate.isDirectory && File(candidate, "values/strings.xml").isFile) {
                    return candidate
                }
            }
            dir = d.parentFile
        }
        error("app/src/main/res not found from ${System.getProperty("user.dir")}")
    }

    /** Parse a strings.xml as real XML; return ordered name→text pairs. */
    private fun parse(file: File): List<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = ArrayList<Pair<String, String>>(nodes.length)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name").nodeValue
            out.add(name to node.textContent)
        }
        return out
    }

    private fun keysByFamily(file: File): Map<String, List<Pair<String, String>>> {
        val all = parse(file)
        val names = all.map { it.first }
        assertEquals(
            "duplicate string names in ${file.parentFile.name}",
            names.size, names.distinct().size
        )
        return all.groupBy { (name, _) ->
            when {
                name.startsWith("guide_") -> "guide_"
                name.startsWith("wgcheck_") -> "wgcheck_"
                else -> "other"
            }
        }
    }

    private fun placeholders(text: String): List<String> =
        Regex("%[0-9]+\\$[sd]|%[sd]").findAll(text).map { it.value }.sorted().toList()

    private fun assertFamilyParity(family: String) {
        val res = resDir()
        val en = keysByFamily(File(res, "values/strings.xml"))[family].orEmpty()
        val enMap = en.toMap()
        assertTrue("EN $family must not be empty", en.isNotEmpty())
        for (lang in languages) {
            val file = File(res, "values-$lang/strings.xml")
            assertTrue("values-$lang/strings.xml missing", file.isFile)
            val translated = keysByFamily(file)[family].orEmpty()
            val map = translated.toMap()
            val missing = enMap.keys - map.keys
            val extra = map.keys - enMap.keys
            assertTrue(
                "[$lang] $family missing keys: ${missing.sorted().take(8)}",
                missing.isEmpty()
            )
            assertTrue(
                "[$lang] $family extra keys: ${extra.sorted().take(8)}",
                extra.isEmpty()
            )
            for ((key, enText) in enMap) {
                val value = map.getValue(key)
                assertTrue("[$lang] $key has blank text", value.isNotBlank())
                assertEquals(
                    "[$lang] $key placeholder drift",
                    placeholders(enText), placeholders(value)
                )
            }
        }
    }

    @Test
    fun `guide strings match the EN key set and placeholders in all six languages`() {
        assertFamilyParity("guide_")
    }

    @Test
    fun `checker strings keep their v1_8_17 parity`() {
        assertFamilyParity("wgcheck_")
    }

    @Test
    fun `six languages each carry at least the full guide family`() {
        val res = resDir()
        for (lang in languages) {
            val guideCount = parse(File(res, "values-$lang/strings.xml"))
                .count { it.first.startsWith("guide_") }
            assertEquals("[$lang] guide key count", 195, guideCount)
        }
    }

    @Test
    fun `files are real XML for every language folder`() {
        val res = resDir()
        for (lang in listOf("values", "values-ar") + languages.map { "values-$it" }) {
            // parse() throws on malformed XML — reaching here proves validity
            parse(File(res, "$lang/strings.xml"))
        }
    }
}
