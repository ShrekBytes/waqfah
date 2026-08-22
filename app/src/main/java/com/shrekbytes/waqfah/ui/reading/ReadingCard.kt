package com.shrekbytes.waqfah.ui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.components.ChevronDirection
import com.shrekbytes.waqfah.ui.components.ChevronIcon
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import com.shrekbytes.waqfah.ui.theme.toFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// How much further the content travels than the finger physically does while
// swiping — see the pointerInput block below for why.
private const val DRAG_MULTIPLIER = 1.35f

// The post-threshold "complete the swipe" animation used to travel the
// content a full box-width off screen before swapping in the next ayah, then
// a full box-width back in from the other side — a real page's worth of
// empty travel in each direction, which read as a big, disconnected jump
// between ayahs rather than a swipe between close neighbours. Fading the
// content out (see contentAlpha, below the pointerInput block) well before
// it reaches this fraction of the width means the data swap is already
// invisible long before full width, so the whole transition can cover much
// less ground without the swap ever popping into view.
private const val EXIT_DISTANCE_FRACTION = 0.5f

@Composable
fun ReadingCard(
    state: ReadingUiState,
    onMarkRead: () -> Unit,
                onNext: () -> Unit,
                onPrevious: () -> Unit,
                onResume: () -> Unit,
                onCycleTranslation: (forward: Boolean) -> Unit,
                onResetTranslation: () -> Unit,
                bottomBar: @Composable () -> Unit,
                modifier: Modifier = Modifier,
) {
    val colors = WaqfahTheme.colors
    val scope = rememberCoroutineScope()

    // Drives the ayah content's horizontal position: follows the finger
    // live while dragging, then either springs back to 0 (swipe released
    // under threshold) or flies fully off/on-screen (swipe past threshold —
    // see the pointerInput block below). Also the button-free base case
    // (0f, at rest) whenever nothing is being dragged.
    val dragOffset = remember { Animatable(0f) }

    // Bumped on every mark-read, whether it came from double-tapping the
    // card or tapping the pill itself, so MarkReadPill can play a small
    // "live" bounce each time — including repeat taps on an already-marked
    // ayah, not just the false->true transition.
    var markReadTrigger by remember { mutableIntStateOf(0) }

    // Whether the translation's left/right "compare sources" arrows are
    // currently revealed. Purely local UI state — the actual text swap lives
    // in ReadingViewModel — reset whenever the ayah itself changes (via
    // ayahLabel below) so switching ayahs doesn't leave a stale switcher open
    // pointed at the ayah that's no longer on screen.
    var translationSwitcherOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.ayahLabel) { translationSwitcherOpen = false }

    val handleMarkRead: () -> Unit = {
        markReadTrigger++
        onMarkRead()
    }

    Column(modifier.fillMaxSize()) {
        if (state.isLoading) {
            // The header and body used to render immediately regardless of
            // isLoading — which was never actually read anywhere — so a cold
            // app open showed a flash of genuinely blank layout (empty surah
            // name, empty ayah text) instead of any loading indication,
            // until the first DB read resolved. One shared skeleton, filling
            // the same space the header + body normally take, replaces that
            // blank flash — see ReadingSkeleton below.
            ReadingSkeleton(Modifier.weight(1f).fillMaxWidth())
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides state.surahNameDirection) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                       horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.surahName, color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(state.totalLabel, color = colors.inkMuted, fontSize = 12.sp)
                }
            }

            if (state.isPaused) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                       verticalArrangement = Arrangement.Center,
                       horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Waqfah is off right now.", color = colors.inkMuted, fontSize = 14.sp)
                    TextButton(onClick = onResume) {
                        Text("Turn back on", color = colors.accent, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                BoxWithConstraints(
                    Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Horizontal-only, so it doesn't compete with the
                    // vertical scroll below: a plain vertical drag never
                    // accumulates meaningful horizontal distance. Swipe
                    // left (finger moves left, content "advances") ->
                    // next, same direction convention as
                    // carousels/stories/etc.
                    //
                    // dragOffset drives a live, finger-following
                    // horizontal translation on the content below (see
                    // its .offset{} modifier) instead of only reacting
                    // once the gesture ends — a swipe with zero visible
                    // motion until release read as unresponsive.
                    // Crossing the threshold continues straight into a
                    // slide-through: the current ayah finishes sliding
                    // out to EXIT_DISTANCE_FRACTION of the width — by
                    // which point it's already faded to fully
                    // transparent (see contentAlpha below) —
                    // onNext()/onPrevious() swaps the underlying content
                    // while it's invisible (so the swap itself is never
                    // visible, regardless of exactly how long the DB
                    // read takes), then the new ayah fades and slides in
                    // from the opposite side. Letting go early, under
                    // threshold, just springs back to center instead.
                    //
                    // DRAG_MULTIPLIER makes the content travel further
                    // than the finger does, and thresholdPx is
                    // deliberately small — a light flick, not a
                    // full-width drag — so reaching it takes noticeably
                    // less physical movement than an 80dp threshold at
                    // 1:1 tracking did.
                    .pointerInput(onNext, onPrevious) {
                        val thresholdPx = 32.dp.toPx()
                        val flingPx = size.width.toFloat() * EXIT_DISTANCE_FRACTION
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val finalDrag = dragOffset.value
                                scope.launch {
                                    when {
                                        finalDrag <= -thresholdPx -> {
                                            dragOffset.animateTo(-flingPx, tween(160))
                                            onNext()
                                            dragOffset.snapTo(flingPx)
                                            dragOffset.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                            )
                                        }
                                        finalDrag >= thresholdPx -> {
                                            dragOffset.animateTo(flingPx, tween(160))
                                            onPrevious()
                                            dragOffset.snapTo(-flingPx)
                                            dragOffset.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                            )
                                        }
                                        else -> dragOffset.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragOffset.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    )
                                }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val moved = dragOffset.value + dragAmount * DRAG_MULTIPLIER
                                dragOffset.snapTo(moved.coerceIn(-flingPx * 1.05f, flingPx * 1.05f))
                            }
                        }
                    }
                    .pointerInput(handleMarkRead) {
                        detectTapGestures(onDoubleTap = { handleMarkRead() })
                    },
                ) {
                    // Same "half the box width" fade distance as flingPx
                    // above (EXIT_DISTANCE_FRACTION) — computed here from
                    // BoxWithConstraints' own `constraints` (no density
                    // conversion needed) so the ayah's alpha can be driven
                    // straight from dragOffset.value, the same way
                    // MarkReadPill drives its bounce scale from
                    // bounce.value below. Read directly here (not deferred
                    // via a graphicsLayer{} block) so this only recomposes
                    // the single Column below on each drag/fling frame,
                    // not the whole card.
                    val exitPx = constraints.maxWidth * EXIT_DISTANCE_FRACTION
                    val contentAlpha = if (exitPx <= 0f) 1f else (1f - (abs(dragOffset.value) / exitPx)).coerceIn(0f, 1f)

                    // heightIn(min = viewport height) is the trick: when the
                    // ayah's natural content is shorter than the screen, the
                    // Column is stretched to fill it, so Arrangement.Center
                    // has room to actually center things. When content is
                    // longer than the screen (a long ayah + translation),
                    // the natural height already exceeds that minimum, so
                    // the min has no effect — it just lays out top-to-bottom
                    // and scrolls normally.
                    Column(
                        Modifier
                        .fillMaxWidth()
                        .heightIn(min = maxHeight)
                        // Fades the ayah out as it approaches
                        // EXIT_DISTANCE_FRACTION of the width (in either
                        // direction) and back in as it returns to
                        // center — see contentAlpha above.
                        .alpha(contentAlpha)
                        // Read in the layout phase (not composition), so
                        // a dragging finger updates position/redraw
                        // only, without forcing a full recomposition
                        // every frame.
                        .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                           verticalArrangement = Arrangement.Center,
                           horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(14.dp))
                        NumDivider(state.ayahLabel)
                        Spacer(Modifier.height(24.dp))
                        Text(
                            state.arabicText,
                             color = colors.ink,
                             textAlign = TextAlign.Center,
                             fontFamily = state.arabicFont.toFontFamily(),
                             fontSize = state.arabicFontSize.sp,
                             // Arabic diacritics (tashkeel) sit above/below the letter and
                             // get clipped by a normal Latin-text line height — this needs
                             // noticeably more room.
                             lineHeight = (state.arabicFontSize * 2f).sp,
                        )
                        state.translitText?.let {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                it,
                                 color = colors.inkMuted,
                                 textAlign = TextAlign.Center,
                                 fontSize = state.translitFontSize.sp,
                                 fontStyle = FontStyle.Italic,
                                 lineHeight = (state.translitFontSize * 1.7f).sp,
                                 modifier = Modifier.widthIn(max = 280.dp),
                            )
                        }
                        state.translationText?.let { translationText ->
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(modifier = Modifier.width(32.dp), color = colors.line)
                            Spacer(Modifier.height(24.dp))

                            if (state.translationHasAlternates) {
                                // Small uppercase source label — e.g. "THE
                                // CLEAR QURAN" — only while the switcher is
                                // open, so it doesn't clutter normal reading
                                // but confirms exactly what's being previewed.
                                AnimatedVisibility(
                                    visible = translationSwitcherOpen,
                                    enter = fadeIn() + expandVertically(),
                                                   exit = fadeOut() + shrinkVertically(),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            (state.translationSourceName ?: "").uppercase(),
                                             color = colors.accent,
                                             fontSize = 10.5.sp,
                                             fontWeight = FontWeight.SemiBold,
                                             letterSpacing = 0.6.sp,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AnimatedVisibility(
                                        visible = translationSwitcherOpen,
                                        enter = fadeIn() + expandHorizontally(),
                                                       exit = fadeOut() + shrinkHorizontally(),
                                    ) {
                                        TranslationSwitchArrow(direction = ChevronDirection.LEFT) { onCycleTranslation(false) }
                                    }
                                    Text(
                                        translationText,
                                         color = colors.inkMuted,
                                         textAlign = TextAlign.Center,
                                         fontSize = state.translationFontSize.sp,
                                         lineHeight = (state.translationFontSize * 1.7f).sp,
                                         modifier = Modifier
                                         .widthIn(max = 280.dp)
                                         // Tapping toggles a lightweight, per-ayah
                                         // "compare translations" mode: reveals
                                         // left/right arrows to preview any other
                                         // downloaded translation for just this
                                         // ayah, without ever touching the real
                                         // default (see
                                         // ReadingViewModel.cycleTranslationSource).
                                         // Closing it (tapping again) reverts to
                                         // the default. This claims single taps
                                         // landing on the translation text itself,
                                         // so double-tapping specifically on this
                                         // text won't also trigger mark-read —
                                         // double-tapping anywhere else on the
                                         // ayah still does.
                                         .clickable(
                                             interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                         ) {
                                             translationSwitcherOpen = !translationSwitcherOpen
                                             if (!translationSwitcherOpen) onResetTranslation()
                                         },
                                    )
                                    AnimatedVisibility(
                                        visible = translationSwitcherOpen,
                                        enter = fadeIn() + expandHorizontally(),
                                                       exit = fadeOut() + shrinkHorizontally(),
                                    ) {
                                        TranslationSwitchArrow(direction = ChevronDirection.RIGHT) { onCycleTranslation(true) }
                                    }
                                }
                            } else {
                                Text(
                                    translationText,
                                     color = colors.inkMuted,
                                     textAlign = TextAlign.Center,
                                     fontSize = state.translationFontSize.sp,
                                     lineHeight = (state.translationFontSize * 1.7f).sp,
                                     modifier = Modifier.widthIn(max = 280.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemArrow(direction = ChevronDirection.LEFT, onClick = onPrevious, contentDescription = "Previous ayah")
                    Spacer(Modifier.width(10.dp))
                    MarkReadPill(marked = state.isMarkedRead, markReadTrigger = markReadTrigger, onClick = handleMarkRead)
                    Spacer(Modifier.width(10.dp))
                    RemArrow(direction = ChevronDirection.RIGHT, onClick = onNext, contentDescription = "Next ayah")
                }
            }
        }

        bottomBar()
    }
}

