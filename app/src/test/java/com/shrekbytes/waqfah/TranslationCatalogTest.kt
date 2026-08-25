package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards the catalog's invariants — a typo here would silently break downloads
// or the active-translation lookup (TranslationCatalog.all.first { ... }).
class TranslationCatalogTest {

    @Test
    fun ids_areUniquePerLanguage() {
        TranslationLanguage.entries.forEach { lang ->
            val ids = TranslationCatalog.all.filter { it.language == lang }.map { it.id }
            assertEquals("Duplicate ids for $lang", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun eachLanguage_hasExactlyOneBundledTranslation() {
        TranslationLanguage.entries.forEach { lang ->
            val bundled = TranslationCatalog.all.filter { it.language == lang && it.isBundled }
            assertEquals("$lang must ship exactly one bundled translation", 1, bundled.size)
        }
    }

    @Test
    fun bundledMeansNoUrl_andDownloadableMeansUrl() {
        TranslationCatalog.all.forEach { meta ->
            if (meta.isBundled) {
                assertNull("'${meta.id}' is bundled but declares a download URL", meta.downloadUrl)
            } else {
                assertNotNull("'${meta.id}' is downloadable but has no URL", meta.downloadUrl)
            }
        }
    }

    @Test
    fun downloadUrls_pointAtExpectedRepoPath() {
        val expectedPrefix = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/"
        TranslationCatalog.all.forEach { meta ->
            meta.downloadUrl?.let { url ->
                assertTrue(
                    "'${meta.id}' URL doesn't match its language folder: $url",
                    url.startsWith(expectedPrefix + meta.language.code + "/"),
                )
                assertTrue("URL for '${meta.id}' should end with '<id>.db'", url.endsWith("${meta.id}.db"))
            }
        }
    }

    // Downloads are rejected unless their bytes hash to this pinned value, so
    // a missing/malformed checksum breaks integrity verification silently.
    @Test
    fun downloadableEntries_pinWellFormedSha256Checksums() {
        TranslationCatalog.all.forEach { meta ->
            if (meta.isBundled) {
                assertNull(
                    "bundled '${meta.id}' shouldn't pin a checksum — APK signing covers it",
                    meta.checksumSha256,
                )
            } else {
                val checksum = meta.checksumSha256
                assertNotNull("downloadable '${meta.id}' needs a pinned SHA-256", checksum)
                assertEquals("'${meta.id}' checksum must be 64 hex chars", 64, checksum!!.length)
                assertTrue(
                    "'${meta.id}' checksum must be hex",
                    checksum.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' },
                )
            }
        }
    }

    @Test
    fun languageCodes_matchBackupDirectoryNames() {
        assertEquals("en", TranslationLanguage.ENGLISH.code)
        assertEquals("bn", TranslationLanguage.BENGALI.code)
        assertFalse(TranslationLanguage.ENGLISH.code == TranslationLanguage.BENGALI.code)
    }
}