package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_shown_at") val lastShownAt: Long? = null,
)

@Entity(tableName = "read_verses")
data class ReadVerseEntity(
    @PrimaryKey @ColumnInfo(name = "verse_id") val verseId: Int,
    @ColumnInfo(name = "read_at") val readAt: Long,
)
