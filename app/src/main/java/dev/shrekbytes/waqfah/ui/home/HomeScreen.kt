package dev.shrekbytes.waqfah.ui.home

import androidx.compose.runtime.Composable
import dev.shrekbytes.waqfah.ui.reading.ReadingViewModel
import dev.shrekbytes.waqfah.ui.reading.WaqfahReadingContent
import dev.shrekbytes.waqfah.ui.sharing.rememberShareAyahLauncher

@Composable
fun HomeScreen(
    onGoToAyah: () -> Unit,
    viewModel: ReadingViewModel,
) {
    WaqfahReadingContent(
        session = viewModel.session,
        onGoToAyah = onGoToAyah,
        onToggleBookmark = viewModel.session::toggleBookmark,
        onShare = rememberShareAyahLauncher(viewModel.session),
        bottomBar = {},
    )
}
