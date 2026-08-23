package com.shrekbytes.waqfah.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.BuildConfig
import com.shrekbytes.waqfah.ui.components.SettingsNavRow
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
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

    SettingsScaffold(title = "About", onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("اقرأ", color = colors.ink, fontSize = 40.sp)
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
                "A quiet pause,\nbefore you continue.",
                color = colors.ink,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", color = colors.inkMuted, fontSize = 12.sp)
        }

        SettingsNavRow("Donate", "Support development", onOpenDonate)
        SettingsNavRow("Gratitude", "People who helped build Waqfah", onOpenGratitude)
        SettingsNavRow("Privacy policy", "What data stays on your device", onOpenPrivacyPolicy)

        Spacer(Modifier.height(22.dp))
        SettingsNavRow(
            "GitHub repository",
            SupportInfo.REPO_URL.removePrefix("https://"),
            external = true,
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportInfo.REPO_URL))) },
        )
        SettingsNavRow(
            "Contact",
            SupportInfo.CONTACT_EMAIL,
            external = true,
            onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${SupportInfo.CONTACT_EMAIL}"))) },
        )
        Spacer(Modifier.height(16.dp))
    }
}
