package dev.shrekbytes.waqfah.data.local.appstate

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "membership_id") val membershipId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_shown_at") val lastShownAt: Long? = null,
    @ColumnInfo(name = "trigger_revision") val triggerRevision: Long = 0L,
)

@Entity(tableName = "read_verses")
data class ReadVerseEntity(
    @PrimaryKey @ColumnInfo(name = "verse_id") val verseId: Int,
    @ColumnInfo(name = "read_at") val readAt: Long,
)

// The bookmark collection (see CONTEXT.md). Its own table, deliberately not a
// column on read_verses and not derived from read state: marking a verse read
// never removes a bookmark, and wiping progress never empties the collection.
@Entity(tableName = "bookmark_verses")
data class BookmarkVerseEntity(
    @PrimaryKey @ColumnInfo(name = "verse_id") val verseId: Int,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)
