package com.shrekbytes.waqfah.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.AppLanguage
import com.shrekbytes.waqfah.ui.components.ChipGroup
import com.shrekbytes.waqfah.ui.components.FieldLabel
import com.shrekbytes.waqfah.ui.components.ProgressRing
import com.shrekbytes.waqfah.ui.components.SectionTitle
import com.shrekbytes.waqfah.ui.components.SettingsNavRow
import com.shrekbytes.waqfah.ui.components.SettingsToggleRow
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenReadingDisplay: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenDonate: () -> Unit,
    // Called right before the activity is recreated for a language change so
    // the relaunch can land back on the tab the user was on.
    onBeforeLocaleRecreate: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
        Text(
            stringResource(R.string.settings_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = colors.ink,
            modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
        )

        SettingsToggleRow(
            title = if (state.isActive) stringResource(R.string.active_on_title) else stringResource(R.string.active_off_title),
            subtitle = if (state.isActive) {
                stringResource(R.string.active_on_sub)
            } else {
                stringResource(R.string.active_off_sub)
            },
            checked = state.isActive,
            onToggle = viewModel::toggleActive,
        )
        HorizontalDivider(color = colors.line)

        SectionTitle(stringResource(R.string.section_reading))
        SettingsNavRow(stringResource(R.string.reading_display_row), stringResource(R.string.tap_to_customize), onOpenReadingDisplay)

        SectionTitle(stringResource(R.string.section_apps))
        SettingsNavRow(
            stringResource(R.string.manage_apps_row),
            stringResource(R.string.selected_count_fmt, state.monitoredAppCount),
            onOpenApps,
        )

        SectionTitle(stringResource(R.string.section_appearance))
        FieldLabel(stringResource(R.string.theme_label))
        ChipGroup(
            options = AppTheme.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
            selected = state.theme,
            onSelect = viewModel::setTheme,
        )
        Spacer(Modifier.height(16.dp))
        FieldLabel(stringResource(R.string.accent_color_label))
        if (state.showAccentPicker) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AccentColor.entries.forEach { accent ->
                    AccentSwatch(
                        color = accent.swatch,
                        isSelected = accent == state.accentColor,
                        onClick = { viewModel.setAccentColor(accent) },
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.accent_unavailable),
                color = colors.inkMuted,
                fontSize = 13.sp,
            )
        }

        SectionTitle(stringResource(R.string.app_language_label))
        // Resolved here: the ChipGroup's onSelect callback isn't composable.
        val activity = LocalContext.current as? Activity
        ChipGroup(
            options = listOf(
                AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                AppLanguage.ENGLISH to stringResource(R.string.aid_english),
                AppLanguage.BENGALI to stringResource(R.string.aid_bengali),
            ),
            selected = state.appLanguage,
            onSelect = { language ->
                // Recreate only AFTER the write completes — see setAppLanguage —
                // and tell MainActivity which tab to relaunch on.
                viewModel.setAppLanguage(language) {
                    onBeforeLocaleRecreate()
                    activity?.recreate()
                }
            },
        )

        SectionTitle(stringResource(R.string.progress_section))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                ProgressRing(percent = state.progressPercent, modifier = Modifier.size(60.dp))
                Text("${state.progressPercent}%", color = colors.ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("${state.readCount} / ${state.totalCount}", color = colors.ink, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.ayat_read), color = colors.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (showResetConfirm) {
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.reset_question), color = colors.ink, fontSize = 13.5.sp)
                TextButton(
                    onClick = { viewModel.resetProgress(); showResetConfirm = false },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(stringResource(R.string.yes_reset), color = colors.danger, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { showResetConfirm = false }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.cancel), color = colors.inkMuted, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            TextButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(R.string.reset_progress), color = colors.danger, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        SectionTitle(stringResource(R.string.section_permissions))
        SettingsNavRow(stringResource(R.string.app_permissions_row), stringResource(R.string.review_anytime), onOpenPermissions)

        SectionTitle(stringResource(R.string.more_section))
        SettingsNavRow(stringResource(R.string.donate_row), stringResource(R.string.donate_row_desc), onOpenDonate)
        SettingsNavRow(stringResource(R.string.faq_row), stringResource(R.string.faq_row_desc), onOpenFaq)
        SettingsNavRow(stringResource(R.string.about_row), stringResource(R.string.about_row_desc), onOpenAbout)
        Spacer(Modifier.height(24.dp))
    }
}

// Fixed outer size whether selected or not so the row doesn't jitter as the
// selection halo appears/disappears.
@Composable
private fun AccentSwatch(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(if (isSelected) colors.ink else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (isSelected) colors.background else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(color).clickable(onClick = onClick))
        }
    }
}
