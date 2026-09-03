package com.shrekbytes.waqfah.data.model

// The translation library: the one place that decides which translations are
// available for a language and which one is active. Pure over the catalog +
// the caller's disk truth (downloadedIds) — see TranslationLibraryTest.
object TranslationLibrary {

    // A catalog entry is usable right now when bundled — its file is created
    // lazily on first use, so statting the disk before that copy would make
    // bundled translations vanish on fresh sessions (the bug that shaped this
    // rule) — or when its file is on disk.
    fun isAvailable(meta: TranslationMeta, downloadedIds: Set<String>): Boolean =
        meta.isBundled || meta.id in downloadedIds

    // Catalog entries usable right now, in catalog order.
    fun available(language: TranslationLanguage, downloadedIds: Set<String>): List<TranslationMeta> =
        TranslationCatalog.all.filter { it.language == language && isAvailable(it, downloadedIds) }

    // The active translation: the stored one when available, otherwise the
    // language's bundled one, so a translation that left the catalog or lost
    // its file never blanks the card. Never null: TranslationCatalogTest pins
    // exactly one bundled translation per language.
    fun resolveActive(language: TranslationLanguage, storedId: String, downloadedIds: Set<String>): TranslationMeta {
        val stored = TranslationCatalog.all.firstOrNull { it.language == language && it.id == storedId }
        return if (stored != null && isAvailable(stored, downloadedIds)) {
            stored
        } else {
            TranslationCatalog.all.first { it.language == language && it.isBundled }
        }
    }
}
