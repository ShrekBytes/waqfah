package com.shrekbytes.waqfah.ui.about

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.shrekbytes.waqfah.BuildConfig
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.SettingsNavRow
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.components.launchExternal
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenGratitude: () -> Unit,
    onOpenDonate: () -> Unit,
) {
    val colors = WaqfahTheme.colors
    val context = LocalContext.current

    SettingsScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_mark),
                contentDescription = stringResource(R.string.waqfah_logo_cd),
                modifier = Modifier.size(72.dp),
                colorFilter = ColorFilter.tint(colors.ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "WAQFAH",
                color = colors.inkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                color = colors.ink,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME), color = colors.inkMuted, fontSize = 12.sp)
        }

        SettingsNavRow(stringResource(R.string.donate_row), stringResource(R.string.donate_row_desc), onOpenDonate)
        SettingsNavRow(stringResource(R.string.gratitude_title), stringResource(R.string.gratitude_row_desc), onOpenGratitude)
        SettingsNavRow(stringResource(R.string.privacy_row), stringResource(R.string.privacy_row_desc), onOpenPrivacyPolicy)

        Spacer(Modifier.height(22.dp))
        SettingsNavRow(
            stringResource(R.string.github_row),
            SupportInfo.REPO_URL.removePrefix("https://"),
            external = true,
            onClick = { context.launchExternal(Intent(Intent.ACTION_VIEW, SupportInfo.REPO_URL.toUri())) },
        )
        SettingsNavRow(
            stringResource(R.string.contact_row),
            SupportInfo.CONTACT_EMAIL,
            external = true,
            onClick = { context.launchExternal(Intent(Intent.ACTION_SENDTO, "mailto:${SupportInfo.CONTACT_EMAIL}".toUri())) },
        )
        Spacer(Modifier.height(16.dp))
    }
}