// Placeholder shown in place of the header (surah name + total label) and
// main content while state.isLoading is true — one composable, one shared
// pulse, rather than two independently-timed animations that could drift
// out of phase with each other.
@Composable
private fun ReadingSkeleton(modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors

    // A gentle pulse between two close alpha values reads clearly as "still
    // loading" without needing a moving shimmer sweep (a gradient sliding
    // across each bar) — much less code for a state that, backed by a local
    // SQLite read, is realistically only ever on screen for a handful of
    // frames anyway.
    val transition = rememberInfiniteTransition(label = "skeleton_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                              label = "skeleton_pulse_alpha",
    )
    val barColor = colors.line.copy(alpha = pulseAlpha)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        SkeletonBar(width = 88.dp, height = 13.dp, color = barColor)
        Spacer(Modifier.height(9.dp))
        SkeletonBar(width = 56.dp, height = 11.dp, color = barColor)
        Spacer(Modifier.weight(1f))
        SkeletonBar(width = 220.dp, height = 22.dp, color = barColor)
        Spacer(Modifier.height(10.dp))
        SkeletonBar(width = 170.dp, height = 22.dp, color = barColor)
        Spacer(Modifier.height(22.dp))
        SkeletonBar(width = 190.dp, height = 13.dp, color = barColor)
        Spacer(Modifier.height(22.dp))
        SkeletonBar(width = 230.dp, height = 11.dp, color = barColor)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(width = 170.dp, height = 11.dp, color = barColor)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp, color: Color) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(6.dp)).background(color))
}

