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
    suspend fun getSurah(surahNo: Int): SurahEntity? = quranDatabase.surahDao().getBySurahNo(surahNo)

    suspend fun getVerseById(id: Int): VerseEntity? = quranDatabase.verseDao().getVerseById(id)
    suspend fun getFirstVerse(): VerseEntity? = quranDatabase.verseDao().getFirstVerse()
    suspend fun getRandomVerse(): VerseEntity? = quranDatabase.verseDao().getRandomVerse()
    suspend fun totalVerseCount(): Int = quranDatabase.verseDao().countAll()

    // Wrap around at either end of the mushaf.
    suspend fun getNextVerse(afterId: Int): VerseEntity? =
        quranDatabase.verseDao().getNextVerse(afterId) ?: quranDatabase.verseDao().getFirstVerse()

    suspend fun getPreviousVerse(beforeId: Int): VerseEntity? =
        quranDatabase.verseDao().getPreviousVerse(beforeId) ?: quranDatabase.verseDao().getLastVerse()
}
