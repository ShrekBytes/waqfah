package com.shrekbytes.waqfah.data.local.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SurahEntity::class, VerseEntity::class], version = 2)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun surahDao(): SurahDao
    abstract fun verseDao(): VerseDao

    companion object {
        private const val DB_NAME = "quran_core.db"

        fun build(context: Context): QuranDatabase =
            Room.databaseBuilder(context, QuranDatabase::class.java, DB_NAME)
                .createFromAsset("databases/$DB_NAME")
                // Bundled + reshipped whole with every app update, so a schema
                // change just bumps the version; no Migration objects needed.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
