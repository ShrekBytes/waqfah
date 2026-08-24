package com.shrekbytes.waqfah.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.theme.WaqfahColors
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import kotlinx.serialization.Serializable

@Serializable
enum class WaqfahTab { HOME, SETTINGS }

@Composable
fun WaqfahTabBar(selected: WaqfahTab, onHomeClick: () -> Unit, onSettingsClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    // Floating pill: detached capsule. Dark themes lift the surface (elevated-
    // surface look); light themes go near-white with a hairline outline in
    // `line` — the crisp card treatment.
    val isLight = colors.background.luminance() > 0.5f
    val shape = RoundedCornerShape(50)
    // Keyed on background only: `isLight` derives from it, so one key covers both.
    val barColor = remember(colors.background) {
        lerp(colors.background, Color.White, if (isLight) 0.82f else 0.07f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp)
            .clip(shape)
            .background(barColor)
            .then(if (isLight) Modifier.border(1.dp, colors.line.copy(alpha = 0.6f), shape) else Modifier),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TabItem(stringResource(R.string.tab_home), Icons.Default.Home, selected == WaqfahTab.HOME, onHomeClick, colors)
            TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, selected == WaqfahTab.SETTINGS, onSettingsClick, colors)
        }
    }
}

// No default ripple: selecting swaps pill + icon color instantly (no animation,
// so there's no spring overshoot flash). Pressing previews via a slight shrink.
@Composable
private fun TabItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, colors: WaqfahColors) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Static color swap (no animation): animated colors with a spring overshoot
    // mid-flight and read as a flash. Click color == selected color, instantly.
    val pillColor = if (isSelected) colors.accentSoft else Color.Transparent
    val pillScale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_pill_scale",
    )
    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_item_scale",
    )
    val contentColor = if (isSelected) colors.accent else colors.inkMuted

    Column(
        Modifier
            .scale(itemScale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(42.dp).height(27.dp).scale(pillScale).clip(RoundedCornerShape(50)).background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
