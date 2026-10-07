package dev.shrekbytes.waqfah.ui.sharing

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The share image page's arithmetic, in one pure object (ADR-0007): the page's
// own furniture — frame, content padding, ornament, wordmark, scale — lives
// here so ShareImageGeometryTest can pin it as invariants instead of it being
// eyeballed against the mockup. The content column's vertical rhythm is the
// image's own too (it is stated in ShareImagePage.kt): the card compresses its
// own to pay for the saved-mark's frame, and the image has no such constraint
// to pay. What the image does mirror is the content — the shared text
// components carry the card's own sizes, and the palette and the reader's aids
// are the reader's. All values are stated in the card's own units (dp/sp) — the
// page composes at renderDensity, where the card's nominal width turns into the
// image's 1080px.
object ShareImageGeometry {

    // The card's nominal width. The composition's density is set so this
    // renders as the image's fixed 1080px: 360dp x 3 = 1080, whatever the
    // device's real density is, so every size in the image is the card's size
    // times the scale factor — a clean enlargement of what the reader sees.
    const val SCALE = 3f
    val pageWidth: Dp = 360.dp

    fun renderDensity(fontScale: Float): Density = Density(SCALE, fontScale)

    // The frame: a 1dp `line` inset uniformly on all four sides — the one
    // margin the design is never allowed to widen on a single side. Four named
    // values rather than one, so the uniformity invariant has something to
    // catch when a side drifts.
    val frameInsetTop: Dp = 16.dp
    val frameInsetBottom: Dp = 16.dp
    val frameInsetStart: Dp = 16.dp
    val frameInsetEnd: Dp = 16.dp

    // The content sits centred between the rules: identical padding above and
    // below inside the frame, so the page's one asymmetry — the wordmark
    // straddling the bottom rule — is spent deliberately, not accumulated.
    val contentPaddingTop: Dp = 47.dp
    val contentPaddingBottom: Dp = 47.dp

    // The content column's side padding — the image's own page furniture, like
    // the padding above and below. What the card and the image share is the
    // 12dp of bare page between the frame and the content (28dp less the 16dp
    // frameInsetStart here; the card's own 12dp inside its frame at 14dp), not
    // this absolute inset. The card states its own padding in AyahPage.
    val contentPaddingHorizontal: Dp = 28.dp

    // The wordmark's line box (ADR-0007): a single line, centred on the bottom
    // rule, so it straddles the rule and its top edge eats into the lower
    // clearance — the page's one deliberate asymmetry. The explicit line height
    // is what makes the straddle's arithmetic exact.
    val wordmarkLineHeight: TextUnit = 13.sp

    // The stated floor for the gap between the content above and the wordmark's
    // top edge. The design values give ~24.5dp at font scale 1; the floor is
    // what turns a future metric change that walks the signature up into the
    // translation into a failing invariant instead of a shipped collision —
    // the design's own review once caught exactly that.
    val minWordmarkGap: Dp = 20.dp

    // The wordmark's gap above its own top edge: from the content's bottom edge
    // down to the wordmark's top edge, i.e. the lower content padding minus the
    // frame inset the rule sits at, minus the half line height below the rule.
    fun wordmarkClearance(fontScale: Float): Dp = with(renderDensity(fontScale)) {
        contentPaddingBottom - frameInsetBottom - wordmarkLineHeight.toDp() / 2f
    }

    // The image's text sizes are the card's text sizes, stated in the card's
    // own sp so the reader's settings — and their system font scale, which the
    // render density carries — apply unchanged. The scale factor to the image's
    // pixels is the render density's job, not this mapping's.
    fun textSp(cardSp: Float): TextUnit = cardSp.sp

    // The ornament (ADR-0007): an accent four-point star on the divider above
    // the translation, flanked by hairlines. Drawn as a path, never set as a
    // glyph, so it cannot fall back to tofu.
    val ornamentHairlineWidth: Dp = 40.dp
    val ornamentStarSize: Dp = 15.dp
    val ornamentGap: Dp = 9.dp

    // The wordmark's own type: the one element on the page that is not mirrored
    // from the card. Its line box and clearance are above; these are its face.
    val wordmarkFontSize: TextUnit = 11.sp
    val wordmarkLetterSpacing: TextUnit = 1.8.sp
    // How much bare rule shows on either side of the wordmark before the
    // interruption resumes.
    val wordmarkRulePadding: Dp = 11.dp
}
