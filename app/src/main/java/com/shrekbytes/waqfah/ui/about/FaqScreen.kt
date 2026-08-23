package com.shrekbytes.waqfah.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

private data class FaqItem(val question: String, val answer: String)

private val items = listOf(
    FaqItem(
        "The reading screen doesn't appear when I open an app",
        "Check four things:\n\n" +
            "1. Usage access and Display over other apps are granted (Settings > App permissions).\n" +
            "2. Waqfah is active (the toggle on Settings).\n" +
            "3. The app you opened is in your monitored list (Settings > Manage monitored apps).\n" +
            "4. That app isn't in its cooldown window yet.",
    ),
    FaqItem(
        "Waqfah stops working after a while",
        "Battery optimization is the usual culprit — grant \"Unrestricted battery\" in " +
            "Settings > App permissions. On aggressive OEMs (Xiaomi/MIUI, Huawei, Oppo/Realme), also " +
            "exclude Waqfah from the system's battery saver / auto-start manager, or background " +
            "services get killed no matter what Android settings say.",
    ),
    FaqItem(
        "Why is there a permanent notification?",
        "Android requires any app running a continuous background service to show a notification " +
            "saying so. Waqfah's is silent and sits at the lowest priority — it exists because the " +
            "system demands transparency, not because it's doing anything worth interrupting you for.",
    ),
    FaqItem(
        "Can Waqfah read my screen or my messages?",
        "No. Waqfah never had screen-reading access. Usage access only tells it which app is in " +
            "the foreground — never what's displayed in it. It cannot read notifications, messages, " +
            "or screen content, and it has no internet permission use beyond downloading optional " +
            "translations you explicitly request.",
    ),
    FaqItem(
        "The reading screen appears a moment late",
        "Waqfah checks which app is foregrounded about once per second rather than watching every " +
            "window event — a deliberate trade-off that keeps battery use tiny. A sub-second delay " +
            "before the pause appears is normal.",
    ),
    FaqItem(
        "How does the cooldown work?",
        "Each monitored app has its own timer. After Waqfah appears for Instagram, Instagram won't " +
            "trigger again until its cooldown passes — but TikTok, monitored at the same time, is " +
            "unaffected. Set the wait time (or turn it off) at the top of Settings > Manage monitored apps.",
    ),
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors

    SettingsScaffold(title = "FAQ & troubleshooting", onBack = onBack) {
        Text(
            "Fixes for the things that most often go wrong, and answers to fair questions.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, item ->
            ExpandableRow(item)
            if (index < items.lastIndex) HorizontalDivider(color = colors.line)
        }
        Text(
            "Still stuck? Reach us at ${SupportInfo.CONTACT_EMAIL}",
            color = colors.inkMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )
    }
}

// Flat full-width row with the same press highlight as every other list row in
// the app — no cards or borders.
@Composable
private fun ExpandableRow(item: FaqItem) {
    val colors = WaqfahTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowHighlight by animateColorAsState(
        targetValue = if (isPressed) colors.line.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (isPressed) 60 else 220),
        label = "faq_row_highlight",
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(rowHighlight)
                .clickable(interactionSource = interactionSource, indication = null) { expanded = !expanded }
                .padding(horizontal = 6.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.question,
                color = if (expanded) colors.accent else colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            // Same thin › glyph as the settings rows; rotates to point down
            // while the answer is open.
            Text(
                "\u203a",
                color = if (expanded) colors.accent else colors.inkSoft,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                item.answer,
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 13.dp),
            )
        }
    }
}
