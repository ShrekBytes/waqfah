package com.shrekbytes.waqfah.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
        Text(
            "Settings",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = colors.ink,
            modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
        )

        SettingsToggleRow(
            title = if (state.isActive) "Waqfah is active" else "Waqfah is off",
            subtitle = if (state.isActive) {
                "Shows an ayah before your chosen apps"
            } else {
                "It won't appear again until you turn it back on"
            },
            checked = state.isActive,
            onToggle = viewModel::toggleActive,
        )
        HorizontalDivider(color = colors.line)

        SectionTitle("Reading")
        SettingsNavRow("Reading & display", "Tap to customize", onOpenReadingDisplay)

        SectionTitle("Apps")
        SettingsNavRow("Manage monitored apps", "${state.monitoredAppCount} selected", onOpenApps)

        SectionTitle("Appearance")
        FieldLabel("Theme")
        ChipGroup(
            options = AppTheme.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) },
            selected = state.theme,
            onSelect = viewModel::setTheme,
        )
        Spacer(Modifier.height(16.dp))
        FieldLabel("Accent color")
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
                "Accent color isn't available in this theme.",
                color = colors.inkMuted,
                fontSize = 13.sp,
            )
        }

        SectionTitle("Progress")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                ProgressRing(percent = state.progressPercent, modifier = Modifier.size(60.dp))
                Text("${state.progressPercent}%", color = colors.ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("${state.readCount} / ${state.totalCount}", color = colors.ink, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text("ayat read", color = colors.inkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (showResetConfirm) {
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Reset all progress?", color = colors.ink, fontSize = 13.5.sp)
                TextButton(
                    onClick = { viewModel.resetProgress(); showResetConfirm = false },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Yes, reset", color = colors.danger, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { showResetConfirm = false }, contentPadding = PaddingValues(0.dp)) {
                    Text("Cancel", color = colors.inkMuted, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            TextButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("Reset progress", color = colors.danger, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        SectionTitle("Permissions")
        SettingsNavRow("App permissions", "Review anytime", onOpenPermissions)
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
