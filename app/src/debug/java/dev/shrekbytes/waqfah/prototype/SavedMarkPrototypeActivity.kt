package dev.shrekbytes.waqfah.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shrekbytes.waqfah.data.model.ArabicFont
import dev.shrekbytes.waqfah.ui.components.ChipGroup
import dev.shrekbytes.waqfah.ui.reading.AyahArabicText
import dev.shrekbytes.waqfah.ui.reading.AyahTranslationText
import dev.shrekbytes.waqfah.ui.reading.AyahTranslitText
import dev.shrekbytes.waqfah.ui.reading.NumDivider
import dev.shrekbytes.waqfah.ui.theme.AccentColor
import dev.shrekbytes.waqfah.ui.theme.AppTheme
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme

// Debug-only evaluation screen for the saved-mark decorations (the reader's
// bookmark state drawn on the ayah itself). Renders the reading card's actual
// content column — its composables, fonts, sizes and spacing — under each of
// the three candidate treatments, with live saved/unsaved toggling so the
// transition can be felt in the hand. Merges into debug builds only; delete
// once the treatment is chosen and wired into ReadingCard.
//
// Launch from a connected device:
//   adb shell am start -n dev.shrekbytes.waqfah.fdroid/dev.shrekbytes.waqfah.prototype.SavedMarkPrototypeActivity
class SavedMarkPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var theme by remember { mutableStateOf(AppTheme.LIGHT) }
            WaqfahTheme(theme = theme) {
                SavedMarkPrototypeScreen(
                    selectedTheme = theme,
                    onThemeSelected = { theme = it },
                )
            }
        }
    }
}

private enum class Treatment { KEPT_PAGE, ACCENT_FRAME, CORNER_MARKS }

// Two verses on purpose: a short one and Ayat al-Kursi, so the frame and the
// pen stroke are judged against tall, wrapped content as well as a one-liner.
private data class PrototypeAyah(
    val label: String,
    val arabic: String,
    val translit: String?,
    val translation: String,
)

private val AYAHS = listOf(
    PrototypeAyah(
        label = "112:1",
        arabic = "قُلۡ هُوَ اللّٰهُ اَحَدٌ",
        translit = "Qul huwa Allāhu aḥad",
        translation = "Say: He is Allah, the One.",
    ),
    PrototypeAyah(
        label = "2:255",
        arabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
        translit = null,
        translation = "Allah - there is no deity except Him, the Ever-Living, the Sustainer of [all] existence. Neither drowsiness overtakes Him nor sleep. To Him belongs whatever is in the heavens and whatever is on the earth. Who is it that can intercede with Him except by His permission? He knows what is [presently] before them and what will be after them, and they encompass not a thing of His knowledge except for what He wills. His Kursi extends over the heavens and the earth, and their preservation tires Him not. And He is the Most High, the Most Great.",
    ),
)

private val TREATMENT_TITLES = mapOf(
    Treatment.KEPT_PAGE to "1 · Kept page — hairline frame + star + underline",
    Treatment.ACCENT_FRAME to "2 · Accent frame + star + underline",
    Treatment.CORNER_MARKS to "3 · Corner marks + underline",
)

@Composable
private fun SavedMarkPrototypeScreen(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val colors = WaqfahTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("Saved-mark prototype — debug only", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Toggle saved to feel the transition; swap ayah to check the snap. None of this ever reaches the share image.",
            color = colors.inkMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        ChipGroup(
            options = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.MIDNIGHT)
                .map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selected = selectedTheme,
            onSelect = onThemeSelected,
        )
        Spacer(Modifier.height(8.dp))

        Treatment.entries.forEach { treatment ->
            Spacer(Modifier.height(26.dp))
            Text(TREATMENT_TITLES.getValue(treatment), color = colors.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            TreatmentDemo(treatment)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun TreatmentDemo(treatment: Treatment) {
    var ayahIndex by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(true) }
    val ayah = AYAHS[ayahIndex]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SavedMarkCard(treatment = treatment, ayah = ayah, saved = saved)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrototypeChip("Toggle saved") { saved = !saved }
            PrototypeChip("Swap ayah") { ayahIndex = (ayahIndex + 1) % AYAHS.size }
        }
    }
}

