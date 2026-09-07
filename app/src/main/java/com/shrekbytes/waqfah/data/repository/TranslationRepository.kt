package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.util.Log
import com.shrekbytes.waqfah.data.local.translation.TranslationDatabase
import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Disk truth: which catalog translations have a file on disk right now.
    // Restated on init and after every download/delete/first-copy; equal sets
    // dedupe, so observers re-render only when availability actually changed.
    // Empty until the first refresh lands — bundled translations never depend
    // on it (TranslationLibrary counts them unconditionally).
    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    // Repository is an app-lifetime singleton, so this scope lives for the
    // process — it exists only to run the initial refresh.
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        initScope.launch { refreshDownloadedIds() }
    }

    // Republishes disk truth. Serialized so an early refresh can never finish
    // after a later one and publish a stale set (a finished download would
    // then read as missing until the next change).
    private val refreshLock = Mutex()

    private suspend fun refreshDownloadedIds() = refreshLock.withLock {
        val ids = withContext(Dispatchers.IO) {
            TranslationCatalog.all.filter { fileFor(it).exists() }.map { it.id }.toSet()
        }
        _downloadedIds.value = ids
    }

    // ConcurrentHashMap: getText() runs on arbitrary dispatchers while
    // download()/delete() run on others.
    private val openDatabases = ConcurrentHashMap<String, TranslationDatabase>()

    // Serializes downloads per translation id. Render fires up to THREE
    // concurrent getText calls for the same meta (main ayah + next/prev
    // previews), and parallel copyBundled/network runs share one `.tmp` path —
    // racing threads rename each other's temp file away mid-write, then fail
    // with NoSuchFileException ("Failed to copy bundled translation …").
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()

    // Suspend + IO: callers poll this per-row inside render paths that run on
    // the main dispatcher; File.exists() is disk I/O. Private — callers read
    // availability through downloadedIds + TranslationLibrary instead.
    private suspend fun isDownloaded(meta: TranslationMeta): Boolean =
        withContext(Dispatchers.IO) { fileFor(meta).exists() }

    suspend fun getText(meta: TranslationMeta, verseId: Int): String? {
        // Bundled translations ship in the APK assets and are copied into
        // internal storage once, the first time they're needed. One existence
        // check serves both branches: a missing non-bundled file returns null,
        // a missing bundled one triggers the asset copy.
        if (!isDownloaded(meta)) {
            if (!meta.isBundled) return null
            try {
                download(meta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled translation '${meta.id}' from assets", e)
                return null
            }
        }

        return try {
            open(meta).translationDao().getText(verseId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading '${meta.id}' for verse $verseId", e)
            var evicted = false
            // Always drop the cached handle so the next call reopens cleanly,
            // but only DELETE the file when it's genuinely broken: transient
            // failures (e.g. SQLITE_BUSY while connections race a first open)
            // must not force a pointless re-copy/re-download and blank ayahs.
            // Under openLock so a concurrent getText can't rebuild a handle
            // over the file being closed/removed (see openLock).
            withContext(Dispatchers.IO) {
                synchronized(openLock) {
                    openDatabases.remove(meta.id)?.close()
                    if (meta.isBundled || isCorruption(e)) {
                        Log.w(TAG, "Evicting translation file for '${meta.id}'")
                        fileFor(meta).delete()
                        evicted = true
                    }
                }
            }
            // The published set must forget the file too, or resolveActive
            // would keep returning a translation that now renders null.
            if (evicted) refreshDownloadedIds()
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
            refreshDownloadedIds()
        }
    }

    // Holds the same per-id mutex as download() so a delete landing mid-download
    // can't be followed by the download's final rename resurrecting the file;
    // the handle removal + file deletion run under openLock so they can't race
    // a concurrent getText's reopen either (see openLock).
    suspend fun delete(meta: TranslationMeta) {
        val lock = downloadLocks.getOrPut(meta.id) { Mutex() }
        lock.withLock {
            withContext(Dispatchers.IO) {
                synchronized(openLock) {
                    openDatabases.remove(meta.id)?.close()
                    fileFor(meta).delete()
                }
            }
            refreshDownloadedIds()
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
            val sha256 = connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    copyBodyCapped(input, output, totalBytes, onProgress = onProgress)
                }
            }

            validateSqliteFile(tmp, meta.id)
            verifyChecksum(meta, sha256)

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
    // Internal for TranslationValidationInstrumentedTest.
    internal fun validateSqliteFile(file: File, id: String) {
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

    // One lock for the handle map's FULL lifecycle — creation, eviction, and
    // the deletions that must not race a reopen. Double-checked creation:
    // render fires up to three concurrent getText calls for the SAME meta
    // (main ayah + next/prev previews), and getOrPut alone is not atomic —
    // racing threads would each build a Room instance and open the same
    // sqlite file simultaneously, which intermittently fails (and used to
    // look like "switching en↔bn sometimes shows an empty translation").
    // Eviction and delete share the lock too: without it, a reopen could
    // build a fresh handle while a close+delete is in flight, and Room would
    // silently recreate an empty database at a path the rest of the app
    // already believes is gone ("no such table" from then on).
    private val openLock = Any()

    private fun open(meta: TranslationMeta): TranslationDatabase =
        openDatabases[meta.id] ?: synchronized(openLock) {
            openDatabases.getOrPut(meta.id) {
                // A delete or eviction may have removed the file while we
                // waited on the lock; building now would recreate it (see
                // openLock). Throwing routes into getText's failure path,
                // which re-copies bundled translations on the next call.
                check(fileFor(meta).exists()) { "Translation file for '${meta.id}' was removed before it could be opened" }
                TranslationDatabase.build(context, fileFor(meta))
            }
        }

    internal companion object {
        const val TAG = "TranslationRepository"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        // Refuses any download body over this size (declared or actually read)
        // so a hostile server can't fill internal storage before the checksum
        // gate runs. Real translations are a few MB.
        const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024

        // Streams the response body into [output] while hashing it, refusing
        // anything over [maxBytes] — either up front when the declared
        // [totalBytes] says so (-1 when the server sends no length), or as
        // soon as the bytes actually read cross the line. Aborting mid-stream
        // is fine: the caller deletes the tmp file on throw. Progress reports
        // at most once per whole percent against totalBytes — the raw
        // per-chunk cadence (every DOWNLOAD_BUFFER_BYTES) would flood
        // StateFlow with thousands of updates and recompose the translations
        // screen for each. Internal for TranslationIntegrityTest.
        internal fun copyBodyCapped(
            input: InputStream,
            output: OutputStream,
            totalBytes: Long,
            maxBytes: Long = MAX_DOWNLOAD_BYTES,
            onProgress: (Float) -> Unit,
        ): MessageDigest {
            if (totalBytes > maxBytes) {
                throw IOException(
                    "Translation download reports $totalBytes bytes — over the $maxBytes-byte limit, refusing to save it",
                )
            }
            val sha256 = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            var bytesRead = 0L
            var lastReportedPercent = -1
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                sha256.update(buffer, 0, read)
                bytesRead += read
                if (bytesRead > maxBytes) {
                    throw IOException(
                        "Translation download exceeded the $maxBytes-byte limit after $bytesRead bytes — refusing to save it",
                    )
                }
                if (totalBytes > 0) {
                    val percent = ((bytesRead * 100) / totalBytes).toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress((percent / 100f).coerceIn(0f, 1f))
                    }
                }
            }
            return sha256
        }

        // Rejects files whose bytes don't match the SHA-256 pinned in
        // TranslationCatalog — runs BEFORE the atomic rename, so a tampered or
        // silently-changed published file can never land at the target path.
        // Bundled asset copies skip this (APK signing covers their integrity).
        // Fails closed: a downloadable entry without a pinned checksum must
        // never install unverified bytes of scripture, so it throws instead of
        // skipping verification. Internal for TranslationIntegrityTest.
        internal fun verifyChecksum(meta: TranslationMeta, digest: MessageDigest) {
            val expected = meta.checksumSha256
                ?: error("Downloadable translation '${meta.id}' has no pinned SHA-256 in TranslationCatalog")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IOException(
                    "Downloaded database for '${meta.id}' failed its integrity check (SHA-256 mismatch). " +
                        "If translations/${meta.language.code}/${meta.id}.db changed in the repo, " +
                        "update its checksum in TranslationCatalog.",
                )
            }
        }

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
