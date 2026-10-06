package dev.shrekbytes.waqfah.ui.reading

import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.data.model.TranslationMeta

// The reading machine's on-demand probes — every fact ReadingSession fetches
// mid-step or mid-render, as opposed to the three signals it subscribes to
// (preferences, downloadedIds, progressReset), which stay direct constructor
// flows on the session. Verse movement (fresh start, stepping) is verse
// selection's decision, not a probe: the session takes VerseSequence
// directly. DefaultReadingPorts (data/repository) adapts the repositories to
// this interface, provided in AppModule; tests fake it inline.
interface ReadingPorts {
    suspend fun verseById(id: Int): VerseEntity?
    suspend fun surah(surahNo: Int): SurahEntity?
    suspend fun totalVerseCount(): Int
    suspend fun readVerseIds(): List<Int>
    suspend fun isRead(verseId: Int): Boolean
    suspend fun markRead(verseId: Int)
    suspend fun unmarkRead(verseId: Int)
    suspend fun countRead(): Int
    suspend fun resetAll()
    suspend fun translationText(meta: TranslationMeta, verseId: Int): String?
    suspend fun setReadingMode(mode: ReadingMode)
}