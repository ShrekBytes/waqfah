package com.shrekbytes.waqfah.data.repository

import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.ReadingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

// The raw verse reads behind verse selection: by-id, id lists, first/last,
// and the nullable positional neighbours (null at the mushaf ends —
// wrap-around is the selection module's decision, never this contract's).
// QuranRepository is the production adapter; tests fake this inline.
interface VerseLookups {
    suspend fun getVerseById(id: Int): VerseEntity?
    suspend fun getAllVerseIds(): List<Int>
    suspend fun getVerseIdsForSurah(surahNo: Int): List<Int>
    suspend fun getFirstVerse(): VerseEntity?
    suspend fun getLastVerse(): VerseEntity?
    suspend fun getNextVerse(afterId: Int): VerseEntity?
    suspend fun getPreviousVerse(beforeId: Int): VerseEntity?
}

// Verse selection (see CONTEXT.md): the one place that decides which verse
// to show. Stateless: callers pass a read-id snapshot in, so this module
// never touches the progress store and needs no lock of its own. Randomness
// comes only from the injected Random — no SQL randomness on these paths —
// so every verb is deterministic under a seeded Random in tests.
//
// Null means "no data" (empty table, unknown surah), never "all read" or
// "end reached": the everything-read fallbacks and the wrap-around already
// happened inside.
@Singleton
class VerseSelection @Inject constructor(
    private val lookups: VerseLookups,
    private val random: Random,
) {
    // Fresh-session start: sequential opens on the lowest unread ayah (the
    // very first ayah once everything is read, so there is always content);
    // random opens on any unread ayah (any ayah at all once all are read).
    suspend fun start(mode: ReadingMode, readIds: Set<Int>): VerseEntity? {
        val ids = lookups.getAllVerseIds()
        return when (mode) {
            ReadingMode.SEQUENTIAL ->
                ids.firstOrNull { it !in readIds }?.let { lookups.getVerseById(it) }
                    ?: lookups.getFirstVerse()
            ReadingMode.RANDOM ->
                ids.filterNot { it in readIds }.randomOrNull(random)?.let { lookups.getVerseById(it) }
                    ?: ids.randomOrNull(random)?.let { lookups.getVerseById(it) }
        }
    }

    // In-surah continue: the surah's first unread ayah, else the surah's
    // first ayah (the caller wants always-enabled behavior). Null only when
    // the surah has no rows at all.
    suspend fun continueInSurah(surahNo: Int, readIds: Set<Int>): VerseEntity? {
        val ids = lookups.getVerseIdsForSurah(surahNo)
        val firstUnreadId = ids.firstOrNull { it !in readIds }
        return when {
            firstUnreadId != null -> lookups.getVerseById(firstUnreadId)
            ids.isNotEmpty() -> lookups.getVerseById(ids.first())
            else -> null
        }
    }

    // Stepping by global id, wrapping around at either end of the mushaf.
    suspend fun next(afterId: Int): VerseEntity? =
        lookups.getNextVerse(afterId) ?: lookups.getFirstVerse()

    suspend fun previous(beforeId: Int): VerseEntity? =
        lookups.getPreviousVerse(beforeId) ?: lookups.getLastVerse()
}
