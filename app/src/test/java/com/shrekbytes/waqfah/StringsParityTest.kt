package com.shrekbytes.waqfah

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * Enforces string parity between the default and Bengali resource files — the
 * rule in AGENTS.md that every user-visible string in `values/strings.xml` is
 * mirrored in `values-bn/strings.xml`. A resource added to one file without
 * the other fails the build here instead of surfacing as untranslated UI at
 * runtime.
 *
 * A resource may be excluded from translation with `translatable="false"`,
 * which removes it from the parity requirement in both directions.
 */
class StringsParityTest {

    @Test
    fun `values-bn mirrors every string in values`() {
        val default = parse("values/strings.xml")
        val bengali = parse("values-bn/strings.xml")

        val missingInBengali = default.keys - bengali.keys
        val missingInDefault = bengali.keys - default.keys

        assertEquals(
            "strings added to values/strings.xml but missing in values-bn/: " +
                missingInBengali.sorted().joinToString(),
            emptySet<String>(),
            missingInBengali,
        )
        assertEquals(
            "strings in values-bn/strings.xml without an entry in values/: " +
                missingInDefault.sorted().joinToString(),
            emptySet<String>(),
            missingInDefault,
        )
    }

    /** Named `<string>` and `<plurals>` resources, minus `translatable="false"`. */
    private fun parse(path: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resFile(path))
        return listOf("string", "plurals").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.tagName }
    }

    /** Locates `app/src/main/res` whether the working dir is the repo or module. */
    private fun resFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "app/src/main/res/$relative")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("could not locate app/src/main/res/$relative from ${System.getProperty("user.dir")}")
    }
}
