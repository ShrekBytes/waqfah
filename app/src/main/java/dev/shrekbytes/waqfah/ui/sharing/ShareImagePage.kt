package dev.shrekbytes.waqfah.ui.sharing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.shrekbytes.waqfah.ui.reading.AyahArabicText
import dev.shrekbytes.waqfah.ui.reading.AyahTranslationText
import dev.shrekbytes.waqfah.ui.reading.AyahTranslitText
import dev.shrekbytes.waqfah.ui.reading.NumDivider
import dev.shrekbytes.waqfah.ui.reading.ReadingUiState
import dev.shrekbytes.waqfah.ui.theme.WaqfahColors
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme

// The wordmark: the one element on the page that is not mirrored from the card
// and the one that does not follow the reader's language — a Latin signature,
// constant in every locale (see the Bengali case in the mockup). A resource
// would be localized; a constant is the point.
private const val WORDMARK = "WAQFAH"

// The share image page (ADR-0007, see CONTEXT.md's "Share image"): the ayah
// exactly as the reading card renders it — same shared text components, same
// sizes, same palette — on a designed page the card does not have: a 1dp frame
// inset uniformly on all four sides, an accent star on the divider above the
// translation, and the wordmark centred on the bottom rule with the rule
// interrupted behind it. The surah's ayah count and every interactive control
// are left out by not being here.
//
// The page's metrics come from ShareImageGeometry so the arithmetic is pinned
// by ShareImageGeometryTest; the text sizes come straight from the reading
// state, unchanged — the composition's render density turns them into the
// image's pixels. Width is the card's nominal 360dp (1080px composed); height
// follows the content.
@Composable
fun ShareImagePage(state: ReadingUiState, modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    val geometry = ShareImageGeometry

    Box(modifier.width(geometry.pageWidth).background(colors.background)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = geometry.contentPaddingHorizontal,
                    vertical = geometry.contentPaddingTop, // == bottom (pinned invariant)
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The header's surah name, mirroring the card's own rendering —
            // including the header's layout direction, which follows the
            // name's script rather than the app's locale.
            CompositionLocalProvider(LocalLayoutDirection provides state.surahNameDirection) {
                Text(
                    state.surahName,
                    color = colors.ink,
                    fontSize = geometry.textSp(13.5f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(14.dp))
            NumDivider(state.ayahLabel)
            Spacer(Modifier.height(24.dp))
            AyahArabicText(state.arabicText, state.arabicFont, state.arabicFontSize)
            state.translitText?.let {
                Spacer(Modifier.height(20.dp))
                AyahTranslitText(it, state.translitFontSize)
            }
            state.translationText?.let { translationText ->
                // The card's 32dp hairline above the translation, promoted to
                // the page's ornament: hairlines flanking the accent star.
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(geometry.ornamentGap),
                ) {
                    OrnamentHairline(colors.line)
                    StarOrnament(colors.accent)
                    OrnamentHairline(colors.line)
                }
                Spacer(Modifier.height(24.dp))
                AyahTranslationText(translationText, state.translationFontSize)
            }
        }

        PageFrameAndWordmark(Modifier.matchParentSize(), colors)
    }
}

@Composable
private fun OrnamentHairline(line: Color) {
    Canvas(
        Modifier
            .width(ShareImageGeometry.ornamentHairlineWidth)
            .height(1.dp),
    ) {
        drawLine(
            color = line,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

// The accent four-point star, drawn as a path (ADR-0007) so it cannot fall
// back to tofu on a device without the character. The path is the mockup's
// 24-unit symbol scaled to the ornament's size.
@Composable
private fun StarOrnament(accent: Color) {
    Canvas(Modifier.size(ShareImageGeometry.ornamentStarSize)) {
        val s = size.width / 24f
        val star = Path().apply {
            moveTo(12f * s, 1.6f * s)
            lineTo(14.3f * s, 9.7f * s)
            lineTo(22.4f * s, 12f * s)
            lineTo(14.3f * s, 14.3f * s)
            lineTo(12f * s, 22.4f * s)
            lineTo(9.7f * s, 14.3f * s)
            lineTo(1.6f * s, 12f * s)
            lineTo(9.7f * s, 9.7f * s)
            close()
        }
        drawPath(star, accent)
    }
}

// The page's frame and wordmark, drawn over the content: three full rules plus
// the bottom rule split around the wordmark, which is centred ON the rule —
// straddling it, its top edge eating into the lower clearance (the page's one
// deliberate asymmetry). The interruption is the segments skipping the
// wordmark's own width plus padding, so nothing shows behind the glyphs.
@Composable
private fun PageFrameAndWordmark(modifier: Modifier, colors: WaqfahColors) {
    val geometry = ShareImageGeometry
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier) {
        val stroke = 1.dp.toPx()
        // All four insets are one value by pinned invariant; read one of them.
        val inset = geometry.frameInsetTop.toPx()
        val w = size.width
        val h = size.height
        val ruleY = h - geometry.frameInsetBottom.toPx()

        drawLine(colors.line, Offset(inset, inset), Offset(w - inset, inset), stroke)
        drawLine(colors.line, Offset(inset, inset), Offset(inset, h - inset), stroke)
        drawLine(colors.line, Offset(w - inset, inset), Offset(w - inset, h - inset), stroke)

        // Measured in this canvas's density (the render density), so the
        // wordmark's size and its interruption agree with the page's scale.
        val wordmark = textMeasurer.measure(
            WORDMARK,
            TextStyle(
                fontSize = geometry.wordmarkFontSize,
                letterSpacing = geometry.wordmarkLetterSpacing,
                fontWeight = FontWeight.Medium,
                color = colors.inkSoft.copy(alpha = 0.55f),
            ),
        )
        val centerX = w / 2f
        val halfGap = wordmark.size.width / 2f + geometry.wordmarkRulePadding.toPx()
        drawLine(colors.line, Offset(inset, ruleY), Offset(centerX - halfGap, ruleY), stroke)
        drawLine(colors.line, Offset(centerX + halfGap, ruleY), Offset(w - inset, ruleY), stroke)
        drawText(
            wordmark,
            topLeft = Offset(centerX - wordmark.size.width / 2f, ruleY - wordmark.size.height / 2f),
        )
    }
}
