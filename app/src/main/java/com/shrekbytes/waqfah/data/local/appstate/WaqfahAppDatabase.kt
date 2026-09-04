package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.Database
import androidx.room.RoomDatabase

// Schema IS exported (see app/build.gradle.kts's ksp arg for the output
// directory): this DB holds real user progress and must never get a destructive
// fallback — if it changes, write a Migration by hand, and the exported JSON is
// what lets a Migration test verify it against the real old schema instead of a
// guess.
@Database(entities = [MonitoredAppEntity::class, ReadVerseEntity::class], version = 2, exportSchema = true)
abstract class WaqfahAppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun readVerseDao(): ReadVerseDao
}
