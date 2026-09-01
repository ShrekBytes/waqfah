package com.shrekbytes.waqfah.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.ui.reading.AyahArabicText
import com.shrekbytes.waqfah.ui.reading.AyahTranslationText
import com.shrekbytes.waqfah.ui.reading.AyahTranslitText
import com.shrekbytes.waqfah.ui.reading.ayahWord
import com.shrekbytes.waqfah.ui.reading.localizeDigits
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun ReadingPreviewCard(prefs: UserPreferences, modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    val surahName = when (prefs.surahNameLanguage) {
        NameDisplayLanguage.ENGLISH -> "Al-Ikhlas"
        NameDisplayLanguage.BENGALI -> "আল-ইখলাস"
        NameDisplayLanguage.ARABIC -> "الإخلاص"
    }
    val totalLabel = "${localizeDigits(4, prefs.surahNameLanguage)} ${ayahWord(prefs.surahNameLanguage)}"
    val ayahLabel = "${localizeDigits(112, prefs.surahNameLanguage)}:${localizeDigits(1, prefs.surahNameLanguage)}"

    val arabicText = when (prefs.arabicScript) {
        ArabicScript.INDOPAK -> "قُلۡ هُوَ اللّٰهُ اَحَدٌ"
        ArabicScript.UTHMANI -> "قُلۡ هُوَ ٱللَّهُ أَحَدٌ"
    }
    val translit = when (prefs.pronunciation) {
        AidLanguage.NONE -> null
        AidLanguage.ENGLISH -> "Qul huwa Allāhu aḥad."
        AidLanguage.BENGALI -> "কুল হুওয়াল্লা-হু আহাদ।"
    }
    val translation = when (prefs.translationDisplay) {
        AidLanguage.NONE -> null
        AidLanguage.ENGLISH -> "Say: He is God, the One."
        AidLanguage.BENGALI -> "বল, তিনি আল্লাহ্‌, এক অদ্বিতীয়।"
    }

    val surahDirection = if (prefs.surahNameLanguage == NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 270.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .background(colors.line.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides surahDirection) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    surahName,
                    color = colors.ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    totalLabel,
                    color = colors.inkMuted,
                    fontSize = 11.5.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HorizontalDivider(modifier = Modifier.width(18.dp), color = colors.line)
            Text(
                ayahLabel,
                color = colors.inkMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.25.sp,
            )
            HorizontalDivider(modifier = Modifier.width(18.dp), color = colors.line)
        }
        Spacer(Modifier.height(10.dp))
        AyahArabicText(arabicText, prefs.arabicFont, prefs.arabicFontSize)
        if (translit != null) {
            Spacer(Modifier.height(8.dp))
            AyahTranslitText(translit, prefs.translitFontSize)
        }
        if (translation != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(modifier = Modifier.width(28.dp), color = colors.line)
            Spacer(Modifier.height(10.dp))
            AyahTranslationText(translation, prefs.translationFontSize)
        }
    }
}
