package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MonitoredAppEntity::class, ReadVerseEntity::class], version = 1)
abstract class WaqfahAppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun readVerseDao(): ReadVerseDao
}
