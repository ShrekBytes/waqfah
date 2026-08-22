package com.shrekbytes.waqfah.data.local.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "surah_no") val surahNo: Int,
    @ColumnInfo(name = "name_arabic") val nameArabic: String?,
    @ColumnInfo(name = "name_english") val nameEnglish: String?,
    @ColumnInfo(name = "name_bengali") val nameBengali: String?,
    @ColumnInfo(name = "ayah_count") val ayahCount: Int,
)

// Matches the updated quran_core.db schema: arabic_text was split into two
// separate script columns so the Reading screen can switch between them
// (see ArabicScript + ReadingViewModel.render()) without needing a second
// database or table.
@Entity(tableName = "verses", indices = [Index(value = ["surah_no", "ayah_no"], unique = true)])
data class VerseEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "surah_no") val surahNo: Int,
    @ColumnInfo(name = "ayah_no") val ayahNo: Int,
    @ColumnInfo(name = "arabic_indopak") val arabicIndopak: String,
    @ColumnInfo(name = "arabic_uthmani") val arabicUthmani: String,
    @ColumnInfo(name = "bn_transliteration") val bnTransliteration: String,
    @ColumnInfo(name = "en_transliteration") val enTransliteration: String,
)
