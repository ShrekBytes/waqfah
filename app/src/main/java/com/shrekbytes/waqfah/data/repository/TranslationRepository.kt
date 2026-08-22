package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.util.Log
import com.shrekbytes.waqfah.data.local.translation.TranslationDatabase
import com.shrekbytes.waqfah.data.model.TranslationMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

    suspend fun download(meta: TranslationMeta) = withContext(Dispatchers.IO) {
        val target = fileFor(meta)
        target.parentFile?.mkdirs()
        if (meta.isBundled) {
            // Already shipped in the APK — "downloading" is a local copy, no network needed.
            context.assets.open("translations/${meta.language.code}/${meta.id}.db").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            // TODO (translations screen phase): fetch meta.downloadUrl with an
            // HTTP client (OkHttp/Ktor), write to a .tmp file first, then rename
            // to `target` on success so a failed download can't leave a
            // half-written db behind.
            error("Network download not wired up yet for ${meta.id}")
        }
    }

    fun delete(meta: TranslationMeta) {
        openDatabases.remove(meta.id)?.close()
        fileFor(meta).delete()
    }

    private fun fileFor(meta: TranslationMeta): File =
        File(context.filesDir, "translations/${meta.language.code}/${meta.id}.db")

    private fun open(meta: TranslationMeta): TranslationDatabase =
        openDatabases.getOrPut(meta.id) { TranslationDatabase.build(context, fileFor(meta)) }

    private companion object {
        const val TAG = "TranslationRepository"
    }
}
