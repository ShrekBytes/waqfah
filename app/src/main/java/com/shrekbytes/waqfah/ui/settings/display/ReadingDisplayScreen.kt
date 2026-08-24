package com.shrekbytes.waqfah.ui.settings.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.ui.components.ChipGroup
import com.shrekbytes.waqfah.ui.components.FieldLabel
import com.shrekbytes.waqfah.ui.components.InlineField
import com.shrekbytes.waqfah.ui.components.SectionTitle
import com.shrekbytes.waqfah.ui.components.SettingsField
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.components.Stepper
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import com.shrekbytes.waqfah.ui.theme.displayName

@Composable
fun ReadingDisplayScreen(
    viewModel: ReadingDisplayViewModel = hiltViewModel(),
    onOpenTranslations: (TranslationLanguage) -> Unit,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.display_title), onBack = onBack) {
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
                    NameDisplayLanguage.ENGLISH to "English",
                    NameDisplayLanguage.BENGALI to "Bengali",
                    NameDisplayLanguage.ARABIC to "Arabic",
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
        if (prefs.pronunciation != AidLanguage.NONE) {
            InlineField(stringResource(R.string.text_size_label), showDivider = false) {
                Stepper(
                    value = prefs.translitFontSize,
                    suffix = "px",
                    min = PreferenceLimits.FONT_SIZE_MIN,
                    max = PreferenceLimits.FONT_SIZE_MAX,
                    onChange = viewModel::setTranslitFontSize,
                )
            }
        }

        SectionTitle(stringResource(R.string.translation_label))
        SettingsField {
            FieldLabel(stringResource(R.string.translation_label_display))
            ChipGroup(
                options = listOf(AidLanguage.NONE to stringResource(R.string.aid_none), AidLanguage.ENGLISH to stringResource(R.string.aid_english), AidLanguage.BENGALI to stringResource(R.string.aid_bengali)),
                selected = prefs.translationDisplay,
                onSelect = viewModel::setTranslationDisplay,
            )
        }
        if (prefs.translationDisplay != AidLanguage.NONE) {
            InlineField(stringResource(R.string.text_size_label)) {
                Stepper(
                    value = prefs.translationFontSize,
                    suffix = "px",
                    min = PreferenceLimits.FONT_SIZE_MIN,
                    max = PreferenceLimits.FONT_SIZE_MAX,
                    onChange = viewModel::setTranslationFontSize,
                )
            }
        }
        TranslationLinkField(stringResource(R.string.english_translation_row), prefs.activeTranslationEnglish, TranslationLanguage.ENGLISH, onOpenTranslations)
        TranslationLinkField(
            stringResource(R.string.bengali_translation_row),
            prefs.activeTranslationBengali,
            TranslationLanguage.BENGALI,
            onOpenTranslations,
            showDivider = false,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TranslationLinkField(
    label: String,
    activeId: String,
    language: TranslationLanguage,
    onClick: (TranslationLanguage) -> Unit,
    showDivider: Boolean = true,
) {
    val colors = WaqfahTheme.colors
    val name = TranslationCatalog.all
        .firstOrNull { it.language == language && it.id == activeId }
        ?.name
        ?: TranslationCatalog.all.first { it.language == language && it.isBundled }.name
    InlineField(label, showDivider = showDivider, onClick = { onClick(language) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = colors.inkMuted, fontSize = 13.sp)
            Text("›", color = colors.inkSoft, fontSize = 14.sp)
        }
    }
}
