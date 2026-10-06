package dev.shrekbytes.waqfah.data.repository

import dev.shrekbytes.waqfah.data.local.core.QuranDatabase
import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.local.core.VerseSurahPair
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepository @Inject constructor(
    private val quranDatabase: QuranDatabase,
) : VerseLookups {
    suspend fun getSurah(surahNo: Int): SurahEntity? = quranDatabase.surahDao().getBySurahNo(surahNo)

    suspend fun getAllSurahs(): List<SurahEntity> = quranDatabase.surahDao().getAll()

    override suspend fun getVerseById(id: Int): VerseEntity? = quranDatabase.verseDao().getVerseById(id)

    // The set form of getVerseById, one round-trip instead of one per id. The
    // bookmark collection is a set of ids and re-emits on every toggle, so the
    // loop it replaces would cost a query per saved ayah per emission — the same
    // shape getAllVerseSurahPairs exists to avoid. An empty set is answered here
    // rather than in SQL: Room expands the IN list into placeholders, and none
    // of them is not a query.
    suspend fun getVersesByIds(ids: List<Int>): List<VerseEntity> =
        if (ids.isEmpty()) emptyList() else quranDatabase.verseDao().getVersesByIds(ids)

    suspend fun getVerse(surahNo: Int, ayahNo: Int): VerseEntity? =
        quranDatabase.verseDao().getBySurahAndAyah(surahNo, ayahNo)

    override suspend fun getVerseIdsForSurah(surahNo: Int): List<Int> =
        quranDatabase.verseDao().getVerseIdsForSurah(surahNo)

    suspend fun getAllVerseSurahPairs(): List<VerseSurahPair> =
        quranDatabase.verseDao().getAllVerseSurahPairs()

    override suspend fun getFirstVerse(): VerseEntity? = quranDatabase.verseDao().getFirstVerse()

    override suspend fun getLastVerse(): VerseEntity? = quranDatabase.verseDao().getLastVerse()

    override suspend fun getAllVerseIds(): List<Int> = quranDatabase.verseDao().getAllVerseIds()

    suspend fun totalVerseCount(): Int = quranDatabase.verseDao().countAll()

    // Raw positional neighbours, null at the mushaf ends — wrap-around is
    // verse selection's decision, never this adapter's.
    override suspend fun getNextVerse(afterId: Int): VerseEntity? =
        quranDatabase.verseDao().getNextVerse(afterId)

    override suspend fun getPreviousVerse(beforeId: Int): VerseEntity? =
        quranDatabase.verseDao().getPreviousVerse(beforeId)
}
