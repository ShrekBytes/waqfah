package com.shrekbytes.waqfah.ui.reading

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.data.model.ArabicFont
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import com.shrekbytes.waqfah.ui.theme.toFontFamily

// Shared ayah text styles: one definition keeps every rendering of an ayah —
// the live reading card, its left/right peek pages, and the settings-screen
// preview — visually identical by construction instead of by copy-paste.

@Composable
fun AyahArabicText(text: String, font: ArabicFont, fontSize: Int, modifier: Modifier = Modifier) {
    Text(
        text,
        color = WaqfahTheme.colors.ink,
        textAlign = TextAlign.Center,
        fontFamily = font.toFontFamily(),
        fontSize = fontSize.sp,
        // Extra room so Arabic diacritics aren't clipped.
        lineHeight = (fontSize * 2f).sp,
        modifier = modifier,
    )
}

@Composable
fun AyahTranslitText(text: String, fontSize: Int, modifier: Modifier = Modifier) {
    Text(
        text,
        color = WaqfahTheme.colors.inkMuted,
        textAlign = TextAlign.Center,
        fontSize = fontSize.sp,
        fontStyle = FontStyle.Italic,
        lineHeight = (fontSize * 1.7f).sp,
        modifier = modifier.widthIn(max = 280.dp),
    )
}

@Composable
fun AyahTranslationText(text: String, fontSize: Int, modifier: Modifier = Modifier) {
    Text(
        text,
        color = WaqfahTheme.colors.inkMuted,
        textAlign = TextAlign.Center,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.7f).sp,
        modifier = modifier.widthIn(max = 280.dp),
    )
}
