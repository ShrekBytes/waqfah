package dev.shrekbytes.waqfah.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.data.model.AidLanguage
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage
import dev.shrekbytes.waqfah.ui.components.ChipGroup
import dev.shrekbytes.waqfah.ui.components.FieldLabel
import dev.shrekbytes.waqfah.ui.components.ReadingPreviewCard
import dev.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import dev.shrekbytes.waqfah.ui.settings.display.ReadingDisplayViewModel
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme

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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.preview_label),
                color = colors.inkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            ReadingPreviewCard(prefs)
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
}
