package com.shrekbytes.waqfah.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

// The app's one shared primary CTA style.
@Composable
fun WaqfahPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = WaqfahTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.accentInk,
            disabledContainerColor = colors.accent.copy(alpha = 0.35f),
            disabledContentColor = colors.accentInk.copy(alpha = 0.7f),
        ),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

enum class ChevronDirection { LEFT, RIGHT }

// Thin two-segment chevron matching the app's icon style, where Material's
// filled arrow glyphs are visibly heavier.
@Composable
fun ChevronIcon(direction: ChevronDirection, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 18f
        val path = Path().apply {
            if (direction == ChevronDirection.LEFT) {
                moveTo(11f * s, 4f * s)
                lineTo(6f * s, 9f * s)
                lineTo(11f * s, 14f * s)
            } else {
                moveTo(7f * s, 4f * s)
                lineTo(12f * s, 9f * s)
                lineTo(7f * s, 14f * s)
            }
        }
        drawPath(path, color = tint, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun WaqfahBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.background,
        contentColor = colors.inkMuted,
        modifier = modifier.padding(top = 16.dp).size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ChevronIcon(ChevronDirection.LEFT, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
        }
    }
}

// Hairline pill search field rather than Material's floating-label text field.
@Composable
fun WaqfahSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search apps",
) {
    val colors = WaqfahTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = colors.inkSoft, modifier = Modifier.size(15.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = colors.inkSoft, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun EmptyListNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = WaqfahTheme.colors.inkSoft,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = WaqfahTheme.colors.inkMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        color = WaqfahTheme.colors.ink,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

// Label on top, control below, hairline divider after (pass showDivider = false
// for the last field in a section).
@Composable
fun SettingsField(showDivider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 16.dp), content = content)
    if (showDivider) HorizontalDivider(color = colors.line)
}

// Label and control share one row — for steppers and single-value pickers.
@Composable
fun InlineField(label: String, showDivider: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = WaqfahTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .let {
                // Clip before clickable so the ripple is bounded to a rounded rect.
                if (onClick != null) it.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else it
            }
            .padding(horizontal = 6.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        content()
    }
    if (showDivider) HorizontalDivider(color = colors.line)
}

// Flat, edge-to-edge press highlight instead of a bounded ripple.
@Composable
fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit, external: Boolean = false) {
    val colors = WaqfahTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowHighlight by animateColorAsState(
        targetValue = if (isPressed) colors.line.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (isPressed) 60 else 220),
        label = "settings_row_highlight",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(rowHighlight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = colors.inkMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (external) {
            // Outward-tilted chevron signals "opens a link" vs. drilling in.
            ChevronIcon(
                direction = ChevronDirection.RIGHT,
                tint = colors.inkSoft,
                modifier = Modifier.size(14.dp).rotate(-45f),
            )
        } else {
            Text("›", color = colors.inkSoft, fontSize = 14.sp)
        }
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = WaqfahTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.inkMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        WaqfahSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

// Permissions row: tapping always opens system settings (these permissions
// can't be flipped in place); the switch only reflects current status.
@Composable
fun PermissionRow(title: String, subtitle: String, granted: Boolean, onOpenSettings: () -> Unit) {
    val colors = WaqfahTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.inkMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        WaqfahSwitch(checked = granted, onCheckedChange = { onOpenSettings() })
    }
}

// Onboarding variant with a Grant button instead of a toggle.
@Composable
fun OnboardPermissionRow(title: String, subtitle: String, granted: Boolean, onOpenSettings: () -> Unit) {
    val colors = WaqfahTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.inkMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Icon(Icons.Default.Check, contentDescription = "Granted", tint = colors.accent, modifier = Modifier.size(16.dp))
        } else {
            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(50),
                color = colors.accentSoft,
                contentColor = colors.accent,
            ) {
                Text(
                    "Grant",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// Slim custom pill toggle rather than Material3's Switch.
@Composable
fun WaqfahSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    val knobOffset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "toggle_knob")
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) colors.accent else colors.line)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = knobOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.background),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = WaqfahTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) colors.accent else Color.Transparent,
                contentColor = if (isSelected) colors.accentInk else colors.inkMuted,
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    suffix: String,
    min: Int,
    max: Int,
    step: Int = 1,
    valueLabel: (Int) -> String = { "$it$suffix" },
    onChange: (Int) -> Unit,
) {
    val colors = WaqfahTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StepperButton("−", enabled = value - step >= min) { onChange(value - step) }
        Text(valueLabel(value), color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        StepperButton("+", enabled = value + step <= max) { onChange(value + step) }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.accentSoft,
        contentColor = if (enabled) colors.ink else colors.inkSoft,
        modifier = Modifier.size(30.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ProgressRing(percent: Int, modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(color = colors.line, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            drawArc(color = colors.accent, startAngle = -90f, sweepAngle = 360f * (percent / 100f), useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        WaqfahBackButton(onClick = onBack)
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = colors.ink,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
    }
}
