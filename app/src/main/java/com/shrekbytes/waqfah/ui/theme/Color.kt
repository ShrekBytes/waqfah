package com.shrekbytes.waqfah.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.shrekbytes.waqfah.R

// accent / accentSoft / accentInk for one light-or-dark variant of an accent color.
data class AccentVariant(val accent: Color, val soft: Color, val ink: Color)

// Sage's light/dark variants are identical to the Light/Dark base palettes'
// hardcoded accent/accentSoft/accentInk (it's the default accent), but the
// other four each need their own hand-tuned soft/ink — a generic
// background-lerp for "soft" and a luminance-threshold guess for "ink"
// visibly drift from these, especially in Dark theme, so every value below
// is taken directly from the prototype's per-accent, per-mode color table
// rather than derived at runtime. `swatch` is only the color shown for the
// picker button itself (always the light variant, matching the prototype's
// static swatch buttons).
enum class AccentColor(val swatch: Color, val light: AccentVariant, val dark: AccentVariant) {
    SAGE(
        swatch = Color(0xFF71835F),
        light = AccentVariant(accent = Color(0xFF71835F), soft = Color(0xFFE3E8DA), ink = Color(0xFFFBFAF6)),
        dark = AccentVariant(accent = Color(0xFF93A87D), soft = Color(0xFF323A28), ink = Color(0xFF1E1C18)),
    ),
    CLAY(
        swatch = Color(0xFFA6634C),
        light = AccentVariant(accent = Color(0xFFA6634C), soft = Color(0xFFF1E3DC), ink = Color(0xFFFBF7F5)),
        dark = AccentVariant(accent = Color(0xFFC98F79), soft = Color(0xFF3D2C25), ink = Color(0xFF211714)),
    ),
    SLATE(
        swatch = Color(0xFF5E7A93),
        light = AccentVariant(accent = Color(0xFF5E7A93), soft = Color(0xFFDFE7ED), ink = Color(0xFFF7FAFC)),
        dark = AccentVariant(accent = Color(0xFF86A4BE), soft = Color(0xFF25313B), ink = Color(0xFF151B20)),
    ),
    PLUM(
        swatch = Color(0xFF8B6483),
        light = AccentVariant(accent = Color(0xFF8B6483), soft = Color(0xFFEBE0E8), ink = Color(0xFFFAF6F9)),
        dark = AccentVariant(accent = Color(0xFFAC8AA3), soft = Color(0xFF332830), ink = Color(0xFF1C161A)),
    ),
    OCHRE(
        swatch = Color(0xFF9C7936),
        light = AccentVariant(accent = Color(0xFF9C7936), soft = Color(0xFFF0E6CC), ink = Color(0xFFFBF8F0)),
        dark = AccentVariant(accent = Color(0xFFC7A667), soft = Color(0xFF39301E), ink = Color(0xFF1E1A10)),
    ),
}

@Composable
fun AccentColor.displayName(): String = when (this) {
    AccentColor.SAGE -> stringResource(R.string.accent_sage)
    AccentColor.CLAY -> stringResource(R.string.accent_clay)
    AccentColor.SLATE -> stringResource(R.string.accent_slate)
    AccentColor.PLUM -> stringResource(R.string.accent_plum)
    AccentColor.OCHRE -> stringResource(R.string.accent_ochre)
}

// Cream, Stone, Midnight and Indigo ship a fixed accent (no picker) — matches
// the prototype.
internal object BasePalettes {
    val Light = WaqfahColors(
        background = Color(0xFFF6F3EC), ink = Color(0xFF2A2823),
        inkMuted = Color(0xFF8A8275), inkSoft = Color(0xFFB7AF9C),
        line = Color(0xFFE4DFD2), accent = Color(0xFF71835F),
        accentInk = Color(0xFFFBFAF6), accentSoft = Color(0xFFE3E8DA),
        danger = Color(0xFFA15C4B),
    )
    val Dark = WaqfahColors(
        background = Color(0xFF1E1C18), ink = Color(0xFFECE7DA),
        inkMuted = Color(0xFF9C9484), inkSoft = Color(0xFF584F41),
        line = Color(0xFF332E27), accent = Color(0xFF93A87D),
        accentInk = Color(0xFF1E1C18), accentSoft = Color(0xFF323A28),
        danger = Color(0xFFC97A64),
    )
    val Cream = WaqfahColors(
        background = Color(0xFFEAE2CE), ink = Color(0xFF2B2620),
        inkMuted = Color(0xFF6E6344), inkSoft = Color(0xFFBFB59A),
        line = Color(0xFFDDD3B8), accent = Color(0xFFB2543D),
        accentInk = Color(0xFFFBF5F1), accentSoft = Color(0xFFEDD9CE),
        danger = Color(0xFFA15C4B),
    )
    val Stone = WaqfahColors(
        background = Color(0xFFB0BAB0), ink = Color(0xFF363E35),
        inkMuted = Color(0xFF4E574E), inkSoft = Color(0xFF798279),
        line = Color(0xFF9EA79E), accent = Color(0xFF363E35),
        accentInk = Color(0xFFB0BAB0), accentSoft = Color(0xFFA4AEA4),
        danger = Color(0xFF363E35),
    )
    // Near-black, warm-neutral — an OLED-friendly "true dark" option distinct
    // from Dark's warm charcoal. Accent is a soft lamplight gold: warm enough
    // to read as "ink and paper by low light" rather than a cold terminal glow.
    val Midnight = WaqfahColors(
        background = Color(0xFF0C0B09), ink = Color(0xFFECE6D6),
        inkMuted = Color(0xFF8F8674), inkSoft = Color(0xFF433D32),
        line = Color(0xFF241F18), accent = Color(0xFFC9A96B),
        accentInk = Color(0xFF1A1610), accentSoft = Color(0xFF2E2717),
        danger = Color(0xFFC9776B),
    )
    // Deep indigo night sky instead of Midnight's neutral black — same warm
    // ink-on-page contrast, cooler backdrop. A second, genuinely different
    // dark mood rather than a re-tint of Midnight.
    val Indigo = WaqfahColors(
        background = Color(0xFF161A2E), ink = Color(0xFFE8E2D0),
        inkMuted = Color(0xFF8890A8), inkSoft = Color(0xFF3D4358),
        line = Color(0xFF262C42), accent = Color(0xFFD4A15C),
        accentInk = Color(0xFF1C1710), accentSoft = Color(0xFF3A311F),
        danger = Color(0xFFCB7B6C),
    )
}
