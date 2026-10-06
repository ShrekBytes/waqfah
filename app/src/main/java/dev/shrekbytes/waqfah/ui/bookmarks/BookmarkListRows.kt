package dev.shrekbytes.waqfah.ui.bookmarks

import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity

// One surah row of the bookmarks list (see CONTEXT.md's "Bookmarks list"): the
// surah itself, carrying its total ayah count, plus the reader's saved verses in
// that surah in ascending ayah order. savedCount is derived rather than stored
// so the two can never disagree.
data class BookmarkSurahRow(
    val surah: SurahEntity,
    val savedAyahs: List<VerseEntity>,
) {
    val savedCount: Int get() = savedAyahs.size
}

// The bookmarks list's shape, as a pure function over data already fetched
// (#17's Seam 2): group the saved verses by surah, drop the surahs holding
// none, order the surahs by number and the verses within each by ayah number.
//
// Both orderings are imposed here rather than inherited, because the collection
// arrives as one observable set with no ORDER BY — the DAO publishes ids, and
// "Quran order" is this projection's promise, not the store's. A saved verse
// whose surah has no row (an id the Quran text does not know) is dropped rather
// than rendered as a nameless row.
internal fun bookmarkSurahRows(
    surahs: List<SurahEntity>,
    savedVerses: List<VerseEntity>,
): List<BookmarkSurahRow> {
    val savedBySurah = savedVerses.groupBy { it.surahNo }
    return surahs
        .sortedBy { it.surahNo }
        .mapNotNull { surah ->
            val saved = savedBySurah[surah.surahNo] ?: return@mapNotNull null
            BookmarkSurahRow(surah, saved.sortedBy { it.ayahNo })
        }
}
