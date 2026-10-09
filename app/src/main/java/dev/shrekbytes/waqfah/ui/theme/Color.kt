package dev.shrekbytes.waqfah.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.shrekbytes.waqfah.R

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
//
// These are the BASE themes' values. The material palettes below deliberately
// do not reuse `ink` — see the note there.
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

// Stone is the only theme that ships a fixed accent (no picker); everything
// else swaps in one of the five AccentColor variants at resolve time.
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
    val Stone = WaqfahColors(
        background = Color(0xFFB0BAB0), ink = Color(0xFF363E35),
        inkMuted = Color(0xFF4E574E), inkSoft = Color(0xFF798279),
        line = Color(0xFF9EA79E), accent = Color(0xFF363E35),
        accentInk = Color(0xFFB0BAB0), accentSoft = Color(0xFFA4AEA4),
        danger = Color(0xFF363E35),
    )
    // A flat neutral surface rather than an OLED-glossy void: #151515 reads as
    // matte grey-black instead of a switched-off panel, and stays the darkest
    // of the palettes (L* 6.8, below Dark's 10.4). Deliberately untinted — the
    // accent is the only colour on screen, and the picker swaps it the same way
    // it does for Light and Dark (the accent trio below is just Sage's dark
    // variant, replaced at resolve time).
    //
    // The hairline sits *above* the surface (#282828) and is picked to match
    // Dark's hairline contrast almost exactly (1.24 vs 1.26), so 1dp borders and
    // dividers read the same in both. A line at or below the surface value is
    // invisible — that is what #262523 would have been here.
    val MatteBlack = WaqfahColors(
        background = Color(0xFF151515), ink = Color(0xFFE9E7E4),
        inkMuted = Color(0xFF9B9894), inkSoft = Color(0xFF5B5854),
        line = Color(0xFF282828), accent = Color(0xFF93A87D),
        accentInk = Color(0xFF1E1C18), accentSoft = Color(0xFF323A28),
        danger = Color(0xFFC97A64),
    )

    // Material Light/Dark are the only palettes where the accent seeds the
    // *surface* as well as the accent roles, so each of the five accents needs
    // its own full entry rather than the base-palette-plus-swapped-accent shape
    // every other theme uses. That is the whole difference from Light/Dark,
    // whose paper stays warm cream whatever the accent.
    //
    // Every value below is a frozen literal from an offline CIELAB tonal run
    // (tone == L*, the same axis Material's HCT calls tone): surfaces sit at low
    // chroma on the accent's own hue, which is what makes the paper re-tint per
    // accent. Nothing is derived at runtime — see the Hand-Tuned Rule in
    // DESIGN.md. `accent` and `accentSoft` are read from AccentColor rather than
    // restated, so the picker swatch can never drift from what is painted.
    //
    // `accentInk` is the exception, and the reason these themes carry their own
    // copy: it is the text drawn *on* the accent fill (pills, selected chips,
    // the toggle knob, checkmarks), and AccentColor's version of it is
    // near-neutral (chroma ~1-4) because the base themes want plain white-on-
    // accent. These themes admit no neutral, so it is re-derived as the
    // lightest/darkest shade of the accent's own hue that stays readable.
    // Chroma is requested high and gamut-mapped down, so each is as tinted as
    // the colour space allows — a near-white cannot hold much (Clay lands at
    // 4.2), a near-black can (~14).
    val MaterialLight: Map<AccentColor, WaqfahColors> = mapOf(
        AccentColor.SAGE to materialLight(
            AccentColor.SAGE,
            background = 0xFFF5FBEE, ink = 0xFF252920, inkMuted = 0xFF6C7563,
            inkSoft = 0xFFA5AE9B, line = 0xFFD4DDC9, accentInk = 0xFFEEFBE1,
        ),
        AccentColor.CLAY to materialLight(
            AccentColor.CLAY,
            background = 0xFFFFF8F5, ink = 0xFF312521, inkMuted = 0xFF846D65,
            inkSoft = 0xFFBFA69D, line = 0xFFEFD4CC, accentInk = 0xFFFFF4F1,
        ),
        AccentColor.SLATE to materialLight(
            AccentColor.SLATE,
            background = 0xFFF5FAFF, ink = 0xFF202931, inkMuted = 0xFF637484,
            inkSoft = 0xFF9CADBE, line = 0xFFCADCEE, accentInk = 0xFFF0F7FF,
        ),
        AccentColor.PLUM to materialLight(
            AccentColor.PLUM,
            background = 0xFFFFF7FD, ink = 0xFF2F252C, inkMuted = 0xFF7F6D7B,
            inkSoft = 0xFFB9A6B4, line = 0xFFE9D4E4, accentInk = 0xFFFFF3FC,
        ),
        AccentColor.OCHRE to materialLight(
            AccentColor.OCHRE,
            background = 0xFFFFF8EF, ink = 0xFF2D271E, inkMuted = 0xFF7C7060,
            inkSoft = 0xFFB6A998, line = 0xFFE6D8C5, accentInk = 0xFFFFF5E7,
        ),
    )
    val MaterialDark: Map<AccentColor, WaqfahColors> = mapOf(
        AccentColor.SAGE to materialDark(
            AccentColor.SAGE,
            background = 0xFF171B11, ink = 0xFFE1E7DA, inkMuted = 0xFF8B9481,
            inkSoft = 0xFF525A49, line = 0xFF343C2C, accentInk = 0xFF252F1C,
        ),
        AccentColor.CLAY to materialDark(
            AccentColor.CLAY,
            background = 0xFF221712, ink = 0xFFF3E2DC, inkMuted = 0xFFA38C83,
            inkSoft = 0xFF68524B, line = 0xFF48352D, accentInk = 0xFF3D261E,
        ),
        AccentColor.SLATE to materialDark(
            AccentColor.SLATE,
            background = 0xFF111B22, ink = 0xFFDBE7F2, inkMuted = 0xFF8193A3,
            inkSoft = 0xFF485968, line = 0xFF2A3B49, accentInk = 0xFF162E3F,
        ),
        AccentColor.PLUM to materialDark(
            AccentColor.PLUM,
            background = 0xFF20171E, ink = 0xFFEFE1EB, inkMuted = 0xFF9F8B99,
            inkSoft = 0xFF64525F, line = 0xFF453440, accentInk = 0xFF3A2635,
        ),
        AccentColor.OCHRE to materialDark(
            AccentColor.OCHRE,
            background = 0xFF1E190F, ink = 0xFFEDE4D8, inkMuted = 0xFF9B8F7E,
            inkSoft = 0xFF605646, line = 0xFF413829, accentInk = 0xFF352B18,
        ),
    )
}

// The material palettes share their danger voice with the base Light/Dark ones:
// DESIGN.md is explicit that danger is terracotta rather than red, and a
// destructive action tinted to the accent would read as an ordinary accent
// action. It is therefore deliberately not part of the accent-derived surface.
private fun materialLight(
    accent: AccentColor,
    background: Long, ink: Long, inkMuted: Long, inkSoft: Long, line: Long, accentInk: Long,
) = WaqfahColors(
    background = Color(background), ink = Color(ink), inkMuted = Color(inkMuted),
    inkSoft = Color(inkSoft), line = Color(line), accent = accent.light.accent,
    accentInk = Color(accentInk), accentSoft = accent.light.soft,
    danger = Color(0xFFA15C4B),
)

private fun materialDark(
    accent: AccentColor,
    background: Long, ink: Long, inkMuted: Long, inkSoft: Long, line: Long, accentInk: Long,
) = WaqfahColors(
    background = Color(background), ink = Color(ink), inkMuted = Color(inkMuted),
    inkSoft = Color(inkSoft), line = Color(line), accent = accent.dark.accent,
    accentInk = Color(accentInk), accentSoft = accent.dark.soft,
    danger = Color(0xFFC97A64),
)
