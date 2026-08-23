package com.shrekbytes.waqfah.ui.settings.permissions

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

private data class RationaleBlock(val title: String, val allows: String, val never: String)

private val blocks = listOf(
    RationaleBlock(
        "Usage access",
        "Lets Waqfah ask Android which app just moved to the foreground, so it knows when one of " +
            "your selected apps opens and the reading screen should appear first.",
        "It never reads what's inside those apps — no screen content, no messages, no browsing " +
            "history. The foreground app's name stays on your device and goes nowhere.",
    ),
    RationaleBlock(
        "Display over other apps",
        "Lets the reading screen appear while another app is opening. Without it, Android would " +
            "block Waqfah from showing anything outside its own window.",
        "It draws only Waqfah's own reading screen, only at the moment a monitored app opens — " +
            "never ads, overlays, or anything on top of other apps at any other time.",
    ),
    RationaleBlock(
        "Unrestricted battery",
        "Stops Android from shutting down Waqfah's background monitor to save power, so triggers " +
            "keep working hours after you last opened the app.",
        "It doesn't change how much battery Waqfah uses — the monitor checks about once per second " +
            "and otherwise sleeps. This only removes the system's permission to kill it early.",
    ),
)

@Composable
fun PermissionsRationaleScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors

    SettingsScaffold(title = "Why Waqfah needs these", onBack = onBack) {
        Text(
            "Each permission maps to one thing Waqfah does. None of them let it read screen content " +
                "or send anything off your device.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(14.dp))
        blocks.forEach { block ->
            Text(
                block.title,
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                "What it lets Waqfah do: " + block.allows,
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                "What it never does: " + block.never,
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
