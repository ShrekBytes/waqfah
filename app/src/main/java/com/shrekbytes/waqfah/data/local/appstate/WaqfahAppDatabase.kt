package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.Database
import androidx.room.RoomDatabase

// No schema export: this DB holds real user progress and must never get a
// destructive fallback — if it ever changes, write a Migration by hand first.
@Database(entities = [MonitoredAppEntity::class, ReadVerseEntity::class], version = 1, exportSchema = false)
abstract class WaqfahAppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun readVerseDao(): ReadVerseDao
}