// The reading card's content column — same composables, font, sizes and
// spacing — wrapped in the treatment's decoration. Every decoration reserves
// its geometry in both states, and every animated value follows the card's
// standing rule: snap when the ayah swaps, tween when the reader acts.
@Composable
private fun SavedMarkCard(treatment: Treatment, ayah: PrototypeAyah, saved: Boolean) {
    val colors = WaqfahTheme.colors
    val accent = colors.accent

    // Snap on ayah swap, tween on toggle — the MarkReadPill rule.
    var lastVerseKey by remember { mutableStateOf(ayah.label) }
    var verseJustChanged by remember { mutableStateOf(false) }
    if (ayah.label != lastVerseKey) {
        lastVerseKey = ayah.label
        verseJustChanged = true
    } else {
        verseJustChanged = false
    }
    val alphaSpec: AnimationSpec<Float> = if (verseJustChanged) snap() else tween(160)
    val colorSpec: AnimationSpec<Color> = if (verseJustChanged) snap() else tween(160)

    val markAlpha by animateFloatAsState(if (saved) 1f else 0f, alphaSpec, label = "saved_mark_alpha")
    val frameColor by animateColorAsState(
        when {
            !saved -> Color.Transparent
            treatment == Treatment.ACCENT_FRAME -> colors.accent
            else -> colors.line
        },
        colorSpec,
        label = "saved_mark_frame",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 14.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, frameColor, RoundedCornerShape(14.dp))
                .padding(top = 20.dp, bottom = 16.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NumDivider(ayah.label)
            Spacer(Modifier.height(24.dp))
            AyahArabicText(ayah.arabic, ArabicFont.DIGITAL_KHATT_INDOPAK, 26)
            // The pen stroke under the Arabic.
            Canvas(
                Modifier
                    .fillMaxWidth(0.72f)
                    .padding(top = 4.dp)
                    .height(8.dp)
                    .graphicsLayer(alpha = markAlpha),
            ) {
                drawPenStroke(accent)
            }
            ayah.translit?.let {
                Spacer(Modifier.height(16.dp))
                AyahTranslitText(it, 18)
            }
            Spacer(Modifier.height(20.dp))
            AyahTranslationText(ayah.translation, 18)
        }

        // The star breaks the frame's top rule; its background padding
        // interrupts the border behind it, the way the share image's wordmark
        // interrupts the bottom rule.
        if (treatment != Treatment.CORNER_MARKS) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-8).dp)
                    .background(colors.background)
                    .padding(horizontal = 9.dp),
            ) {
                Canvas(
                    Modifier
                        .width(15.dp)
                        .height(15.dp)
                        .graphicsLayer(alpha = markAlpha),
                ) {
                    drawStar4(accent)
                }
            }
        }

        // Corner marks pin to the panel's four corners for their treatment.
        if (treatment == Treatment.CORNER_MARKS) {
            CornerMark(Modifier.align(Alignment.TopStart), accent)
            CornerMark(Modifier.align(Alignment.TopEnd), accent, mirrored = true)
            CornerMark(Modifier.align(Alignment.BottomStart), accent, flipped = true)
            CornerMark(Modifier.align(Alignment.BottomEnd), accent, mirrored = true, flipped = true)
        }
    }
}

private fun DrawScope.drawStar4(accent: Color) {
    val s = size.width / 24f
    val path = Path().apply {
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
    drawPath(path, accent)
}

private fun DrawScope.drawPenStroke(accent: Color) {
    val sx = size.width / 190f
    val sy = size.height / 7f
    fun x(v: Float) = v * sx
    fun y(v: Float) = v * sy

    val main = Path().apply {
        moveTo(x(2f), y(4f))
        cubicTo(x(30f), y(1.5f), x(55f), y(5.5f), x(88f), y(3.5f))
        cubicTo(x(112f), y(2.2f), x(150f), y(2f), x(188f), y(4.2f))
    }
    drawPath(main, accent, style = Stroke(width = 2.2f * sy, cap = StrokeCap.Round))

    val echo = Path().apply {
        moveTo(x(14f), y(5.6f))
        cubicTo(x(48f), y(4.4f), x(90f), y(6f), x(128f), y(4.6f))
        cubicTo(x(150f), y(3.9f), x(172f), y(4.8f), x(182f), y(5.4f))
    }
    drawPath(echo, accent.copy(alpha = accent.alpha * 0.55f), style = Stroke(width = 1.4f * sy, cap = StrokeCap.Round))
}

// An L-shaped corner stroke, drawn for the top-left orientation and mirrored
// into the others.
@Composable
private fun CornerMark(
    modifier: Modifier,
    accent: Color,
    mirrored: Boolean = false,
    flipped: Boolean = false,
) {
    Canvas(
        modifier
            .width(14.dp)
            .height(14.dp)
            .graphicsLayer(
                scaleX = if (mirrored) -1f else 1f,
                scaleY = if (flipped) -1f else 1f,
            ),
    ) {
        val s = size.width / 14f
        val path = Path().apply {
            moveTo(13f * s, 1f * s)
            lineTo(4f * s, 1f * s)
            quadraticBezierTo(1f * s, 1f * s, 1f * s, 4f * s)
            lineTo(1f * s, 13f * s)
        }
        drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun PrototypeChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = WaqfahTheme.colors.accentSoft,
        contentColor = WaqfahTheme.colors.accent,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
