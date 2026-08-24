package com.shrekbytes.waqfah.data.local.core

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SurahDao {
    @Query("SELECT * FROM surahs WHERE surah_no = :surahNo")
    suspend fun getBySurahNo(surahNo: Int): SurahEntity?
}

@Dao
interface VerseDao {
    @Query("SELECT * FROM verses WHERE id = :id")
    suspend fun getVerseById(id: Int): VerseEntity?

    @Query("SELECT * FROM verses WHERE id > :afterId ORDER BY id LIMIT 1")
    suspend fun getNextVerse(afterId: Int): VerseEntity?

    @Query("SELECT * FROM verses WHERE id < :beforeId ORDER BY id DESC LIMIT 1")
    suspend fun getPreviousVerse(beforeId: Int): VerseEntity?

    @Query("SELECT * FROM verses ORDER BY id LIMIT 1")
    suspend fun getFirstVerse(): VerseEntity?

    @Query("SELECT * FROM verses ORDER BY id DESC LIMIT 1")
    suspend fun getLastVerse(): VerseEntity?

    @Query("SELECT * FROM verses ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomVerse(): VerseEntity?

    @Query("SELECT COUNT(*) FROM verses")
    suspend fun countAll(): Int

    @Query("SELECT id FROM verses ORDER BY id")
    suspend fun getAllVerseIds(): List<Int>
}
