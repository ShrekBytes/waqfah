package com.shrekbytes.waqfah.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.ui.components.ChipGroup
import com.shrekbytes.waqfah.ui.components.FieldLabel
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import com.shrekbytes.waqfah.ui.settings.display.ReadingDisplayViewModel
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun OnboardReadingPrefsScreen(
    viewModel: ReadingDisplayViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors

    OnboardingScaffold(
        step = 1,
        title = stringResource(R.string.onboard_prefs_title),
        onBack = onBack,
        bottomContent = {
            WaqfahPrimaryButton(text = stringResource(R.string.continue_btn), onClick = onContinue)
        },
    ) {
        Text(
            stringResource(R.string.preview_label),
            color = colors.inkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        )
        Spacer(Modifier.height(8.dp))
        OnboardingPreviewCard(prefs)
        Spacer(Modifier.height(20.dp))

        FieldLabel(stringResource(R.string.pronunciation_label))
        ChipGroup(
            options = listOf(
                AidLanguage.NONE to stringResource(R.string.aid_none),
                AidLanguage.ENGLISH to stringResource(R.string.aid_english),
                AidLanguage.BENGALI to stringResource(R.string.aid_bengali),
            ),
            selected = prefs.pronunciation,
            onSelect = { lang ->
                viewModel.setPronunciation(lang)
                // Match the surah-name language to the pronunciation choice so
                // onboarding doesn't ask for it twice.
                viewModel.setSurahNameLanguage(
                    when (lang) {
                        AidLanguage.ENGLISH -> NameDisplayLanguage.ENGLISH
                        AidLanguage.BENGALI -> NameDisplayLanguage.BENGALI
                        AidLanguage.NONE -> NameDisplayLanguage.ARABIC
                    },
                )
            },
        )
        Spacer(Modifier.height(16.dp))
        FieldLabel(stringResource(R.string.translation_label))
        ChipGroup(
            options = listOf(
                AidLanguage.NONE to stringResource(R.string.aid_none),
                AidLanguage.ENGLISH to stringResource(R.string.aid_english),
                AidLanguage.BENGALI to stringResource(R.string.aid_bengali),
            ),
            selected = prefs.translationDisplay,
            onSelect = viewModel::setTranslationDisplay,
        )
    }
}

// Fixed sample verse (Al-Ikhlas 112:1) with a constant card height so the
// footprint never moves as fields appear/disappear.
@Composable
private fun OnboardingPreviewCard(prefs: UserPreferences) {
    val colors = WaqfahTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.preview_surah_name), color = colors.inkMuted, fontSize = 11.5.sp)
        Spacer(Modifier.height(10.dp))
        Text("112:1", color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "قُلْ هُوَ اللَّهُ أَحَدٌ",
            color = colors.ink,
            fontSize = 22.sp,
            lineHeight = 34.sp,
            textAlign = TextAlign.Center,
        )

        val translit = when (prefs.pronunciation) {
            AidLanguage.NONE -> null
            AidLanguage.ENGLISH -> "Qul huwa Allāhu aḥad."
            AidLanguage.BENGALI -> "কুল হুওয়াল্লা-হু আহাদ।"
        }
        translit?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = colors.inkMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        }

        val translation = when (prefs.translationDisplay) {
            AidLanguage.NONE -> null
            AidLanguage.ENGLISH -> "Say: He is God, the One."
            AidLanguage.BENGALI -> "বল, তিনি আল্লাহ্‌, এক অদ্বিতীয়।"
        }
        translation?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = colors.inkMuted,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp),
            )
        }
    }
}
