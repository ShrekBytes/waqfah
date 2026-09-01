package com.shrekbytes.waqfah.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.reading.ReadingViewModel
import com.shrekbytes.waqfah.ui.reading.WaqfahReadingContent
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun HomeScreen(
    onStartTour: () -> Unit,
    onGoToAyah: () -> Unit = {},
    viewModel: ReadingViewModel = hiltViewModel(),
) {
    Box(Modifier.fillMaxSize()) {
        WaqfahReadingContent(viewModel = viewModel, onGoToAyah = onGoToAyah, bottomBar = {})

        // Subtle tour relauncher: a bare "?" pinned to the top-right, with no
        // chip fill (minimal). Only this screen composes it — TriggerActivity's
        // interstitial never shows a tour entry point.
        val tourLabel = stringResource(R.string.tour_start_button)
        Surface(
            onClick = onStartTour,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 16.dp)
                .semantics { contentDescription = tourLabel },
        ) {
            Text(
                "?",
                color = WaqfahTheme.colors.inkMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                // The Surface announces the tour label; the bare glyph adds
                // nothing for TalkBack.
                modifier = Modifier
                    .padding(horizontal = 9.dp, vertical = 1.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
}
