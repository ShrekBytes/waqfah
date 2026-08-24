package com.shrekbytes.waqfah

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// isCorruption decides whether a failed read should DELETE the translation file
// (forcing a re-copy/re-download) or just drop the cached handle. Misclassifying
// either way blanks ayahs or wastes downloads.
class TranslationCorruptionTest {

    @Test
    fun corruptDatabase_isCorruption() {
        assertTrue(TranslationRepository.isCorruption(SQLiteDatabaseCorruptException("corrupt")))
    }

    // NOTE: the SQLiteException-with-"malformed"-message heuristic is not
    // covered here — the unit-test android.jar stubs exception methods, so
    // message content can't be exercised off-device.

    @Test
    fun nestedCause_isFound() {
        val wrapped = RuntimeException("getText failed", SQLiteDatabaseCorruptException("deep"))
        assertTrue(TranslationRepository.isCorruption(wrapped))
    }

    @Test
    fun unrelatedErrors_areNotCorruption() {
        assertFalse(TranslationRepository.isCorruption(IOException("disk full")))
        assertFalse(TranslationRepository.isCorruption(IllegalStateException("nope")))
        // A plain (non-corrupt, non-"malformed") SQLiteException must not force
        // a file delete — transient failures like SQLITE_BUSY would otherwise
        // trigger pointless re-downloads. Message-based branches are covered by
        // instrumented tests only; see NOTE above.
        assertFalse(TranslationRepository.isCorruption(SQLiteException()))
    }
}