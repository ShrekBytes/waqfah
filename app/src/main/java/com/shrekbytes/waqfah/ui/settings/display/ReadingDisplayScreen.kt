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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.ArabicFont
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
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

    SettingsScaffold(title = "Reading & display", onBack = onBack) {
        SectionTitle("Reading")
        SettingsField {
            FieldLabel("Mode")
            ChipGroup(
                options = listOf(ReadingMode.SEQUENTIAL to "Sequential", ReadingMode.RANDOM to "Random"),
                selected = prefs.readingMode,
                onSelect = viewModel::setReadingMode,
            )
        }
        SettingsField(showDivider = false) {
            FieldLabel("Surah name & numbers")
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

        SectionTitle("Arabic")
        SettingsField {
            FieldLabel("Script")
            ChipGroup(
                options = listOf(ArabicScript.INDOPAK to "IndoPak", ArabicScript.UTHMANI to "Uthmani"),
                selected = prefs.arabicScript,
                onSelect = viewModel::setArabicScript,
            )
        }
        SettingsField {
            FieldLabel("Font")
            // Each script has its own font list — only matching fonts show.
            ChipGroup(
                options = ArabicFont.entries
                    .filter { it.script == prefs.arabicScript }
                    .map { it to it.displayName() },
                selected = prefs.arabicFont,
                onSelect = viewModel::setArabicFont,
            )
        }
        InlineField("Text size", showDivider = false) {
            Stepper(value = prefs.arabicFontSize, suffix = "px", min = 11, max = 33, onChange = viewModel::setArabicFontSize)
        }

        SectionTitle("Pronunciation")
        SettingsField {
            FieldLabel("Display")
            ChipGroup(
                options = listOf(AidLanguage.NONE to "None", AidLanguage.ENGLISH to "English", AidLanguage.BENGALI to "Bengali"),
                selected = prefs.pronunciation,
                onSelect = viewModel::setPronunciation,
            )
        }
        if (prefs.pronunciation != AidLanguage.NONE) {
            InlineField("Text size", showDivider = false) {
                Stepper(value = prefs.translitFontSize, suffix = "px", min = 11, max = 33, onChange = viewModel::setTranslitFontSize)
            }
        }

        SectionTitle("Translation")
        SettingsField {
            FieldLabel("Display")
            ChipGroup(
                options = listOf(AidLanguage.NONE to "None", AidLanguage.ENGLISH to "English", AidLanguage.BENGALI to "Bengali"),
                selected = prefs.translationDisplay,
                onSelect = viewModel::setTranslationDisplay,
            )
        }
        if (prefs.translationDisplay != AidLanguage.NONE) {
            InlineField("Text size") {
                Stepper(value = prefs.translationFontSize, suffix = "px", min = 11, max = 33, onChange = viewModel::setTranslationFontSize)
            }
        }
        TranslationLinkField("English translation", prefs.activeTranslationEnglish, TranslationLanguage.ENGLISH, onOpenTranslations)
        TranslationLinkField(
            "Bengali translation",
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
    val name = TranslationCatalog.all.first { it.language == language && it.id == activeId }.name
    InlineField(label, showDivider = showDivider, onClick = { onClick(language) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = colors.inkMuted, fontSize = 13.sp)
            Text("›", color = colors.inkSoft, fontSize = 14.sp)
        }
    }
}
