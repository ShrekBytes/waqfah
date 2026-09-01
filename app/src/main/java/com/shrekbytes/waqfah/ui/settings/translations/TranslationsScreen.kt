package com.shrekbytes.waqfah.ui.settings.translations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun TranslationsScreen(
    languageCode: String,
    viewModel: TranslationsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    // firstOrNull + fallback guards against a stale route argument ever
    // crashing the screen.
    val language = remember(languageCode) {
        TranslationLanguage.entries.firstOrNull { it.code == languageCode } ?: TranslationLanguage.ENGLISH
    }
    val rows by viewModel.rowsFor(language).collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    val languageName = stringResource(if (language == TranslationLanguage.ENGLISH) R.string.aid_english else R.string.aid_bengali)
    val title = stringResource(R.string.translations_title, languageName)

    SettingsScaffold(title = title, onBack = onBack) {
        Text(
            stringResource(R.string.translations_body),
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
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.meta.name, color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    row.isDownloading -> Text(
                        // Percentage only once a Content-Length is known.
                        row.downloadProgress
                            ?.let { stringResource(R.string.downloading_percent, (it * 100).roundToInt()) }
                            ?: stringResource(R.string.downloading),
                        color = colors.inkMuted.copy(alpha = 0.5f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    row.errorMessage != null -> Text(
                        stringResource(R.string.failed_retry),
                        color = colors.danger,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onDownload),
                    )
                    row.isActive -> Text(stringResource(R.string.active_label), color = colors.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    // Bundled translations can't meaningfully be downloaded or deleted.
                    row.meta.isBundled -> Text(
                        stringResource(R.string.select_label),
                        color = colors.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onSelect),
                    )
                    row.isDownloaded -> {
                        Text(
                            stringResource(R.string.select_label),
                            color = colors.accent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onSelect),
                        )
                        Text(
                            stringResource(R.string.delete_label),
                            color = colors.danger,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onDelete),
                        )
                    }
                    else -> Text(
                        stringResource(R.string.download_label),
                        color = colors.inkMuted,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onDownload),
                    )
                }
            }
        }
        if (row.errorMessage != null) {
            Text(
                row.errorMessage,
                color = colors.danger.copy(alpha = 0.85f),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
