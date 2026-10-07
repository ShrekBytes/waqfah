package dev.shrekbytes.waqfah.ui.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.ui.reading.BookmarksViewModel
import dev.shrekbytes.waqfah.ui.reading.WaqfahReadingContent
import dev.shrekbytes.waqfah.ui.sharing.rememberShareAyahLauncher

// The Bookmarks tab's host (see CONTEXT.md's "Bookmarks tab" and "Bookmarks
// card"): the same reading card as Home — same typography, same swipe, same
// Mark Read control, same bookmark toggle, same share control — over the
// reader's saved ayahs, rendered from the tab's own session so the two tabs
// hold independent positions. No tour launcher and no bottom bar: the tab bar
// below it is the whole chrome.
//
// The header is tappable, and opens the Bookmarks list where Home's opens the
// surah picker (ADR-0005). Hence the host-specific label: the header announces
// where it leads, and "Surahs & ayahs" would name the wrong screen here. The
// list is a pushed destination, so this screen only hands up the tap.
//
// It also carries the collection mark — the ribbon tile and the collection
// total — which is what tells this header apart from Home's at a glance. That
// is this host's alone, so it is stated here rather than inferred by the card.
@Composable
fun BookmarksScreen(viewModel: BookmarksViewModel, onOpenList: () -> Unit) {
    val controls by viewModel.readingControls.collectAsStateWithLifecycle()
    WaqfahReadingContent(
        session = viewModel.session,
        onGoToAyah = onOpenList,
        goToAyahLabelRes = R.string.cd_bookmarks_header,
        showCollectionMark = true,
        onToggleBookmark = viewModel.session::toggleBookmark,
        onShare = rememberShareAyahLauncher(viewModel.session),
        controls = controls,
        bottomBar = {},
    )
}
