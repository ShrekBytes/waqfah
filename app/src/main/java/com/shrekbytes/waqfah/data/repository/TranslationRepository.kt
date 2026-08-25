package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.util.Log
import com.shrekbytes.waqfah.data.local.translation.TranslationDatabase
import com.shrekbytes.waqfah.data.model.TranslationMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Bumped whenever translation files appear or disappear (download finished,
    // file deleted, bundled first-copy). Lets long-lived screens restat
    // availability reactively instead of staying stale until their next render.
    private val _downloadsChanged = MutableStateFlow(0)
    val downloadsChanged: StateFlow<Int> = _downloadsChanged.asStateFlow()

    // ConcurrentHashMap: getText() runs on arbitrary dispatchers while
    // download()/delete() run on others.
    private val openDatabases = ConcurrentHashMap<String, TranslationDatabase>()

    // Serializes downloads per translation id. Render fires up to THREE
    // concurrent getText calls for the same meta (main ayah + next/prev
    // previews), and parallel copyBundled/network runs share one `.tmp` path —
    // racing threads rename each other's temp file away mid-write, then fail
    // with NoSuchFileException ("Failed to copy bundled translation …").
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()

    // Suspend + IO: callers poll this per-row inside flow transforms (and
    // render paths) that run on the main dispatcher; File.exists() is disk I/O.
    suspend fun isDownloaded(meta: TranslationMeta): Boolean =
        withContext(Dispatchers.IO) { fileFor(meta).exists() }

    suspend fun getText(meta: TranslationMeta, verseId: Int): String? {
        // Bundled translations ship in the APK assets and are copied into
        // internal storage once, the first time they're needed.
        if (meta.isBundled && !isDownloaded(meta)) {
            try {
                download(meta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled translation '${meta.id}' from assets", e)
                return null
            }
        }
        if (!isDownloaded(meta)) return null

        return try {
            open(meta).translationDao().getText(verseId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading '${meta.id}' for verse $verseId", e)
            // Always drop the cached handle so the next call reopens cleanly,
            // but only DELETE the file when it's genuinely broken: transient
            // failures (e.g. SQLITE_BUSY while connections race a first open)
            // must not force a pointless re-copy/re-download and blank ayahs.
            withContext(Dispatchers.IO) {
                openDatabases.remove(meta.id)?.close()
                if (meta.isBundled || isCorruption(e)) {
                    Log.w(TAG, "Evicting translation file for '${meta.id}'")
                    fileFor(meta).delete()
                }
            }
            null
        }
    }

    /**
     * Downloads [meta] into internal storage — an asset copy for bundled
     * translations, a network fetch otherwise. [onProgress] receives a 0f..1f
     * fraction while downloading (never for bundled copies).
     *
     * Cancellation-safe and crash-safe: the body is written to a `.tmp` file
     * that's renamed into place only after being fully downloaded and verified
     * as a sqlite db matching the expected schema.
     */
    suspend fun download(meta: TranslationMeta, onProgress: (Float) -> Unit = {}) {
        // One worker per id; latecomers wait, then re-check instead of redoing.
        val lock = downloadLocks.getOrPut(meta.id) { Mutex() }
        lock.withLock {
            val target = fileFor(meta)
            target.parentFile?.mkdirs()
            if (meta.isBundled) {
                // A concurrent caller may have finished the asset copy while we
                // waited on the lock — the file is already complete then.
                if (!target.exists()) {
                    withContext(Dispatchers.IO) { copyBundled(meta, target) }
                }
            } else {
                val url = meta.downloadUrl
                    ?: error("No download URL configured for '${meta.id}'")
                withContext(Dispatchers.IO) {
                    // Lets coroutine cancellation interrupt the blocking socket read.
                    runInterruptible { downloadOverNetwork(meta, url, target, onProgress) }
                }
            }
            _downloadsChanged.update { it + 1 }
        }
    }

    // Holds the same per-id mutex as download() so a delete landing mid-download
    // can't be followed by the download's final rename resurrecting the file.
    suspend fun delete(meta: TranslationMeta) {
        val lock = downloadLocks.getOrPut(meta.id) { Mutex() }
        lock.withLock {
            withContext(Dispatchers.IO) {
                openDatabases.remove(meta.id)?.close()
                fileFor(meta).delete()
            }
            _downloadsChanged.update { it + 1 }
        }
    }

    private fun copyBundled(meta: TranslationMeta, target: File) {
        // Atomic like network downloads: an interrupted copy must never leave a
        // half-written file at the target path — that file would be treated as
        // valid forever (isDownloaded only checks existence) and surface as
        // sqlite corruption later.
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            context.assets.open("translations/${meta.language.code}/${meta.id}.db").use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            tmp.delete()
        }
    }

    private fun downloadOverNetwork(meta: TranslationMeta, url: String, target: File, onProgress: (Float) -> Unit) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        var connection: HttpURLConnection? = null
        try {
            connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream, */*")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Server returned HTTP $code while downloading '${meta.id}'")
            }

            val totalBytes = connection.contentLengthLong // -1 if not sent
            var bytesRead = 0L
            // Report at most once per whole percent — the raw per-chunk cadence
            // (every DOWNLOAD_BUFFER_BYTES) would flood StateFlow with thousands
            // of updates and recompose the translations screen for each.
            var lastReportedPercent = -1
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val percent = ((bytesRead * 100) / totalBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress((percent / 100f).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }

            validateSqliteFile(tmp, meta.id)

            // Atomic on the common case (same filesystem); fall back otherwise.
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            Log.e(TAG, "Download failed for '${meta.id}'", e)
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    // Cheap checks so a bad URL (404 page saved as "success", HTML redirect…)
    // fails with a clear error instead of surfacing later as a silent null or
    // a confusing Room crash.
    private fun validateSqliteFile(file: File, id: String) {
        val header = ByteArray(SQLITE_MAGIC.size)
        try {
            DataInputStream(file.inputStream()).use { it.readFully(header) }
        } catch (e: EOFException) {
            throw IOException("Downloaded file for '$id' isn't a SQLite database — check the download URL", e)
        }
        if (!header.contentEquals(SQLITE_MAGIC)) {
            throw IOException("Downloaded file for '$id' isn't a SQLite database — check the download URL")
        }

        val db = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            throw IOException("Downloaded file for '$id' isn't a readable SQLite database", e)
        }
        try {
            // A foreign sqlite file without Room's metadata relies on its
            // user_version matching what this build expects. version 0 means the
            // generator never opened it through Room — Room initializes the
            // identity on first open while keeping the existing tables — so 0 is
            // accepted alongside the expected version. Any OTHER mismatch would
            // surface later as destructive-migration data loss, so reject early.
            if (db.version != 0 && db.version != TranslationDatabase.SCHEMA_VERSION) {
                throw IOException(
                    "Downloaded database for '$id' has schema version ${db.version}, " +
                        "but this build expects ${TranslationDatabase.SCHEMA_VERSION}",
                )
            }
            db.rawQuery("SELECT verse_id, text FROM translations LIMIT 1", null).close()
        } catch (e: SQLiteException) {
            throw IOException(
                "Downloaded database for '$id' doesn't match the expected schema " +
                    "(table 'translations' with columns verse_id, text)",
                e,
            )
        } finally {
            db.close()
        }
    }

    private fun fileFor(meta: TranslationMeta): File =
        File(context.filesDir, "translations/${meta.language.code}/${meta.id}.db")

    // Double-checked creation: render fires up to three concurrent getText calls
    // for the SAME meta (main ayah + next/prev previews), and getOrPut alone is
    // not atomic — racing threads would each build a Room instance and open the
    // same sqlite file simultaneously, which intermittently fails (and used to
    // look like "switching en↔bn sometimes shows an empty translation").
    private val openLock = Any()

    private fun open(meta: TranslationMeta): TranslationDatabase =
        openDatabases[meta.id] ?: synchronized(openLock) {
            openDatabases.getOrPut(meta.id) { TranslationDatabase.build(context, fileFor(meta)) }
        }

    internal companion object {
        const val TAG = "TranslationRepository"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        // Decides whether a failed read should DELETE the translation file
        // (forcing a re-copy/re-download) or just drop the cached handle.
        // Misclassifying either way blanks ayahs or wastes downloads.
        internal fun isCorruption(e: Throwable): Boolean =
            generateSequence(e as Throwable?) { it.cause }.filterNotNull().any {
                it is SQLiteDatabaseCorruptException ||
                    (it is SQLiteException && it.message.orEmpty().contains("malformed", ignoreCase = true))
            }
    }
}
