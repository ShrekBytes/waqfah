package dev.shrekbytes.waqfah.data.repository

import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ReadingMode
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

// The verse sequence a reading session walks (see CONTEXT.md's "Verse
// selection" and "Bookmarked-ayah stepper"): which verse a fresh session opens
// on, and which verse comes next/previous, wrapping at the ends. Two
// implementations, deliberately siblings rather than one an extension of the
// other — VerseSelection answers "what verse comes next in the mushaf",
// BookmarkedAyahStepper answers "what bookmarked verse comes next" — and the
// reading machine is handed whichever applies without knowing which it has.
interface VerseSequence {
    // Whether this sequence is the whole mushaf. Read progress is the mushaf's
    // bookkeeping, so this is what decides its reach: the every-ayah-read
    // completion event belongs to a mushaf-wide walk, and a progress reset
    // re-lands one. A collection-scoped sequence has no relationship to read
    // status in either direction (ADR-0005).
    val isMushafWide: Boolean

    suspend fun start(mode: ReadingMode, readIds: Set<Int>): VerseEntity?

    suspend fun next(afterId: Int): VerseEntity?

    suspend fun previous(beforeId: Int): VerseEntity?
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
) : VerseSequence {

    override val isMushafWide = true

    // Fresh-session start: sequential opens on the lowest unread ayah (the
    // very first ayah once everything is read, so there is always content);
    // random opens on any unread ayah (any ayah at all once all are read);
    // bookmarks is not a mushaf ordering at all, so it means nothing here and
    // falls in with sequential — Home is always the whole Quran, and a fresh
    // Home session under BOOKMARKS opens on the first unread ayah exactly as it
    // does under SEQUENTIAL.
    override suspend fun start(mode: ReadingMode, readIds: Set<Int>): VerseEntity? {
        val ids = lookups.getAllVerseIds()
        return when (mode) {
            ReadingMode.SEQUENTIAL, ReadingMode.BOOKMARKS ->
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
    override suspend fun next(afterId: Int): VerseEntity? =
        lookups.getNextVerse(afterId) ?: lookups.getFirstVerse()

    override suspend fun previous(beforeId: Int): VerseEntity? =
        lookups.getPreviousVerse(beforeId) ?: lookups.getLastVerse()
}
