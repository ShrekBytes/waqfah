package com.shrekbytes.waqfah.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppTheme { SYSTEM, LIGHT, DARK, CREAM, RETRO, STONE }

// CREAM/RETRO/STONE ship a hand-tuned fixed accent (see BasePalettes), so the
// accent picker only makes sense for the three base themes.
val AppTheme.hasAccentPicker: Boolean
    get() = this == AppTheme.SYSTEM || this == AppTheme.LIGHT || this == AppTheme.DARK

data class WaqfahColors(
    val background: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkSoft: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val danger: Color,
)

private fun WaqfahColors.withAccent(accentColor: AccentColor, isDark: Boolean): WaqfahColors {
    val variant = if (isDark) accentColor.dark else accentColor.light
    return copy(accent = variant.accent, accentInk = variant.ink, accentSoft = variant.soft)
}

private fun resolveColors(theme: AppTheme, isSystemDark: Boolean, accentColor: AccentColor): WaqfahColors =
    when (theme) {
        AppTheme.SYSTEM -> if (isSystemDark) BasePalettes.Dark.withAccent(accentColor, isDark = true)
        else BasePalettes.Light.withAccent(accentColor, isDark = false)
        AppTheme.LIGHT -> BasePalettes.Light.withAccent(accentColor, isDark = false)
        AppTheme.DARK -> BasePalettes.Dark.withAccent(accentColor, isDark = true)
        AppTheme.CREAM -> BasePalettes.Cream
        AppTheme.RETRO -> BasePalettes.Retro
        AppTheme.STONE -> BasePalettes.Stone
    }

val LocalWaqfahColors = staticCompositionLocalOf { BasePalettes.Light }

// Recursively unwraps ContextWrapper chains down to the hosting Activity.
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun WaqfahTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    accentColor: AccentColor = AccentColor.SAGE,
    content: @Composable () -> Unit,
) {
    val colors = resolveColors(theme, isSystemInDarkTheme(), accentColor)

    // NOTE: app language is NOT applied here. Overriding LocalContext with a
    // configuration-wrapped context breaks hiltViewModel(), which needs a real
    // Activity context. Locale is applied by AppCompatDelegate instead:
    // SettingsViewModel.setAppLanguage calls setApplicationLocales, which every
    // AppCompatActivity (both hosts of this theme) picks up automatically.

    // enableEdgeToEdge()'s icon-contrast guess only follows system light/dark;
    // set it explicitly from whichever theme background actually resolved.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Both current hosts attach themselves as the ComposeView context,
            // but unwrap defensively anyway: an OEM LayoutInflater wrapper (or
            // a future non-activity host) must skip bar tinting, not crash
            // every theme application.
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val useLightIcons = colors.background.luminance() > 0.5f
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = useLightIcons
            controller.isAppearanceLightNavigationBars = useLightIcons
        }
    }

    CompositionLocalProvider(LocalWaqfahColors provides colors) {
        MaterialTheme(typography = WaqfahTypography) {
            // Surface paints edge-to-edge; only the inner Box gets safe-drawing
            // insets so content clears the bars without exposing an unthemed gap.
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
                Box(Modifier.safeDrawingPadding()) {
                    content()
                }
            }
        }
    }
}

object WaqfahTheme {
    val colors: WaqfahColors
        @Composable get() = LocalWaqfahColors.current
}
