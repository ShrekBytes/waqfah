package com.shrekbytes.waqfah.ui.reading

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton

// Only ever reached via TriggerActivity — see its doc comment.
@Composable
fun ReadingScreen(triggeredPackage: String, viewModel: ReadingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(triggeredPackage) { viewModel.setTriggeredPackage(triggeredPackage) }

    // The target app is still paused underneath; finishing on back-press would
    // fall through to it, which looks like Waqfah opened the app. Go home instead.
    BackHandler {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        (context as? Activity)?.finish()
    }

    ReadingCard(
        state = state,
        onMarkRead = viewModel::markCurrentRead,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onResume = viewModel::resume,
        onCycleTranslation = viewModel::cycleTranslationSource,
        onResetTranslation = viewModel::resetTranslationSource,
        bottomBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
                WaqfahPrimaryButton(
                    text = "Open ${state.triggeredAppLabel ?: "app"} →",
                    onClick = {
                        viewModel.openTriggeredApp(triggeredPackage) {
                            context.packageManager.getLaunchIntentForPackage(triggeredPackage)?.let(context::startActivity)
                            (context as? Activity)?.finish()
                        }
                    },
                )
            }
        },
    )
}
