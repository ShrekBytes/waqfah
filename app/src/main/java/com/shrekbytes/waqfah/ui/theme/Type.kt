package com.shrekbytes.waqfah.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO: swap the default (system) font for Inter on UI text, and Hind
// Siliguri for Bengali script specifically — both on Google Fonts, same
// process as the Arabic fonts (see ui/theme/ArabicFonts.kt + SETUP.md).
// This is the base fallback scale; most screens set explicit fontSize/
// fontWeight per element to match the prototype's precise type scale rather
// than relying on these four generic Material roles.
val WaqfahTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp),
)
