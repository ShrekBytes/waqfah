package dev.shrekbytes.waqfah

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.fontscaling.FontScaleConverterFactory
import androidx.compose.ui.unit.sp
import dev.shrekbytes.waqfah.ui.sharing.ShareImageGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pins the four layout invariants of the share image's page (ADR-0007, ticket
// "share: the share image, end to end on every reading surface") so the parts
// of the design that are arithmetic are asserted rather than eyeballed. The
// font-scale-sensitive invariants are checked at a system font scale other
// than 1, because a reader's font scale is part of what the image mirrors.
class ShareImageGeometryTest {

    // The card's text roles, in sp, as the card draws them.
    private val cardTextSizes = listOf(13.5f, 12.5f, 26f, 18f)

    @Test
    fun `frame inset is identical on all four sides`() {
        assertEquals(ShareImageGeometry.frameInsetTop, ShareImageGeometry.frameInsetBottom)
        assertEquals(ShareImageGeometry.frameInsetTop, ShareImageGeometry.frameInsetStart)
        assertEquals(ShareImageGeometry.frameInsetTop, ShareImageGeometry.frameInsetEnd)
    }

    @Test
    fun `content padding is identical above and below`() {
        assertEquals(ShareImageGeometry.contentPaddingTop, ShareImageGeometry.contentPaddingBottom)
    }

    @Test
    fun `wordmark keeps a positive gap below the content of at least the stated minimum`() {
        for (fontScale in listOf(1f, 1.3f)) {
            val gap = ShareImageGeometry.wordmarkClearance(fontScale)
            assertTrue(
                "gap $gap at fontScale $fontScale is not positive",
                gap > 0.dp,
            )
            assertTrue(
                "gap $gap at fontScale $fontScale is below the stated minimum ${ShareImageGeometry.minWordmarkGap}",
                gap >= ShareImageGeometry.minWordmarkGap,
            )
        }
    }

    @Test
    fun `every card text size renders at the card's size times the scale factor`() {
        // The card's text sizes, as independent literals: the surah name and
        // ayah label as the card draws them, and the reader's default Arabic,
        // pronunciation and translation sizes as the state carries them.
        //
        // The expected value is the card's own rendering: the size converted by
        // a density-1 Density at the same font scale — including whatever
        // non-linear font-scaling curve that conversion applies — then scaled
        // by the image's scale factor and nothing else. The image must
        // introduce no size mapping of its own.
        for (fontScale in listOf(1f, 1.3f)) {
            val cardDp = with(Density(1f, fontScale)) { cardTextSizes.map { it.sp.toDp().value } }
            with(ShareImageGeometry.renderDensity(fontScale)) {
                for ((cardSp, expectedDp) in cardTextSizes.zip(cardDp)) {
                    assertEquals(
                        "$cardSp sp at fontScale $fontScale",
                        expectedDp * ShareImageGeometry.SCALE,
                        ShareImageGeometry.textSp(cardSp).toPx(),
                        0.01f,
                    )
                }
            }
        }
    }

    @Test
    fun `the render density's text conversion matches the platform's font scaling curve`() {
        // The premise the mirror rests on: a manually-built Density converts sp
        // exactly the way the on-screen card's platform density does — the same
        // non-linear curve, not a linear approximation. Without this, the test
        // above could pass while the image rendered text at sizes the reader
        // never saw.
        for (fontScale in listOf(1f, 1.15f, 1.3f, 1.5f, 2f)) {
            val curve = FontScaleConverterFactory.forScale(fontScale)
            with(Density(1f, fontScale)) {
                for (cardSp in cardTextSizes) {
                    curve?.let {
                        assertEquals(
                            "$cardSp sp at fontScale $fontScale",
                            it.convertSpToDp(cardSp),
                            cardSp.sp.toDp().value,
                            0.001f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `page width composes to the image's 1080px`() {
        with(ShareImageGeometry.renderDensity(1.3f)) {
            assertEquals(1080f, ShareImageGeometry.pageWidth.toPx(), 0.01f)
        }
    }
}
