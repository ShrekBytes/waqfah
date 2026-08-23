package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps")
    fun observeAll(): Flow<List<MonitoredAppEntity>>

    @Upsert
    suspend fun upsert(app: MonitoredAppEntity)

    @Query("DELETE FROM monitored_apps WHERE package_name = :packageName")
    suspend fun remove(packageName: String)

    @Query("UPDATE monitored_apps SET last_shown_at = :timestamp WHERE package_name = :packageName")
    suspend fun updateLastShown(packageName: String, timestamp: Long)

    @Query("SELECT last_shown_at FROM monitored_apps WHERE package_name = :packageName")
    suspend fun getLastShown(packageName: String): Long?
}

@Dao
interface ReadVerseDao {
    @Query("SELECT COUNT(*) FROM read_verses")
    fun observeReadCount(): Flow<Int>

    @Upsert
    suspend fun markRead(entity: ReadVerseEntity)

    @Query("DELETE FROM read_verses WHERE verse_id = :verseId")
    suspend fun unmarkRead(verseId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM read_verses WHERE verse_id = :verseId)")
    suspend fun isRead(verseId: Int): Boolean

    @Query("DELETE FROM read_verses")
    suspend fun clearAll()
}
