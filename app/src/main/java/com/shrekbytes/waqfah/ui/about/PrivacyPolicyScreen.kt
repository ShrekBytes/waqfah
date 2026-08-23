package com.shrekbytes.waqfah.ui.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

private data class PolicySection(val title: String, val body: String)

// Static, on-device copy — keep this in sync with any hosted version linked
// from the Play Store listing.
private val sections = listOf(
    PolicySection(
        "Overview",
        "Waqfah shows you a Quran verse before the apps you choose open. It is built to work " +
            "entirely on your device. We do not collect, store, or share any personal data — there " +
            "are no accounts, no analytics, no advertising, and no tracking of any kind.",
    ),
    PolicySection(
        "Data kept on your device",
        "Everything Waqfah knows lives in its own local storage on your phone: which apps you " +
            "chose to monitor, your reading progress, your display preferences, and your translation " +
            "downloads. Deleting the app deletes all of it. None of it ever leaves your device.",
    ),
    PolicySection(
        "Usage access permission",
        "Android's usage access lets an app ask the system which app is currently in the " +
            "foreground. Waqfah uses this for one purpose only: noticing when one of your selected " +
            "apps opens so the reading screen can appear first. It never reads anything inside those " +
            "apps, never records your browsing or app history anywhere, and never sends what it sees " +
            "off the device.",
    ),
    PolicySection(
        "Display over other apps",
        "This permission lets Waqfah's reading screen appear while another app is opening. It " +
            "is used only for that reading screen, only when you trigger a monitored app, and can be " +
            "revoked at any time from your device settings.",
    ),
    PolicySection(
        "Network use",
        "Waqfah connects to the internet only when you choose to download an optional Quran " +
            "translation. No other network requests are made, and downloaded translations are stored " +
            "locally like everything else.",
    ),
    PolicySection(
        "Changes and contact",
        "If this policy changes, the update will be described here and in the app's release " +
            "notes. Questions about privacy can be sent to " + SupportInfo.CONTACT_EMAIL + ".",
    ),
)

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors

    SettingsScaffold(title = "Privacy policy", onBack = onBack) {
        Text(
            "The short version: Waqfah has nothing to send anyone — your data stays on your phone.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(10.dp))
        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(22.dp))
            Text(
                section.title,
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                section.body,
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
