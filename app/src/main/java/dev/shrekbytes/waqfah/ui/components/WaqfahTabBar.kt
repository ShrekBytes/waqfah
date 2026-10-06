package dev.shrekbytes.waqfah.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.ui.theme.WaqfahColors
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class WaqfahTab { HOME, BOOKMARKS, SETTINGS }

// One size for all three glyphs — the row only stays even if every tab's icon
// is the same height, whatever shape it is.
private val TAB_ICON_SIZE = 22.dp

// The selection pill's slot. The pill used to live inside each tab and settle
// at 0.85 scale when selected; it is now one shared element drawn at that same
// settled size and moved between tabs, so the geometry is named here.
private val TAB_PILL_SLOT_WIDTH = 42.dp
private val TAB_PILL_SLOT_HEIGHT = 27.dp
private const val TAB_PILL_SETTLED_SCALE = 0.85f
private val TAB_ITEM_VERTICAL_PADDING = 6.dp

// Shared by the pill's travel and the icon colour's fade, so selection reads as
// one motion — the icon must not finish changing before the pill lands.
private const val TAB_SELECTION_DURATION_MS = 250

@Composable
fun WaqfahTabBar(
    selected: WaqfahTab,
    onHomeClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
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

    // The selected pill is ONE shared element that travels between the tabs,
    // not a pill each tab pops in place. Home and Bookmarks render the same
    // reading card, so nothing on the card can signal "you changed tab" — this
    // pill is that signal (see MainScreen's tab transitionSpec). Each tab
    // reports the centre of its icon slot in the row's coordinates; the pill
    // animates between those centres.
    val centres = remember {
        mutableStateListOf<Float?>().apply { repeat(WaqfahTab.entries.size) { add(null) } }
    }
    val measured = centres.all { it != null }
    val targetX = centres[selected.ordinal] ?: 0f
    val pillX = remember { Animatable(0f) }
    var pillPlaced by remember { mutableStateOf(false) }

    // Guards the write: onGloballyPositioned fires on every layout pass, and
    // writing an unchanged centre back into the list would invalidate this
    // composition again and again.
    fun reportCentre(index: Int, x: Float) {
        if (centres[index] != x) centres[index] = x
    }

    LaunchedEffect(targetX, measured) {
        if (!measured) return@LaunchedEffect
        if (pillPlaced) {
            pillX.animateTo(targetX, tween(TAB_SELECTION_DURATION_MS, easing = FastOutSlowInEasing))
        } else {
            // First placement snaps: the pill must appear under the tab that is
            // already selected, not slide in from the left edge on frame one.
            pillX.snapTo(targetX)
            pillPlaced = true
        }
    }

    val density = LocalDensity.current
    val pillWidth = TAB_PILL_SLOT_WIDTH * TAB_PILL_SETTLED_SCALE
    val pillHeight = TAB_PILL_SLOT_HEIGHT * TAB_PILL_SETTLED_SCALE
    val pillHalfWidthPx = with(density) { pillWidth.toPx() / 2f }
    val pillTopPx = with(density) {
        (TAB_ITEM_VERTICAL_PADDING + (TAB_PILL_SLOT_HEIGHT - pillHeight) / 2f).toPx()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp)
            .clip(shape)
            .background(barColor)
            .then(if (isLight) Modifier.border(1.dp, colors.line.copy(alpha = 0.6f), shape) else Modifier),
    ) {
        // A Box, not the Row directly, so the shared pill can sit behind the
        // tabs. The Row fills the box, so a tab's centre relative to the row is
        // also its centre relative to this box.
        Box {
            if (measured) {
                Box(
                    Modifier
                        // Layout-phase read, so the pill animates without a
                        // recomposition per frame.
                        .offset {
                            IntOffset(
                                ((if (pillPlaced) pillX.value else targetX) - pillHalfWidthPx).roundToInt(),
                                pillTopPx.roundToInt(),
                            )
                        }
                        .size(pillWidth, pillHeight)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accentSoft),
                )
            }
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TabItem(
                    label = stringResource(R.string.tab_home),
                    isSelected = selected == WaqfahTab.HOME,
                    onClick = onHomeClick,
                    colors = colors,
                    onCentreMeasured = { x -> reportCentre(0, x) },
                ) { tint ->
                    Icon(Icons.Default.Home, contentDescription = null, tint = tint, modifier = Modifier.size(TAB_ICON_SIZE))
                }
                // The hand-drawn ribbon rather than a Material bookmark glyph: it
                // is the same control the reading card's toggle shows, so the tab
                // and the action that fills it read as one thing. Filled, not
                // outlined, so its weight matches Home's and Settings' filled
                // glyphs — an outlined ribbon beside two solid ones read as a
                // lighter, secondary tab.
                TabItem(
                    label = stringResource(R.string.tab_bookmarks),
                    isSelected = selected == WaqfahTab.BOOKMARKS,
                    onClick = onBookmarksClick,
                    colors = colors,
                    onCentreMeasured = { x -> reportCentre(1, x) },
                ) { tint ->
                    BookmarkRibbonIcon(filled = true, tint = tint, modifier = Modifier.size(TAB_ICON_SIZE))
                }
                TabItem(
                    label = stringResource(R.string.tab_settings),
                    isSelected = selected == WaqfahTab.SETTINGS,
                    onClick = onSettingsClick,
                    colors = colors,
                    onCentreMeasured = { x -> reportCentre(2, x) },
                ) { tint ->
                    Icon(Icons.Default.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(TAB_ICON_SIZE))
                }
            }
        }
    }
}

// No default ripple: the shared pill travels to the selected tab while the icon
// colour fades over the same window, so selection reads as one motion rather
// than a colour flash (a spring here would overshoot mid-flight and flash).
// Pressing previews via a slight shrink.
@Composable
private fun TabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: WaqfahColors,
    onCentreMeasured: (Float) -> Unit,
    // A slot rather than an ImageVector: the bar's icons are not all Material
    // glyphs — the bookmark tab draws the app's own ribbon.
    icon: @Composable (tint: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_item_scale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.inkMuted,
        animationSpec = tween(TAB_SELECTION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "tab_content_color",
    )

    Column(
        Modifier
            // Outermost, so it reports the whole item's bounds — and the icon
            // slot is centred in the item, so the item's centre is the pill's.
            .onGloballyPositioned { coords ->
                onCentreMeasured(coords.positionInParent().x + coords.size.width / 2f)
            }
            .scale(itemScale)
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = TAB_ITEM_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(TAB_PILL_SLOT_WIDTH, TAB_PILL_SLOT_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            // null: the tab is clickable as one merged unit, and the visible
            // label Text below already supplies its accessible name — setting
            // it here too would make TalkBack announce the label twice.
            icon(contentColor)
        }
        Text(
            label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
