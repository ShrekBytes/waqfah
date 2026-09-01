package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.Database
import androidx.room.RoomDatabase

// Schema IS exported (see app/build.gradle.kts's ksp arg for the output
// directory) even though version is still 1: this DB holds real user
// progress and must never get a destructive fallback — if it ever changes,
// write a Migration by hand, and the exported JSON is what lets a Migration
// test actually verify it against the real old schema instead of a guess.
@Database(entities = [MonitoredAppEntity::class, ReadVerseEntity::class], version = 1, exportSchema = true)
abstract class WaqfahAppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun readVerseDao(): ReadVerseDao
}
