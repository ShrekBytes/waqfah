package com.shrekbytes.waqfah.data.repository

import com.shrekbytes.waqfah.data.local.core.QuranDatabase
import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepository @Inject constructor(
    private val quranDatabase: QuranDatabase,
) {
    suspend fun getAllSurahs(): List<SurahEntity> = quranDatabase.surahDao().getAll()
    suspend fun getSurah(surahNo: Int): SurahEntity? = quranDatabase.surahDao().getBySurahNo(surahNo)

    suspend fun getVerse(surahNo: Int, ayahNo: Int): VerseEntity? = quranDatabase.verseDao().getVerse(surahNo, ayahNo)
    suspend fun getVerseById(id: Int): VerseEntity? = quranDatabase.verseDao().getVerseById(id)
    suspend fun getFirstVerse(): VerseEntity? = quranDatabase.verseDao().getFirstVerse()
    suspend fun getRandomVerse(): VerseEntity? = quranDatabase.verseDao().getRandomVerse()
    suspend fun totalVerseCount(): Int = quranDatabase.verseDao().countAll()

    // Wraps back to the first verse once the end of the mushaf is reached.
    suspend fun getNextVerse(afterId: Int): VerseEntity? =
        quranDatabase.verseDao().getNextVerse(afterId) ?: quranDatabase.verseDao().getFirstVerse()

    // Wraps back to the last verse once the start of the mushaf is reached.
    suspend fun getPreviousVerse(beforeId: Int): VerseEntity? =
        quranDatabase.verseDao().getPreviousVerse(beforeId) ?: quranDatabase.verseDao().getLastVerse()
}
