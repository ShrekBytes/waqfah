package com.shrekbytes.waqfah.ui.settings.translations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun TranslationsScreen(
    languageCode: String,
    viewModel: TranslationsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val language = remember(languageCode) { TranslationLanguage.entries.first { it.code == languageCode } }
    val rows by viewModel.rowsFor(language).collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    val title = if (language == TranslationLanguage.ENGLISH) "English translations" else "Bengali translations"

    SettingsScaffold(title = title, onBack = onBack) {
        Text(
            "One is active at a time. Download others to switch, or remove ones you don't need.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(14.dp))
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = colors.line)
            TranslationRow(
                row = row,
                onSelect = { viewModel.select(row.meta) },
                onDownload = { viewModel.download(row.meta) },
                onDelete = { viewModel.delete(row.meta) },
            )
        }
    }
}

@Composable
private fun TranslationRow(
    row: TranslationRowState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = WaqfahTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.meta.name, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                row.isDownloading -> Text(
                    "Downloading…",
                    color = colors.inkMuted.copy(alpha = 0.5f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                row.isActive -> Text("✓ Active", color = colors.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                row.isDownloaded -> {
                    Text(
                        "Select",
                        color = colors.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onSelect),
                    )
                    // Deletable even when bundled — it's shipped in the APK, so
                    // "downloading" it again afterward is a local copy, not a
                    // network fetch. See TranslationRepository.download().
                    Text(
                        "Delete",
                        color = colors.danger,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onDelete),
                    )
                }
                // No ".tr-btn.download" override in the prototype, so this
                // stays the base .tr-btn color (ink-muted), not accent.
                else -> Text(
                    "Download",
                    color = colors.inkMuted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDownload),
                )
            }
        }
    }
}
