package com.shrekbytes.waqfah.ui.reading

import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.TranslationMeta

// The reading machine's on-demand probes — every fact ReadingSession fetches
// mid-step or mid-render, as opposed to the three signals it subscribes to
// (preferences, downloadedIds, progressReset), which stay direct constructor
// flows on the session. DefaultReadingPorts (data/repository) adapts the
// repositories to this interface, provided in AppModule; tests fake it inline.
// 1:1 with the function ports it replaced, so the session body is untouched.
interface ReadingPorts {
    suspend fun verseById(id: Int): VerseEntity?
    suspend fun nextVerse(afterId: Int): VerseEntity?
    suspend fun previousVerse(beforeId: Int): VerseEntity?
    suspend fun firstUnreadVerse(exclude: Set<Int>): VerseEntity?
    suspend fun randomUnreadVerse(exclude: Set<Int>): VerseEntity?
    suspend fun firstVerse(): VerseEntity?
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