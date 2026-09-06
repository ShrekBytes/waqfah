package com.shrekbytes.waqfah

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrekbytes.waqfah.data.local.translation.TranslationDatabase
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import java.io.File
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Runs against real Android runtime classes (the JVM's android.jar stubs can't
// open real SQLite databases), covering validateSqliteFile — the gate deciding
// whether a downloaded file ever lands at its target path. Run via
// ./gradlew :app:connectedDebugAndroidTest on a device/emulator.
@RunWith(AndroidJUnit4::class)
class TranslationValidationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = TranslationRepository(context)
    private val dir = File(context.cacheDir, "translation-validation-test").apply { mkdirs() }

    // A real sqlite db with the expected table; version defaults to 0, which
    // validateSqliteFile accepts alongside SCHEMA_VERSION (a file the catalog
    // generator never opened through Room).
    private fun validDb(name: String, version: Int = 0, withTable: Boolean = true): File {
        val file = File(dir, name)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            if (withTable) {
                db.execSQL("CREATE TABLE translations (verse_id INTEGER NOT NULL, text TEXT NOT NULL)")
            }
            if (version != 0) db.version = version
        }
        return file
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun freshGeneratorFile_versionZero_isAccepted() {
        repository.validateSqliteFile(validDb("version0.db"), "version0")
    }

    @Test
    fun roomInitializedFile_currentSchemaVersion_isAccepted() {
        val file = validDb("current.db", version = TranslationDatabase.SCHEMA_VERSION)
        repository.validateSqliteFile(file, "current")
    }

    @Test
    fun truncatedFile_isRejected() {
        val file = File(dir, "truncated.db").apply { writeBytes(ByteArray(5)) }
        val e = assertThrows(IOException::class.java) {
            repository.validateSqliteFile(file, "truncated")
        }
        assertTrue(e.message!!.contains("isn't a SQLite database"))
    }

    @Test
    fun nonSqliteBytes_areRejected() {
        val file = File(dir, "html.db").apply {
            writeBytes("definitely not a sqlite database, just some html".toByteArray())
        }
        val e = assertThrows(IOException::class.java) {
            repository.validateSqliteFile(file, "html")
        }
        assertTrue(e.message!!.contains("isn't a SQLite database"))
    }

    @Test
    fun corruptSqliteFile_isRejected() {
        // Valid magic bytes followed by garbage — whether openDatabase or the
        // schema probe trips first, the file must not be accepted.
        val file = File(dir, "corrupt.db").apply {
            writeBytes(TranslationRepository.SQLITE_MAGIC + ByteArray(64))
        }
        assertThrows(IOException::class.java) {
            repository.validateSqliteFile(file, "corrupt")
        }
    }

    @Test
    fun wrongSchemaVersion_isRejected() {
        val file = validDb("wrongversion.db", version = TranslationDatabase.SCHEMA_VERSION + 1)
        val e = assertThrows(IOException::class.java) {
            repository.validateSqliteFile(file, "wrongversion")
        }
        assertTrue(e.message!!.contains("schema version"))
    }

    @Test
    fun missingTranslationsTable_isRejected() {
        val file = validDb("notable.db", withTable = false)
        val e = assertThrows(IOException::class.java) {
            repository.validateSqliteFile(file, "notable")
        }
        assertTrue(e.message!!.contains("doesn't match the expected schema"))
    }
}
