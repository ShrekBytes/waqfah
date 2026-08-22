package com.shrekbytes.waqfah.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.theme.WaqfahColors
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import kotlinx.serialization.Serializable

@Serializable
enum class WaqfahTab { HOME, SETTINGS }

@Composable
fun WaqfahTabBar(selected: WaqfahTab, onHomeClick: () -> Unit, onSettingsClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        TabItem("Home", Icons.Default.Home, selected == WaqfahTab.HOME, onHomeClick, colors)
        TabItem("Settings", Icons.Default.Settings, selected == WaqfahTab.SETTINGS, onSettingsClick, colors)
    }
}

// No default Material ripple here on purpose — a generic gray ripple reads
// as a completely different visual language from the accent-soft pill that
// marks the selected tab, so pressing an *unselected* tab used to flash one
// effect and then, if it becomes selected, settle into a totally different
// one. Instead, press and selection share the exact same pill: pressing
// previews it (dimmer, slightly smaller), selecting commits it (full color).
// The pill's entrance also swaps a plain fade for a low-bounce spring so it
// feels like it *lands* rather than just dissolving in, and the whole item
// gets a tiny press-down scale for extra tactile feedback in place of the
// ripple.
@Composable
private fun TabItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, colors: WaqfahColors) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pillColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentSoft
            isPressed -> colors.accentSoft.copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_pill_color",
    )
    // Selected already reads as "bigger" than unselected purely from its
    // background pill + padding — scaling it up further on top of that
    // doubled up the emphasis and made the unselected icon look small by
    // comparison, when it's really the plain, unadorned one and doesn't need
    // to shrink at all. So the direction here is deliberately the reverse of
    // what you'd first reach for: full-size icon at rest, a slight settle as
    // it's pressed, landing a touch smaller once selected — the pill's own
    // fill is what carries the "this one's active" signal, the icon doesn't
    // need to grow to match it.
    val pillScale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_pill_scale",
    )
    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tab_item_scale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.inkMuted,
        label = "tab_content_color",
    )

    Column(
        Modifier
            .scale(itemScale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(46.dp).height(30.dp).scale(pillScale).clip(RoundedCornerShape(50)).background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
