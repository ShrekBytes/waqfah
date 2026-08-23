package com.shrekbytes.waqfah.ui.theme

import android.app.Activity
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

@Composable
fun WaqfahTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    accentColor: AccentColor = AccentColor.SAGE,
    content: @Composable () -> Unit,
) {
    val colors = resolveColors(theme, isSystemInDarkTheme(), accentColor)

    // enableEdgeToEdge()'s icon-contrast guess only follows system light/dark;
    // set it explicitly from whichever theme background actually resolved.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
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
