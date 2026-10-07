package dev.shrekbytes.waqfah.ui.home

import androidx.compose.runtime.Composable
import dev.shrekbytes.waqfah.ui.reading.ReadingViewModel
import dev.shrekbytes.waqfah.ui.reading.WaqfahReadingContent

// Home's top-right corner belongs to the ayah, not the app chrome: the tour's
// relauncher lives on FAQ & troubleshooting instead.
@Composable
fun HomeScreen(
    onGoToAyah: () -> Unit,
    viewModel: ReadingViewModel,
) {
    WaqfahReadingContent(
        session = viewModel.session,
        onGoToAyah = onGoToAyah,
        onToggleBookmark = viewModel.session::toggleBookmark,
        bottomBar = {},
    )
}
