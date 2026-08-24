package com.shrekbytes.waqfah.data.repository

import com.shrekbytes.waqfah.data.local.appstate.ReadVerseEntity
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val appDatabase: WaqfahAppDatabase,
) {
    val readCount: Flow<Int> = appDatabase.readVerseDao().observeReadCount()

    suspend fun markRead(verseId: Int) =
        appDatabase.readVerseDao().markRead(ReadVerseEntity(verseId, readAt = System.currentTimeMillis()))

    suspend fun unmarkRead(verseId: Int) = appDatabase.readVerseDao().unmarkRead(verseId)

    suspend fun isRead(verseId: Int): Boolean = appDatabase.readVerseDao().isRead(verseId)

    suspend fun getReadVerseIds(): List<Int> = appDatabase.readVerseDao().getAllReadVerseIds()

    suspend fun countRead(): Int = appDatabase.readVerseDao().countAll()

    suspend fun resetAll() = appDatabase.readVerseDao().clearAll()
}
