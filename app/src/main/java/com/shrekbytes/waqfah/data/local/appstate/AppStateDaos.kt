package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps")
    fun observeAll(): Flow<List<MonitoredAppEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(app: MonitoredAppEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM monitored_apps WHERE package_name = :packageName)")
    suspend fun exists(packageName: String): Boolean

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

    @Query("SELECT COUNT(*) FROM read_verses")
    suspend fun countAll(): Int

    @Upsert
    suspend fun markRead(entity: ReadVerseEntity)

    @Query("DELETE FROM read_verses WHERE verse_id = :verseId")
    suspend fun unmarkRead(verseId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM read_verses WHERE verse_id = :verseId)")
    suspend fun isRead(verseId: Int): Boolean

    @Query("SELECT verse_id FROM read_verses")
    suspend fun getAllReadVerseIds(): List<Int>

    @Query("DELETE FROM read_verses")
    suspend fun clearAll()
}
