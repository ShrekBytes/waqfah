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

    suspend fun getAllSurahs(): List<SurahEntity> = quranDatabase.surahDao().getAll()

    suspend fun getVerseById(id: Int): VerseEntity? = quranDatabase.verseDao().getVerseById(id)

    suspend fun getVerse(surahNo: Int, ayahNo: Int): VerseEntity? =
        quranDatabase.verseDao().getBySurahAndAyah(surahNo, ayahNo)

    suspend fun getVerseIdsForSurah(surahNo: Int): List<Int> =
        quranDatabase.verseDao().getVerseIdsForSurah(surahNo)
    suspend fun getFirstVerse(): VerseEntity? = quranDatabase.verseDao().getFirstVerse()
    suspend fun getRandomVerse(): VerseEntity? = quranDatabase.verseDao().getRandomVerse()

    // Sequential mode fresh-session start: the lowest-numbered ayah not yet
    // marked read. Null only when every ayah has been read.
    suspend fun getFirstUnreadVerse(readVerseIds: Set<Int>): VerseEntity? {
        val dao = quranDatabase.verseDao()
        return dao.getAllVerseIds()
            .firstOrNull { it !in readVerseIds }
            ?.let { dao.getVerseById(it) }
    }

    // Random mode fresh-session start: any unread ayah, uniformly. Falls back
    // to a purely random ayah once everything has been marked read.
    suspend fun getRandomUnreadVerse(readVerseIds: Set<Int>): VerseEntity? {
        val dao = quranDatabase.verseDao()
        val unread = dao.getAllVerseIds().filterNot { it in readVerseIds }
        return unread.randomOrNull()?.let { dao.getVerseById(it) } ?: getRandomVerse()
    }

    // First unread within a specific surah — used by "Continue this surah".
    // Falls back to the surah's first ayah when everything is already read or
    // nothing has been read yet (caller wants always-enabled behavior).
    suspend fun getFirstUnreadVerseInSurah(surahNo: Int, readVerseIds: Set<Int>): VerseEntity? {
        val dao = quranDatabase.verseDao()
        val ids = dao.getVerseIdsForSurah(surahNo)
        val firstUnreadId = ids.firstOrNull { it !in readVerseIds }
        return when {
            firstUnreadId != null -> dao.getVerseById(firstUnreadId)
            ids.isNotEmpty() -> dao.getVerseById(ids.first())
            else -> null
        }
    }

    suspend fun totalVerseCount(): Int = quranDatabase.verseDao().countAll()

    // Wrap around at either end of the mushaf.
    suspend fun getNextVerse(afterId: Int): VerseEntity? =
        quranDatabase.verseDao().getNextVerse(afterId) ?: quranDatabase.verseDao().getFirstVerse()

    suspend fun getPreviousVerse(beforeId: Int): VerseEntity? =
        quranDatabase.verseDao().getPreviousVerse(beforeId) ?: quranDatabase.verseDao().getLastVerse()
}
