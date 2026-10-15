package dev.shrekbytes.waqfah.ui.about

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.ui.components.ChevronDirection
import dev.shrekbytes.waqfah.ui.components.ChevronIcon
import dev.shrekbytes.waqfah.ui.components.SettingsScaffold
import dev.shrekbytes.waqfah.ui.components.rowHighlight
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme

private data class FaqItem(val questionRes: Int, val answerRes: Int, val image: FaqImage? = null)

// A screenshot of a device's own settings screen, for the answers where the
// menu path is the hard part. Grouped with its caption so a row cannot carry
// an image whose caption went missing.
private data class FaqImage(val res: Int, val captionRes: Int)

private val items = listOf(
    FaqItem(R.string.faq_q1, R.string.faq_a1),
    FaqItem(R.string.faq_q2, R.string.faq_a2),
    FaqItem(R.string.faq_q3, R.string.faq_a3),
    FaqItem(R.string.faq_q4, R.string.faq_a4),
    FaqItem(R.string.faq_q5, R.string.faq_a5),
    FaqItem(R.string.faq_q6, R.string.faq_a6),
    FaqItem(R.string.faq_q7, R.string.faq_a7),
    FaqItem(
        R.string.faq_q8,
        R.string.faq_a8,
        FaqImage(R.drawable.img_faq_miui_bg_windows, R.string.faq_a8_image_caption),
    ),
)

@Composable
fun FaqScreen(onStartTour: () -> Unit, onBack: () -> Unit) {
    val colors = WaqfahTheme.colors

    SettingsScaffold(title = stringResource(R.string.faq_title), onBack = onBack) {
        Text(
            stringResource(R.string.faq_intro),
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(12.dp))
        // The tour's relauncher: a card above the list, not a row in it — the
        // list is questions, and this is an action, so it says who it is for
        // and what it does. Same soft-accent card as the tour's own
        // instruction cards; the whole card is the tap target. The tour's
        // closing copy already points readers here.
        Surface(
            onClick = onStartTour,
            shape = RoundedCornerShape(16.dp),
            color = colors.accentSoft,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Decorative: the title and description carry the meaning.
                Image(
                    painter = painterResource(R.drawable.ic_logo_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.accent),
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.faq_tour_title),
                        color = colors.ink,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.faq_tour_desc),
                        color = colors.ink.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                ChevronIcon(
                    direction = ChevronDirection.RIGHT,
                    tint = colors.accent,
                    modifier = Modifier.padding(start = 10.dp).size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        items.forEachIndexed { index, item ->
            ExpandableRow(item)
            if (index < items.lastIndex) HorizontalDivider(color = colors.line)
        }
        Text(
            stringResource(R.string.faq_still_stuck, SupportInfo.CONTACT_EMAIL),
            color = colors.inkMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )
    }
}

// Flat full-width row with the same press highlight as every other list row in
// the app — no cards or borders.
@Composable
private fun ExpandableRow(item: FaqItem) {
    val colors = WaqfahTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .rowHighlight(onClick = { expanded = !expanded })
                .padding(horizontal = 6.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(item.questionRes),
                color = if (expanded) colors.accent else colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            // Same chevron as the settings rows; rotates to point down while
            // the answer is open.
            ChevronIcon(
                direction = ChevronDirection.RIGHT,
                tint = if (expanded) colors.accent else colors.inkSoft,
                modifier = Modifier.padding(start = 4.dp).size(14.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        // animateContentSize rather than AnimatedVisibility: the latter measures
        // its content as one block and mis-places siblings around a full-width
        // image, so the answer text ended up painted underneath the screenshot.
        // An explicit Column with an animated height lays every child out in
        // order whatever it contains.
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            if (expanded) {
                Text(
                    stringResource(item.answerRes),
                    color = colors.inkMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                )
                item.image?.let { image ->
                    val painter = painterResource(image.res)
                    Image(
                        painter = painter,
                        // The answer above already names the exact setting to
                        // turn on, so the picture carries nothing a screen
                        // reader is missing. The caption below is real text and
                        // reads out.
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 6.dp, end = 6.dp, top = 12.dp)
                            .fillMaxWidth()
                            // A phone screen: its height is whatever its width
                            // works out to — read off the painter rather than
                            // hardcoded, so swapping the image cannot leave the
                            // box the wrong shape.
                            .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height)
                            .clip(RoundedCornerShape(12.dp))
                            // The vendor screens are light; a hairline keeps the
                            // white panel from floating loose on the dark themes.
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
                    )
                    Text(
                        stringResource(image.captionRes),
                        color = colors.inkSoft,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
                    )
                }
                Spacer(Modifier.height(13.dp))
            }
        }
    }
}
