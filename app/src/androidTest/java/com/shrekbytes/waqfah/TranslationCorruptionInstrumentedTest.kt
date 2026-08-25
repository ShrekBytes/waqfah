package com.shrekbytes.waqfah

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Runs against real Android runtime classes, covering what the JVM unit suite
// can't: the SQLiteException-with-"malformed"-message heuristic (the unit-test
// android.jar stubs exception methods, so message content isn't exercisable
// there). Run via ./gradlew :app:connectedDebugAndroidTest on a device/emulator.
@RunWith(AndroidJUnit4::class)
class TranslationCorruptionInstrumentedTest {

    @Test
    fun malformedMessage_isCorruption() {
        assertTrue(
            TranslationRepository.isCorruption(
                SQLiteException("file is not a database: database disk image is malformed"),
            ),
        )
    }

    @Test
    fun otherSqliteErrors_areNotCorruption() {
        assertFalse(
            TranslationRepository.isCorruption(SQLiteException("unable to open database file")),
        )
        assertFalse(TranslationRepository.isCorruption(SQLiteException()))
    }
}