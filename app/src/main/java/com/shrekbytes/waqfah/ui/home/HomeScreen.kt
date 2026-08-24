package com.shrekbytes.waqfah.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.ui.reading.ReadingCard
import com.shrekbytes.waqfah.ui.reading.ReadingViewModel

@Composable
fun HomeScreen(viewModel: ReadingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReadingCard(
        state = state,
        onMarkRead = viewModel::markCurrentRead,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onResume = viewModel::resume,
        onCycleTranslation = viewModel::cycleTranslationSource,
        onResetTranslation = viewModel::resetTranslationSource,
        onCompletionDismiss = viewModel::dismissCompletion,
        onStartOver = viewModel::startOver,
        onSwitchModeAndRestart = viewModel::switchModeAndRestart,
        bottomBar = {},
    )
}
