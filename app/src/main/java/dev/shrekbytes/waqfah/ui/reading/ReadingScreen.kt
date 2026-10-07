package dev.shrekbytes.waqfah.ui.reading

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import dev.shrekbytes.waqfah.ui.sharing.rememberShareAyahLauncher

// Shared wiring for ReadingCard's callbacks. Every reading surface renders the
// same card — the Home tab, the Bookmarks tab and the TriggerActivity
// interstitial — and they differ only in their bottom bar and in which
// callbacks they pass. onGoToAyah is Home-only (null for the others): the header
// is tappable only where it leads somewhere, and the pause screen has no header
// affordance. onToggleBookmark and onShare are passed by all three; the tour's
// practice card passes neither (see ReadingCard).
//
// Takes the session, not the ViewModel that hosts it: the card is the same
// machine whichever sequence the session walks, so the Bookmarks tab renders
// it from its own session with no second copy of this wiring.
@Composable
fun WaqfahReadingContent(
    session: ReadingSession,
    onGoToAyah: (() -> Unit)? = null,
    // Only consulted when onGoToAyah is non-null: the header's label names the
    // destination, and the destination differs per host.
    @StringRes goToAyahLabelRes: Int = R.string.cd_goto_header,
    onToggleBookmark: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    // The Bookmarks tab's header marks the collection it walks; every other
    // host's describes the surah it is showing. Passed by the host rather than
    // derived from goToAyahLabelRes or the session's sequence — it is a
    // presentation choice, not a fact about either.
    showCollectionMark: Boolean = false,
    // Which action-row controls the host draws: the three reading surfaces
    // pass the reader's Advanced hide-settings; the tour's practice card
    // passes the default and keeps every control it has.
    controls: ReadingControlsVisibility = ReadingControlsVisibility(),
    bottomBar: @Composable () -> Unit,
) {
    val state by session.uiState.collectAsStateWithLifecycle()
    ReadingCard(
        state = state,
        onMarkRead = session::markCurrentRead,
        onNext = session::next,
        onPrevious = session::previous,
        onCycleTranslation = session::cycleTranslationSource,
        onResetTranslation = session::resetTranslationSource,
        onCompletionDismiss = session::dismissCompletion,
        onStartOver = session::startOver,
        onSwitchModeAndRestart = session::switchModeAndRestart,
        onGoToAyah = onGoToAyah,
        goToAyahLabelRes = goToAyahLabelRes,
        showCollectionMark = showCollectionMark,
        onToggleBookmark = onToggleBookmark,
        onShare = onShare,
        controls = controls,
        bottomBar = bottomBar,
    )
}

// Only ever reached via TriggerActivity — see its doc comment. The session is
// the interstitial's own ViewModel rather than ReadingViewModel: this is the one
// surface that follows the reading mode's Bookmarks option, and which sequence a
// host hands its session is settled when the ViewModel is built.
// [onDismissRequest] lets the host run its own exit animation before actually
// finishing; when null, dismissal finishes the hosting activity directly.
@Composable
fun ReadingScreen(
    triggeredPackage: String,
    viewModel: InterstitialReadingViewModel = hiltViewModel(),
    onDismissRequest: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val state by viewModel.session.uiState.collectAsStateWithLifecycle()
    val controls by viewModel.readingControls.collectAsStateWithLifecycle()

    LaunchedEffect(triggeredPackage) { viewModel.setTriggeredPackage(triggeredPackage) }

    // The interstitial sits directly on top of the monitored app's actual
    // task, so finishing falls through to whatever screen was really opened
    // (main UI, share sheet, file viewer) — exactly like a normal back press.
    // No launch intent is needed; getLaunchIntentForPackage would only ever
    // restart the app's main activity.
    fun requestDismiss() {
        onDismissRequest?.invoke() ?: (context as? Activity)?.finish()
    }

    BackHandler(onBack = ::requestDismiss)

    // The pause screen's session is its own (a separate Activity), so the save
    // control here reads and writes the same shared collection the Home card
    // does — a save made over a monitored app shows on Home with no refresh.
    WaqfahReadingContent(
        session = viewModel.session,
        onGoToAyah = null,
        onToggleBookmark = viewModel.session::toggleBookmark,
        onShare = rememberShareAyahLauncher(viewModel.session),
        controls = controls,
    ) {
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
