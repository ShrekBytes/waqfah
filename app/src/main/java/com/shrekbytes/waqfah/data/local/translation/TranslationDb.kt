package com.shrekbytes.waqfah.data.local.translation

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey @ColumnInfo(name = "verse_id") val verseId: Int,
    @ColumnInfo(name = "text") val text: String,
)

@Dao
interface TranslationDao {
    @Query("SELECT text FROM translations WHERE verse_id = :verseId LIMIT 1")
    suspend fun getText(verseId: Int): String?
}

// NOTE: keep version in sync with SCHEMA_VERSION below — KSP/Room requires a
// literal here, so the constant can't be referenced directly.
@Database(entities = [TranslationEntity::class], version = 1)
abstract class TranslationDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    companion object {
        // Must equal @Database's version above; download validation checks a
        // file's user_version against this before accepting it.
        const val SCHEMA_VERSION = 1

        fun build(context: Context, dbFile: File): TranslationDatabase =
            Room.databaseBuilder(context, TranslationDatabase::class.java, dbFile.absolutePath)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
