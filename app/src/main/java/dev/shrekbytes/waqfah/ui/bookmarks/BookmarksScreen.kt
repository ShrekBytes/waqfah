package dev.shrekbytes.waqfah.ui.bookmarks

import androidx.compose.runtime.Composable
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.ui.reading.BookmarksViewModel
import dev.shrekbytes.waqfah.ui.reading.WaqfahReadingContent

// The Bookmarks tab's host (see CONTEXT.md's "Bookmarks tab" and "Bookmarks
// card"): the same reading card as Home — same typography, same swipe, same
// Mark Read control, same bookmark toggle — over the reader's saved ayahs,
// rendered from the tab's own session so the two tabs hold independent
// positions. No tour launcher and no bottom bar: the tab bar below it is the
// whole chrome.
//
// The header is tappable, and opens the Bookmarks list where Home's opens the
// surah picker (ADR-0005). Hence the host-specific label: the header announces
// where it leads, and "Surahs & ayahs" would name the wrong screen here. The
// list is a pushed destination, so this screen only hands up the tap.
@Composable
fun BookmarksScreen(viewModel: BookmarksViewModel, onOpenList: () -> Unit) {
    WaqfahReadingContent(
        session = viewModel.session,
        onGoToAyah = onOpenList,
        goToAyahLabelRes = R.string.cd_bookmarks_header,
        onToggleBookmark = viewModel.session::toggleBookmark,
        bottomBar = {},
    )
}
