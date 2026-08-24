package com.shrekbytes.waqfah.ui.about

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

private data class PolicySection(val titleRes: Int, val bodyRes: Int)

// Static, on-device copy — keep this in sync with any hosted version linked
// from the Play Store listing.
private val sections = listOf(
    PolicySection(R.string.privacy_overview_title, R.string.privacy_overview_body),
    PolicySection(R.string.privacy_local_data_title, R.string.privacy_local_data_body),
    PolicySection(R.string.privacy_usage_title, R.string.privacy_usage_body),
    PolicySection(R.string.privacy_overlay_title, R.string.privacy_overlay_body),
    PolicySection(R.string.privacy_network_title, R.string.privacy_network_body),
    PolicySection(R.string.privacy_changes_title, R.string.privacy_changes_contact),
)

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors

    SettingsScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) {
        Text(
            stringResource(R.string.privacy_short),
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(10.dp))
        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(22.dp))
            Text(
                stringResource(section.titleRes),
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                if (section.bodyRes == R.string.privacy_changes_contact) {
                    stringResource(section.bodyRes, SupportInfo.CONTACT_EMAIL)
                } else {
                    stringResource(section.bodyRes)
                },
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
