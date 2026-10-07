package dev.shrekbytes.waqfah.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shrekbytes.waqfah.ui.reading.ReadingViewModel
import dev.shrekbytes.waqfah.ui.reading.WaqfahReadingContent
import dev.shrekbytes.waqfah.ui.sharing.rememberShareAyahLauncher

@Composable
fun HomeScreen(
    onGoToAyah: () -> Unit,
    viewModel: ReadingViewModel,
) {
    val controls by viewModel.readingControls.collectAsStateWithLifecycle()
    WaqfahReadingContent(
        session = viewModel.session,
        onGoToAyah = onGoToAyah,
        onToggleBookmark = viewModel.session::toggleBookmark,
        onShare = rememberShareAyahLauncher(viewModel.session),
        controls = controls,
        bottomBar = {},
    )
}
