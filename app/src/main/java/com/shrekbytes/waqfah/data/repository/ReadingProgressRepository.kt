package com.shrekbytes.waqfah.data.repository

import com.shrekbytes.waqfah.data.local.appstate.ReadVerseEntity
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val appDatabase: WaqfahAppDatabase,
) {
    val readCount: Flow<Int> = appDatabase.readVerseDao().observeReadCount()

    // Bumped on every resetAll() so a ReadingViewModel already showing content
    // (e.g. the Home tab, while the user resets progress from Settings) knows
    // to reload its starting verse instead of silently going stale. Same
    // nudge-a-long-lived-screen pattern as TranslationRepository.downloadedIds.
    private val _progressReset = MutableStateFlow(0)
    val progressReset: StateFlow<Int> = _progressReset.asStateFlow()

    suspend fun markRead(verseId: Int) =
        appDatabase.readVerseDao().markRead(ReadVerseEntity(verseId, readAt = System.currentTimeMillis()))

    suspend fun unmarkRead(verseId: Int) = appDatabase.readVerseDao().unmarkRead(verseId)

    suspend fun isRead(verseId: Int): Boolean = appDatabase.readVerseDao().isRead(verseId)

    suspend fun getReadVerseIds(): List<Int> = appDatabase.readVerseDao().getAllReadVerseIds()

    suspend fun countRead(): Int = appDatabase.readVerseDao().countAll()

    suspend fun resetAll() {
        appDatabase.readVerseDao().clearAll()
        _progressReset.update { it + 1 }
    }
}
