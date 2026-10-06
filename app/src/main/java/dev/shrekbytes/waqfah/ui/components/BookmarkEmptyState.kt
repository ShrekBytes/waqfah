package dev.shrekbytes.waqfah.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme

// The "nothing saved yet" state: the invitation to start a collection
// (ADR-0005), not an error and not a blank surface. The copy is the only
// definition of what a bookmark is, so it is shared rather than restated — the
// bookmarks card renders it in place of the reading card, and the bookmarks
// list renders it in place of its rows, and both must say the same thing. The
// ribbon shows the control the message asks the reader to look for.
//
// The caller supplies the space: the card hands it the height the card body
// would have used, the list the space under its title.
@Composable
fun BookmarkEmptyState(modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    Column(
        modifier.padding(horizontal = 44.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BookmarkRibbonIcon(filled = false, tint = colors.inkSoft, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.bookmarks_empty_title),
            color = colors.ink,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.bookmarks_empty_body),
            color = colors.inkMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}
