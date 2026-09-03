package com.shrekbytes.waqfah.ui.settings.display

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.ArabicFont
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.PreferenceLimits
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.TranslationLibrary
import com.shrekbytes.waqfah.ui.components.ChevronDirection
import com.shrekbytes.waqfah.ui.components.ChevronIcon
import com.shrekbytes.waqfah.ui.components.ChipGroup
import com.shrekbytes.waqfah.ui.components.FieldLabel
import com.shrekbytes.waqfah.ui.components.InlineField
import com.shrekbytes.waqfah.ui.components.ReadingPreviewCard
import com.shrekbytes.waqfah.ui.components.SectionTitle
import com.shrekbytes.waqfah.ui.components.SettingsField
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.components.Stepper
import com.shrekbytes.waqfah.ui.navigation.ReadingDisplaySettings
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import com.shrekbytes.waqfah.ui.theme.displayName
import kotlinx.coroutines.delay

@Composable
fun ReadingDisplayScreen(
    viewModel: ReadingDisplayViewModel = hiltViewModel(),
    onOpenTranslations: (TranslationLanguage) -> Unit,
    onBack: () -> Unit,
    scrollToSection: String? = null,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    val scrollState = rememberScrollState()
    val translationRequester = remember { BringIntoViewRequester() }
    var highlightTitle by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToSection) {
        if (scrollToSection == ReadingDisplaySettings.SECTION_TRANSLATION) {
            // Small delay lets the first frame lay out before scrolling, so the
            // anchor's bringIntoView request lands on a measured scrollable.
            delay(220)
            translationRequester.bringIntoView()
            highlightTitle = true
            delay(1200)
            highlightTitle = false
        }
    }
    val translationTitleColor by animateColorAsState(
        targetValue = if (highlightTitle) colors.accent else colors.inkMuted,
        animationSpec = tween(durationMillis = if (highlightTitle) 220 else 600),
        label = "translation_title_highlight",
    )

    SettingsScaffold(title = stringResource(R.string.display_title), onBack = onBack, scrollState = scrollState) {
        Text(
            stringResource(R.string.preview_label),
            color = colors.inkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ReadingPreviewCard(prefs)
        Spacer(Modifier.height(8.dp))

        SectionTitle(stringResource(R.string.section_reading))
        SettingsField {
            FieldLabel(stringResource(R.string.mode_label))
            ChipGroup(
                options = listOf(ReadingMode.SEQUENTIAL to stringResource(R.string.mode_sequential), ReadingMode.RANDOM to stringResource(R.string.mode_random)),
                selected = prefs.readingMode,
                onSelect = viewModel::setReadingMode,
            )
        }
        SettingsField(showDivider = false) {
            FieldLabel(stringResource(R.string.surah_names_label))
            ChipGroup(
                options = listOf(
                    NameDisplayLanguage.ENGLISH to stringResource(R.string.aid_english),
                    NameDisplayLanguage.BENGALI to stringResource(R.string.aid_bengali),
                    NameDisplayLanguage.ARABIC to stringResource(R.string.section_arabic),
                ),
                selected = prefs.surahNameLanguage,
                onSelect = viewModel::setSurahNameLanguage,
            )
        }

        SectionTitle(stringResource(R.string.section_arabic))
        SettingsField {
            FieldLabel(stringResource(R.string.script_label))
            ChipGroup(
                options = listOf(ArabicScript.INDOPAK to stringResource(R.string.script_indopak), ArabicScript.UTHMANI to stringResource(R.string.script_uthmani)),
                selected = prefs.arabicScript,
                onSelect = viewModel::setArabicScript,
            )
        }
        SettingsField {
            FieldLabel(stringResource(R.string.font_label))
            // Each script has its own font list — only matching fonts show.
            ChipGroup(
                options = ArabicFont.entries
                    .filter { it.script == prefs.arabicScript }
                    .map { it to it.displayName() },
                selected = prefs.arabicFont,
                onSelect = viewModel::setArabicFont,
            )
        }
        InlineField(stringResource(R.string.text_size_label), showDivider = false) {
            Stepper(
                value = prefs.arabicFontSize,
                suffix = "px",
                min = PreferenceLimits.FONT_SIZE_MIN,
                max = PreferenceLimits.FONT_SIZE_MAX,
                onChange = viewModel::setArabicFontSize,
            )
        }

        SectionTitle(stringResource(R.string.pronunciation_label))
        SettingsField {
            FieldLabel(stringResource(R.string.translation_label_display))
            ChipGroup(
                options = listOf(AidLanguage.NONE to stringResource(R.string.aid_none), AidLanguage.ENGLISH to stringResource(R.string.aid_english), AidLanguage.BENGALI to stringResource(R.string.aid_bengali)),
                selected = prefs.pronunciation,
                onSelect = viewModel::setPronunciation,
            )
        }
        InlineField(stringResource(R.string.text_size_label), showDivider = false) {
            Stepper(
                value = prefs.translitFontSize,
                suffix = "px",
                min = PreferenceLimits.FONT_SIZE_MIN,
                max = PreferenceLimits.FONT_SIZE_MAX,
                onChange = viewModel::setTranslitFontSize,
            )
        }

        // Anchor for readinganddisplay#translation — whole section scrolls into view,
        // but only the title text is highlighted (like web :target).
        Column(Modifier.bringIntoViewRequester(translationRequester)) {
            Text(
                stringResource(R.string.translation_label).uppercase(),
                color = translationTitleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
                modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
            )
            SettingsField {
                FieldLabel(stringResource(R.string.translation_label_display))
                ChipGroup(
                    options = listOf(AidLanguage.NONE to stringResource(R.string.aid_none), AidLanguage.ENGLISH to stringResource(R.string.aid_english), AidLanguage.BENGALI to stringResource(R.string.aid_bengali)),
                    selected = prefs.translationDisplay,
                    onSelect = viewModel::setTranslationDisplay,
                )
            }
            InlineField(stringResource(R.string.text_size_label)) {
                Stepper(
                    value = prefs.translationFontSize,
                    suffix = "px",
                    min = PreferenceLimits.FONT_SIZE_MIN,
                    max = PreferenceLimits.FONT_SIZE_MAX,
                    onChange = viewModel::setTranslationFontSize,
                )
            }
            TranslationLinkField(stringResource(R.string.english_translation_row), prefs.activeTranslationEnglish, TranslationLanguage.ENGLISH, downloadedIds, onOpenTranslations)
            TranslationLinkField(
                stringResource(R.string.bengali_translation_row),
                prefs.activeTranslationBengali,
                TranslationLanguage.BENGALI,
                downloadedIds,
                onOpenTranslations,
                showDivider = false,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TranslationLinkField(
    label: String,
    storedId: String,
    language: TranslationLanguage,
    downloadedIds: Set<String>,
    onClick: (TranslationLanguage) -> Unit,
    showDivider: Boolean = true,
) {
    val colors = WaqfahTheme.colors
    val name = TranslationLibrary.resolveActive(language, storedId, downloadedIds).name
    InlineField(label, showDivider = showDivider, onClick = { onClick(language) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = colors.inkMuted, fontSize = 13.sp)
            ChevronIcon(direction = ChevronDirection.RIGHT, tint = colors.inkSoft, modifier = Modifier.size(10.dp))
        }
    }
}
