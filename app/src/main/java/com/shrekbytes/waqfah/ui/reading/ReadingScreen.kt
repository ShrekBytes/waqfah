package com.shrekbytes.waqfah.ui.reading

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton

// Shared wiring for ReadingCard's callbacks: both hosts (the Home tab and the
// TriggerActivity interstitial) render the same card and differ only in their
// bottom bar.
@Composable
fun WaqfahReadingContent(viewModel: ReadingViewModel, bottomBar: @Composable () -> Unit) {
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
        bottomBar = bottomBar,
    )
}

// Only ever reached via TriggerActivity — see its doc comment. [onDismissRequest]
// lets the host run its own exit animation before actually finishing; when null,
// dismissal finishes the hosting activity directly.
@Composable
fun ReadingScreen(
    triggeredPackage: String,
    viewModel: ReadingViewModel = hiltViewModel(),
    onDismissRequest: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(triggeredPackage) { viewModel.setTriggeredPackage(triggeredPackage) }

    // The interstitial sits directly on top of the target app's actual task, so
    // finishing falls through to whatever screen was really opened (main UI,
    // share sheet, file viewer) — exactly like a normal back press. No launch
    // intent is needed; getLaunchIntentForPackage would only ever restart the
    // app's main activity.
    fun requestDismiss() {
        onDismissRequest?.invoke() ?: (context as? Activity)?.finish()
    }

    BackHandler(onBack = ::requestDismiss)

    WaqfahReadingContent(viewModel = viewModel) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
            WaqfahPrimaryButton(
                text = stringResource(R.string.open_app_button, state.triggeredAppLabel ?: stringResource(R.string.app_name)),
                // Cooldown bookkeeping happens in AppMonitorService at trigger
                // time; dismissal only reveals the app underneath.
                onClick = ::requestDismiss,
            )
        }
    }
}
