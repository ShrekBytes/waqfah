package dev.shrekbytes.waqfah.ui.bookmarks

import androidx.compose.runtime.Composable
import dev.shrekbytes.waqfah.ui.reading.BookmarksViewModel
import dev.shrekbytes.waqfah.ui.reading.WaqfahReadingContent

// The Bookmarks tab's host (see CONTEXT.md's "Bookmarks tab" and "Bookmarks
// card"): the same reading card as Home — same typography, same swipe, same
// Mark Read control, same bookmark toggle — over the reader's saved ayahs,
// rendered from the tab's own session so the two tabs hold independent
// positions. No tour launcher and no bottom bar: the tab bar below it is the
// whole chrome.
//
// The header is left un-tappable for now. On this card it opens the bookmarks
// list, not Home's surah picker, and that list arrives in its own change; until
// then the card names the surah the saved ayah belongs to and no more.
@Composable
fun BookmarksScreen(viewModel: BookmarksViewModel) {
    WaqfahReadingContent(
        session = viewModel.session,
        onToggleBookmark = viewModel.session::toggleBookmark,
        bottomBar = {},
    )
}
