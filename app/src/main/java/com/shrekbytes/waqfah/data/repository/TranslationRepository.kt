package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.shrekbytes.waqfah.data.local.translation.TranslationDatabase
import com.shrekbytes.waqfah.data.model.TranslationMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val openDatabases = mutableMapOf<String, TranslationDatabase>()

    fun isDownloaded(meta: TranslationMeta): Boolean = fileFor(meta).exists()

    suspend fun getText(meta: TranslationMeta, verseId: Int): String? {
        // Bundled translations ship in the APK's assets, but nothing copies
        // them into internal storage until this is actually needed — that
        // copy was previously only triggered by the Translations screen's
        // manual Download button, which nothing else in the app required
        // visiting. Do it here instead, once, the first time it's needed.
        if (meta.isBundled && !isDownloaded(meta)) {
            try {
                download(meta)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled translation '${meta.id}' from assets", e)
                return null
            }
        }
        if (!isDownloaded(meta)) return null

        return try {
            open(meta).translationDao().getText(verseId)
        } catch (e: Exception) {
            // Most likely cause if this fires: the .db file's actual schema
            // doesn't match TranslationEntity (table `translations`, columns
            // `verse_id INTEGER PRIMARY KEY` + `text TEXT`) — check with
            // `SELECT sql FROM sqlite_master WHERE name = 'translations';`
            // the same way the quran_core.db schema mismatch was diagnosed.
            Log.e(TAG, "Failed reading '${meta.id}' for verse $verseId", e)
            null
        }
    }

    /**
     * Downloads [meta] into internal storage — a plain asset copy for bundled
     * translations, a real network fetch otherwise. [onProgress] is called
     * with a 0f..1f fraction while a network download is in flight and the
     * server sent a Content-Length (never called for bundled copies, which
     * are effectively instant).
     *
     * Cancellation-safe and crash-safe: the network body is written to a
     * `.tmp` file that's only renamed into place after it's fully downloaded
     * and verified to actually be a sqlite db matching the expected schema,
     * so a failed, cancelled, or malformed download never leaves
     * `isDownloaded(meta)` returning true for a half-written or garbage file.
     */
    suspend fun download(meta: TranslationMeta, onProgress: (Float) -> Unit = {}) {
        val target = fileFor(meta)
        target.parentFile?.mkdirs()
        if (meta.isBundled) {
            withContext(Dispatchers.IO) { copyBundled(meta, target) }
        } else {
            val url = meta.downloadUrl
                ?: error("No download URL configured for '${meta.id}'")
            withContext(Dispatchers.IO) {
                // runInterruptible lets coroutine cancellation (e.g. the user
                // leaves the Translations screen mid-download) interrupt the
                // blocking socket read below instead of it silently
                // continuing to download in the background to nowhere.
                runInterruptible { downloadOverNetwork(meta, url, target, onProgress) }
            }
        }
    }

    fun delete(meta: TranslationMeta) {
        openDatabases.remove(meta.id)?.close()
        fileFor(meta).delete()
    }

    private fun copyBundled(meta: TranslationMeta, target: File) {
        // Already shipped in the APK — "downloading" is a local copy, no network needed.
        context.assets.open("translations/${meta.language.code}/${meta.id}.db").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
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

            val totalBytes = connection.contentLengthLong // -1 if the server didn't send one
            var bytesRead = 0L
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) onProgress((bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }

            validateSqliteFile(tmp, meta.id)

            // Atomic on the common case (same filesystem, internal storage);
            // fall back to copy+delete for the rare case it isn't.
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

    // Two cheap checks, in order of cost, so a bad download URL (wrong link,
    // a 404 page saved as "success", an HTML redirect, etc.) is caught here
    // with a clear message rather than surfacing later as a silent null from
    // getText() or a confusing Room crash.
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

    private fun open(meta: TranslationMeta): TranslationDatabase =
        openDatabases.getOrPut(meta.id) { TranslationDatabase.build(context, fileFor(meta)) }

    private companion object {
        const val TAG = "TranslationRepository"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
