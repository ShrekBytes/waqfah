package com.shrekbytes.waqfah.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.installedapp.InstalledAppCatalog
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

// HomeScreen and ReadingScreen live in separate Activities (MainActivity and
// TriggerActivity), so each gets its own instance. Everything that must survive
// across them — read status — is persisted in Room, not held in memory.
//
// The reading machine itself lives in ReadingSession; this class is only its
// Android adapter: it hands the session its three signals, exposes the session
// for every verb and state read, and resolves the interstitial's package label.
// Every behavioural question — stepping, rendering, mark-read, completion — is
// answered (and tested) there. The session's probes arrive through
// ReadingPorts, provided by AppModule (DefaultReadingPorts).
@HiltViewModel
class ReadingViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    translationRepository: TranslationRepository,
    readingProgressRepository: ReadingProgressRepository,
    private val installedAppCatalog: InstalledAppCatalog,
    ports: ReadingPorts,
) : ViewModel() {

    val session = ReadingSession(
        // filterNotNull: the session's first emission renders, so nothing
        // renders until prefs are loaded — same wait the cold flow imposed.
        preferences = settingsRepository.loadedPreferences.filterNotNull(),
        downloadedIds = translationRepository.downloadedIds,
        progressReset = readingProgressRepository.progressReset,
        ports = ports,
        scope = viewModelScope,
    )

    // The interstitial's "you opened <app>" caption — label resolution is
    // adapter work (package-manager lookups), the state write is the session's.
    fun setTriggeredPackage(packageName: String?) = viewModelScope.launch {
        val label = packageName?.let { installedAppCatalog.labelFor(it) ?: it }
        session.setTriggeredAppLabel(label)
    }
}
