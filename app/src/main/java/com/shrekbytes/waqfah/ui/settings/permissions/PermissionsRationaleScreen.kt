package com.shrekbytes.waqfah.ui.settings.permissions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

private data class RationaleBlock(val titleRes: Int, val allowsRes: Int, val neverRes: Int)

private val blocks = listOf(
    RationaleBlock(
        R.string.perm_usage_name,
        R.string.rationale_usage_allows,
        R.string.rationale_usage_never,
    ),
    RationaleBlock(
        R.string.perm_overlay_name,
        R.string.rationale_overlay_allows,
        R.string.rationale_overlay_never,
    ),
    RationaleBlock(
        R.string.perm_battery_name,
        R.string.rationale_battery_allows,
        R.string.rationale_battery_never,
    ),
)

@Composable
fun PermissionsRationaleScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors

    SettingsScaffold(title = stringResource(R.string.rationale_title), onBack = onBack) {
        Text(
            stringResource(R.string.rationale_intro),
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(14.dp))
        blocks.forEach { block ->
            Text(
                stringResource(block.titleRes),
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                stringResource(R.string.rationale_allows_fmt, stringResource(block.allowsRes)),
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                stringResource(R.string.rationale_never_fmt, stringResource(block.neverRes)),
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
