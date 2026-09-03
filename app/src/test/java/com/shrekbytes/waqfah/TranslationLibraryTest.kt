package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.TranslationLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Spec for the translation library, worked against the shipped catalog:
// sahih is the bundled English entry; pickthall, yusufali and
// maududi are English downloads; rawaialbayan is a Bengali download; taisirul
// is the bundled Bengali entry.
class TranslationLibraryTest {

    // ---- available(): what the language can render right now ----

    @Test
    fun isAvailable_bundledEvenWithNoDiskTruth_missingOtherwise() {
        val bundled = TranslationLibrary.available(TranslationLanguage.ENGLISH, emptySet()).single()
        val download = TranslationCatalog.all.first { it.language == TranslationLanguage.ENGLISH && !it.isBundled }
        assertTrue(TranslationLibrary.isAvailable(bundled, emptySet()))
        assertFalse(TranslationLibrary.isAvailable(download, emptySet()))
        assertTrue(TranslationLibrary.isAvailable(download, setOf(download.id)))
    }

    @Test
    fun available_countsBundled_beforeItsLazyFirstCopy() {
        val ids = TranslationLibrary.available(TranslationLanguage.ENGLISH, emptySet()).map { it.id }
        assertEquals(listOf("sahih"), ids)
    }

    @Test
    fun available_countsDownloaded_excludesMissingAndOtherLanguages() {
        val ids = TranslationLibrary.available(TranslationLanguage.ENGLISH, setOf("pickthall", "rawaialbayan")).map { it.id }
        assertEquals(listOf("sahih", "pickthall"), ids)
    }

    @Test
    fun available_preservesCatalogOrder() {
        val ids = TranslationLibrary.available(TranslationLanguage.ENGLISH, setOf("maududi", "yusufali", "pickthall")).map { it.id }
        assertEquals(listOf("sahih", "pickthall", "yusufali", "maududi"), ids)
    }

    @Test
    fun available_bengaliWithNothingOnDisk_isJustTheBundledOne() {
        val ids = TranslationLibrary.available(TranslationLanguage.BENGALI, emptySet()).map { it.id }
        assertEquals(listOf("taisirul"), ids)
    }

    // ---- resolveActive(): stored translation when usable, else bundled ----

    @Test
    fun resolveActive_storedUsable_returnsIt() {
        val active = TranslationLibrary.resolveActive(TranslationLanguage.ENGLISH, "pickthall", setOf("pickthall"))
        assertEquals("pickthall", active.id)
    }

    @Test
    fun resolveActive_storedFileMissing_fallsBackToBundled() {
        val active = TranslationLibrary.resolveActive(TranslationLanguage.ENGLISH, "pickthall", emptySet())
        assertEquals("sahih", active.id)
    }

    @Test
    fun resolveActive_storedLeftTheCatalog_fallsBackToBundled() {
        val active = TranslationLibrary.resolveActive(TranslationLanguage.ENGLISH, "removed-after-update", setOf("removed-after-update"))
        assertEquals("sahih", active.id)
    }

    @Test
    fun resolveActive_storedBundled_needsNoDiskTruth() {
        val active = TranslationLibrary.resolveActive(TranslationLanguage.BENGALI, "taisirul", emptySet())
        assertEquals("taisirul", active.id)
    }

    @Test
    fun resolveActive_fallbackStaysWithinTheStoredLanguage() {
        // pickthall exists in the catalog but is an English translation —
        // never usable for Bengali.
        val active = TranslationLibrary.resolveActive(TranslationLanguage.BENGALI, "pickthall", setOf("pickthall"))
        assertEquals("taisirul", active.id)
    }
}
