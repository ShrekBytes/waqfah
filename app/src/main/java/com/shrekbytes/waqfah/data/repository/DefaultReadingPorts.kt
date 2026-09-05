package com.shrekbytes.waqfah.data.repository

import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.TranslationMeta
import com.shrekbytes.waqfah.ui.reading.ReadingPorts
import javax.inject.Inject

// The repositories behind the reading machine's probes (ReadingPorts). One
// line per probe: this file is pure wiring — each fact's owner is the
// repository it forwards to. Verse movement lives in VerseSelection, taken
// directly by the session, so no probe here merely forwards to it. Provided
// in AppModule next to provideMonitorSupervisor, the other
// pure-core-to-repository adapter.
class DefaultReadingPorts @Inject constructor(
    private val quranRepository: QuranRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val translationRepository: TranslationRepository,
    private val settingsRepository: SettingsRepository,
) : ReadingPorts {
    override suspend fun verseById(id: Int) = quranRepository.getVerseById(id)
    override suspend fun surah(surahNo: Int) = quranRepository.getSurah(surahNo)
    override suspend fun totalVerseCount() = quranRepository.totalVerseCount()
    override suspend fun readVerseIds() = readingProgressRepository.getReadVerseIds()
    override suspend fun isRead(verseId: Int) = readingProgressRepository.isRead(verseId)
    override suspend fun markRead(verseId: Int) = readingProgressRepository.markRead(verseId)
    override suspend fun unmarkRead(verseId: Int) = readingProgressRepository.unmarkRead(verseId)
    override suspend fun countRead() = readingProgressRepository.countRead()
    override suspend fun resetAll() = readingProgressRepository.resetAll()
    override suspend fun translationText(meta: TranslationMeta, verseId: Int) = translationRepository.getText(meta, verseId)
    override suspend fun setReadingMode(mode: ReadingMode) = settingsRepository.setReadingMode(mode)
}