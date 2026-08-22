package com.shrekbytes.waqfah.data.local.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// version 2: verses.arabic_text split into arabic_indopak/arabic_uthmani —
// bump so Room doesn't try to validate the new asset against the old
// version-1 schema it has cached. fallbackToDestructiveMigration below means
// this is enough on its own; no Migration object needed for a bundled,
// reshipped-whole asset database.
@Database(entities = [SurahEntity::class, VerseEntity::class], version = 2)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun surahDao(): SurahDao
    abstract fun verseDao(): VerseDao

    companion object {
        private const val DB_NAME = "quran_core.db"

        fun build(context: Context): QuranDatabase =
            Room.databaseBuilder(context, QuranDatabase::class.java, DB_NAME)
                .createFromAsset("databases/$DB_NAME")
                // Bundled + shipped fresh with every app update, so a version
                // bump can just reship the asset rather than migrate in place.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
