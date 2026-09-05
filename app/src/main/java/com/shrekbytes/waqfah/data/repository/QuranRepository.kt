package com.shrekbytes.waqfah.data.repository

import com.shrekbytes.waqfah.data.local.core.QuranDatabase
import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.local.core.VerseSurahPair
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepository @Inject constructor(
    private val quranDatabase: QuranDatabase,
) : VerseLookups {
    suspend fun getSurah(surahNo: Int): SurahEntity? = quranDatabase.surahDao().getBySurahNo(surahNo)

    suspend fun getAllSurahs(): List<SurahEntity> = quranDatabase.surahDao().getAll()

    override suspend fun getVerseById(id: Int): VerseEntity? = quranDatabase.verseDao().getVerseById(id)

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