@Composable
private fun NumDivider(label: String) {
    val colors = WaqfahTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.width(20.dp), color = colors.line)
        Text(label, color = colors.inkMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.25.sp)
        HorizontalDivider(modifier = Modifier.width(20.dp), color = colors.line)
    }
}

@Composable
private fun RemArrow(direction: ChevronDirection, onClick: () -> Unit, contentDescription: String) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        ChevronIcon(
            direction = direction,
            tint = WaqfahTheme.colors.inkMuted,
            modifier = Modifier.size(18.dp).semantics { this.contentDescription = contentDescription },
        )
    }
}

// Smaller and accent-tinted (vs. RemArrow's muted 44dp ayah-navigation
// arrows) — this is a secondary, "you're in compare mode" affordance sitting
// right next to the translation text, not a primary nav control, so it
// should read as noticeably lighter-weight.
@Composable
private fun TranslationSwitchArrow(direction: ChevronDirection, onClick: () -> Unit) {
    val contentDescription = if (direction == ChevronDirection.LEFT) "Previous translation" else "Next translation"
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        ChevronIcon(
            direction = direction,
            tint = WaqfahTheme.colors.accent,
            modifier = Modifier.size(13.dp).semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
private fun MarkReadPill(marked: Boolean, markReadTrigger: Int, onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    // Pending (not yet marked) is the call-to-action, so it gets the loud
    // solid-accent treatment — that's what should draw the eye. Once marked,
    // the state should quietly recede rather than compete for attention, so
    // it switches to the same soft, muted treatment used everywhere else for
    // a low-emphasis state (this was previously backwards: marked was the
    // louder of the two, which read as more prominent than the actual
    // pending action).
    val backgroundColor by animateColorAsState(if (marked) colors.accentSoft else colors.accent, label = "mark_read_bg")
    val contentColor by animateColorAsState(if (marked) colors.accent else colors.accentInk, label = "mark_read_content")

    // Crossfade measures each branch on its own and defaults to top-start
    // alignment for whichever one isn't the current bounding box, so the
    // checkmark rendered pinned to the left edge of the (wider, text-sized)
    // box for most of the transition and only snapped to center once the old
    // content was gone — "comes in on the left, then jumps to center" — and
    // re-measuring both branches every frame showed up as visible frame
    // drops. Keeping both the text and the icon permanently composed,
    // stacked in the same centered Box, and animating only their opacity
    // sidesteps both problems: nothing ever moves, and each frame is a pure
    // redraw with no relayout.
    val checkAlpha by animateFloatAsState(if (marked) 1f else 0f, label = "mark_read_check_alpha")
    val textAlpha by animateFloatAsState(if (marked) 0f else 1f, label = "mark_read_text_alpha")

    // A small bounce on every mark-read tap (double-tap on the card or
    // tapping the pill itself) — not just the false->true transition — so
    // the pill itself feels "live" and gives the double-tap gesture some
    // acknowledgement without needing a separate effect just for it.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(markReadTrigger) {
        if (markReadTrigger == 0) return@LaunchedEffect
            bounce.snapTo(0.86f)
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
            color = backgroundColor,
            contentColor = contentColor,
            modifier = Modifier
            .scale(bounce.value)
            .defaultMinSize(minWidth = 124.dp)
            .semantics { contentDescription = if (marked) "Marked read" else "Mark read" },
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
            // Text is the wider of the two, so it's what reserves the pill's
            // footprint at all times. Its own semantics are cleared since the
            // Surface above already carries the accessible label for both states.
            Text(
                "Mark Read",
                 fontSize = 13.sp,
                 fontWeight = FontWeight.SemiBold,
                 modifier = Modifier.alpha(textAlpha).clearAndSetSemantics {},
            )
            Icon(
                Icons.Default.Check,
                 contentDescription = null,
                 modifier = Modifier.size(17.dp).alpha(checkAlpha),
            )
        }
    }
}
