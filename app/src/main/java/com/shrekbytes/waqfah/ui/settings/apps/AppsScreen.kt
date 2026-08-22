package com.shrekbytes.waqfah.ui.settings.apps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.ui.components.EmptyListNote
import com.shrekbytes.waqfah.ui.components.InlineField
import com.shrekbytes.waqfah.ui.components.Stepper
import com.shrekbytes.waqfah.ui.components.WaqfahBackButton
import com.shrekbytes.waqfah.ui.components.WaqfahSearchField
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun AppsScreen(viewModel: AppsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors

    // Not built on SettingsScaffold: the app list can run to 100+ installed
    // apps, so it needs a LazyColumn rather than sitting inside one big
    // scrolling Column with everything else.
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        WaqfahBackButton(onClick = onBack)
        Text(
            "Apps",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = colors.ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Waqfah appears before these apps open.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
        )

        InlineField("Wait before showing again", showDivider = false) {
            Stepper(
                value = state.cooldownMinutes,
                suffix = " min",
                min = 0,
                max = 60,
                valueLabel = { if (it == 0) "Off" else "$it min" },
                onChange = viewModel::setCooldown,
            )
        }
        Text(
            "Applies per app. Opening Instagram won't silence the screen for TikTok or your other selected apps during this time.",
            color = colors.inkMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        WaqfahSearchField(value = state.searchQuery, onValueChange = viewModel::setSearchQuery)
        Spacer(Modifier.height(6.dp))

        if (state.apps.isEmpty()) {
            EmptyListNote("No apps found")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.apps, key = { it.app.packageName }) { row ->
                AppRow(row, onClick = { viewModel.toggle(row.app) })
            }
        }
    }
}

// internal, not private: reused as-is by OnboardChooseAppsScreen.
// Same flat, full-line press highlight as SettingsNavRow (see its comment)
// rather than a bounded ripple — this row previously had indication = null
// with nothing in its place, so presses on the app list gave no feedback
// at all.
@Composable
internal fun AppRow(row: AppRowState, onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowHighlight by animateColorAsState(
        targetValue = if (isPressed) colors.line.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (isPressed) 60 else 220),
        label = "app_row_highlight",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(rowHighlight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.app.label.take(1).uppercase(), color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(row.app.label, color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        AppCheckbox(checked = row.isMonitored)
    }
}

// A compact rounded-square checkbox matching the prototype, rather than
// Material3's default Checkbox (a noticeably different, larger shape).
@Composable
private fun AppCheckbox(checked: Boolean) {
    val colors = WaqfahTheme.colors
    Box(
        Modifier
            .size(21.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) colors.accent else colors.background)
            .border(1.5.dp, if (checked) colors.accent else colors.inkSoft, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.accentInk, modifier = Modifier.size(13.dp))
        }
    }
}
