package dev.shrekbytes.waqfah.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
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
// bookmarks card renders it in place of the reading card, the bookmarks list
// renders it in place of its rows, and the pause screen renders it when the
// reading mode is Bookmarks and nothing is saved (ADR-0006); all three must say
// the same thing.
//
// Every host that reaches this is a full-height surface — the card fills the
// screen on Home, on the Bookmarks tab and on the pause screen — so the card's
// empty state gets a whole screen's worth of room and needs no trimmed variant.
// One layout serves all three.
//
// The caller supplies the space: the card hands it the height the card body
// would have used, the list the space under its title.
//
// The mark is the reader's own future state — a filled ribbon, which is what a
// saved bookmark looks like — sitting on an accentSoft disc. That disc is the
// only accent inside this block, which is what The One Voice Rule asks of it;
// the screen's other accent is the tab bar's selected pill, which the host
// draws. Nothing else here carries color — the ink ladder does the rest.
@Composable
fun BookmarkEmptyState(modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    Column(
        modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(78.dp).background(colors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BookmarkRibbonIcon(filled = true, tint = colors.accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.bookmarks_empty_title),
            color = colors.ink,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            stringResource(R.string.bookmarks_empty_body),
            color = colors.inkMuted,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        // A short rule separates the invitation from the how-to. Deliberately
        // not the reading card's NumDivider: that flanks a *label* between two
        // 20dp rules, and there is no label here — this is the bare hairline.
        Spacer(Modifier.height(26.dp))
        Box(Modifier.width(40.dp).height(1.dp).background(colors.line))
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.widthIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            EmptyStep(
                label = stringResource(R.string.bookmarks_empty_step1),
                // The long-press alternative rides here rather than as its own
                // line: it toggles the same bookmark the ribbon does, so it
                // belongs to the same step. It is named at all because the ribbon
                // toggle can be hidden in Advanced settings, and then the gesture
                // is the only way in.
                sub = stringResource(R.string.bookmarks_empty_step1_sub),
            ) {
                // The outlined ribbon — the exact control the step names, drawn
                // the way the reader will first see it on the card.
                BookmarkRibbonIcon(filled = false, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
            }
            EmptyStep(
                label = stringResource(R.string.bookmarks_empty_step2),
                sub = stringResource(R.string.bookmarks_empty_step2_sub),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// One how-to line: a glyph in a hairline capsule, the action beside it, and the
// consequence beneath. The chip is a 1dp line ring over a faint line tint — no
// shadow, per The Hairline Rule. The 0.4 alpha sits inside the 0.3-0.6 band
// `line` fills already use elsewhere, and it is one value for every palette:
// DESIGN.md's 12% is the settings preview card's fill specifically, not a cap.
@Composable
private fun EmptyStep(
    label: String,
    sub: String,
    glyph: @Composable () -> Unit,
) {
    val colors = WaqfahTheme.colors
    Row {
        Box(
            Modifier
                .size(34.dp)
                .background(colors.line.copy(alpha = 0.4f), CircleShape)
                .border(1.dp, colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            glyph()
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                label,
                color = colors.ink,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                color = colors.inkMuted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
