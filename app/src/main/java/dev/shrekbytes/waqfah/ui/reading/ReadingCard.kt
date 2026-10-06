package dev.shrekbytes.waqfah.ui.reading

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.ui.components.BookmarkRibbonIcon
import dev.shrekbytes.waqfah.ui.components.ChevronDirection
import dev.shrekbytes.waqfah.ui.components.ChevronIcon
import dev.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import dev.shrekbytes.waqfah.ui.components.skeletonPulseAlpha
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Fixed absolute distance (not a fraction of screen width) so commit travel is
// small and consistent across device sizes.
private val COMMIT_THRESHOLD_DISTANCE = 56.dp

// Calm, bounce-free return to center on under-threshold release / cancellation.
private val CANCEL_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

// The bookmark toggle's footprint in the action row. Fixed, and the only thing
// that ever changes about the toggle is what it looks like — never how much room
// it takes, so the row's geometry, and with it Mark Read's position, is
// identical in both states.
//
// The row fits the toggle whole while it is at least 356dp wide with the pill at
// its 124dp minimum: 10 + 124 + 10 is fixed, and the right slot must also hold
// an arrow, a gap and the toggle (48 + 10 + 48) after mirroring the left slot's
// arrow. Below that — a 320dp card, or a 360dp one whose font scale has grown
// the pill past 128dp — the right slot runs out and the toggle is the part that
// overhangs. That is deliberate: the alternative is letting the toggle push Mark
// Read off the centre line, which is the one thing this row must not do.
private val ACTION_TOGGLE_SIZE = 44.dp

// One handler for every gesture-launched coroutine in the card: the swipe and
// arrow handlers await the session's suspend verbs, whose Room probes can fail
// on a troubled disk — log and keep the last good card, never crash.
private val gestureExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e("ReadingCard", "Unhandled error in reading gesture", throwable)
}

@Composable
fun ReadingCard(
    state: ReadingUiState,
    onMarkRead: () -> Unit,
    onNext: suspend () -> Unit,
    onPrevious: suspend () -> Unit,
    onCycleTranslation: (forward: Boolean) -> Unit,
    onResetTranslation: () -> Unit,
    onCompletionDismiss: () -> Unit,
    onStartOver: () -> Unit,
    onSwitchModeAndRestart: () -> Unit,
    onGoToAyah: (() -> Unit)? = null,
    // Home-only for now, like onGoToAyah: null means this host shows no
    // bookmark toggle at all, and the action row keeps its pre-toggle layout.
    onToggleBookmark: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaqfahTheme.colors
    val scope = rememberCoroutineScope { gestureExceptionHandler }

    // Follows the finger 1:1 while dragging; springs back to 0 under threshold,
    // animates to a full page width past it.
    val dragOffset = remember { Animatable(0f) }

    // The pointerInput blocks below are installed once (Unit-keyed) so a drag in
    // progress can never be cut off by recomposition; rememberUpdatedState keeps
    // their callbacks reading fresh values without restarting the blocks.
    val latestOnNext = rememberUpdatedState(onNext)
    val latestOnPrevious = rememberUpdatedState(onPrevious)
    val latestState = rememberUpdatedState(state)

    // Bumped on every mark-read so MarkReadPill can play its bounce each time.
    var markReadTrigger by remember { mutableIntStateOf(0) }

    // Read fresh by the switcher's disposal effect in the keyed subtree below,
    // so a recomposition that swaps the callback can't leave a stale capture
    // behind.
    val latestOnResetTranslation = rememberUpdatedState(onResetTranslation)

    val handleMarkRead: () -> Unit = {
        markReadTrigger++
        onMarkRead()
    }
    val latestHandleMarkRead = rememberUpdatedState(handleMarkRead)

    // Completion-popup reset confirmations — both Start Again and switching
    // mode wipe read history, so both route through the same confirm dialog.
    var confirmStartOver by remember { mutableStateOf(false) }
    var confirmSwitchMode by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        if (state.isLoading) {
            ReadingSkeleton(Modifier.weight(1f).fillMaxWidth())
        } else if (state.isEmpty) {
            BookmarkEmptyState(Modifier.weight(1f).fillMaxWidth())
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides state.surahNameDirection) {
                val gotoAyahLabel = if (onGoToAyah != null) stringResource(R.string.cd_goto_header) else null
                // Header tap affordance: the surah block is a floating pill mirroring
                // WaqfahTabBar's capsule (barColor + hairline outline + spring press
                // scale), so the tappable header at the top reads as a sibling of the
                // bottom nav tab bar — symmetrical and on-idiom, no icon or text hint.
                val headerInteractionSource = remember { MutableInteractionSource() }
                val isHeaderPressed by headerInteractionSource.collectIsPressedAsState()
                val headerPressed = onGoToAyah != null && isHeaderPressed
                val isLight = colors.background.luminance() > 0.5f
                val barColor = remember(colors.background) {
                    lerp(colors.background, Color.White, if (isLight) 0.82f else 0.07f)
                }
                val headerScale by animateFloatAsState(
                    targetValue = if (headerPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    label = "reading_header_scale",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .then(
                                if (onGoToAyah != null) Modifier
                                    .scale(headerScale)
                                    .clip(RoundedCornerShape(50))
                                    .background(barColor)
                                    .then(if (isLight) Modifier.border(1.dp, colors.line.copy(alpha = 0.6f), RoundedCornerShape(50)) else Modifier)
                                    .clickable(
                                        interactionSource = headerInteractionSource,
                                        indication = null,
                                        onClick = onGoToAyah,
                                    )
                                else Modifier
                            )
                            .semantics(mergeDescendants = true) {
                                if (gotoAyahLabel != null) contentDescription = gotoAyahLabel + ", " + state.surahName + ", " + state.totalLabel
                            }
                            .padding(horizontal = 64.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.surahName, color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            if (onGoToAyah != null) {
                                Spacer(Modifier.width(4.dp))
                                ChevronIcon(
                                    direction = ChevronDirection.RIGHT,
                                    tint = colors.inkMuted,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .rotate(90f),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(state.totalLabel, color = colors.inkMuted, fontSize = 12.sp)
                    }
                }
            }

            // Ayah pager — always shown. Waqfah's on/off toggle governs
            // detection only; it must never gate reading here (see the toggle
            // note in docs/ARCHITECTURE.md).
            run {
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Horizontal-only dragging: swipe left -> next ayah, same
                        // direction convention as carousels/stories. Kept separate
                        // from the double-tap detector because merging both into one
                        // block made touches occasionally not register.
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    // Read here, not at block-install time, so the value
                                    // stays correct after rotation/config changes.
                                    val pageWidthPx = size.width.toFloat()
                                    val commitThresholdPx = COMMIT_THRESHOLD_DISTANCE.toPx()
                                    val finalDrag = dragOffset.value
                                    scope.launch {
                                        when {
                                            finalDrag <= -commitThresholdPx && latestState.value.nextPreview != null -> {
                                                dragOffset.animateTo(-pageWidthPx, tween(220, easing = FastOutSlowInEasing))
                                                latestOnNext.value()
                                                dragOffset.snapTo(0f)
                                            }
                                            finalDrag >= commitThresholdPx && latestState.value.previousPreview != null -> {
                                                dragOffset.animateTo(pageWidthPx, tween(220, easing = FastOutSlowInEasing))
                                                latestOnPrevious.value()
                                                dragOffset.snapTo(0f)
                                            }
                                            else -> dragOffset.animateTo(0f, CANCEL_SPRING)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { dragOffset.animateTo(0f, CANCEL_SPRING) }
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val pageWidthPx = size.width.toFloat()
                                    val moved = dragOffset.value + dragAmount
                                    dragOffset.snapTo(moved.coerceIn(-pageWidthPx, pageWidthPx))
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { latestHandleMarkRead.value() })
                        },
                ) {
                    val pageWidthPx = constraints.maxWidth.toFloat()

                    // Peek pages sit just off-screen and slide in alongside the
                    // current ayah as dragOffset moves. A null preview just means
                    // that edge has nothing to reveal.
                    state.previousPreview?.let { preview ->
                        AyahPeekPage(preview = preview, minHeight = maxHeight, offsetPx = { -pageWidthPx + dragOffset.value })
                    }
                    state.nextPreview?.let { preview ->
                        AyahPeekPage(preview = preview, minHeight = maxHeight, offsetPx = { pageWidthPx + dragOffset.value })
                    }

                    // Keyed per ayah so this whole subtree — including scroll position
                    // and the translation-switcher state below — is rebuilt fresh on every
                    // swap. Without the key, a switcher left open on the previous ayah
                    // replayed its exit animation over the first frames of the incoming one.
                    key(state.ayahLabel) {
                        var translationSwitcherOpen by remember { mutableStateOf(false) }

                        // The close-tap is not the only way this subtree ends:
                        // a tab switch or a pushed screen disposes it outright.
                        // Disposing with the switcher open must revert the peek,
                        // or the next composition renders the peeked translation
                        // styled exactly like the user's default. (Ayah swaps
                        // dispose too, but step() already cleared the peek and
                        // resetTranslationSource() no-ops on a null override.)
                        DisposableEffect(Unit) {
                            onDispose {
                                if (translationSwitcherOpen) latestOnResetTranslation.value()
                            }
                        }

                        // heightIn(min = viewport height) lets Arrangement.Center center short
                        // content while long content still lays out top-to-bottom and scrolls.
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = maxHeight)
                                // Layout-phase read: dragging updates position/redraw only,
                                // no recomposition per frame.
                                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 28.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(14.dp))
                            NumDivider(state.ayahLabel)
                            Spacer(Modifier.height(24.dp))
                            AyahArabicText(state.arabicText, state.arabicFont, state.arabicFontSize)
                            state.translitText?.let {
                                Spacer(Modifier.height(20.dp))
                                AyahTranslitText(it, state.translitFontSize)
                            }
                            state.translationText?.let { translationText ->
                                Spacer(Modifier.height(24.dp))
                                HorizontalDivider(modifier = Modifier.width(32.dp), color = colors.line)
                                Spacer(Modifier.height(24.dp))

                                if (state.translationHasAlternates) {
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
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AyahTranslationText(
                                            translationText,
                                            state.translationFontSize,
                                            // Tapping toggles compare mode for this ayah only;
                                            // closing reverts to the default. Claims single taps
                                            // landing on the text, so double-tapping here won't
                                            // also trigger mark-read.
                                            modifier = Modifier
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    translationSwitcherOpen = !translationSwitcherOpen
                                                    if (!translationSwitcherOpen) onResetTranslation()
                                                }
                                                .padding(horizontal = 28.dp),
                                        )
                                        if (translationSwitcherOpen) {
                                            TranslationSwitchArrow(
                                                direction = ChevronDirection.LEFT,
                                                onClick = { onCycleTranslation(false) },
                                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
                                            )
                                            TranslationSwitchArrow(
                                                direction = ChevronDirection.RIGHT,
                                                onClick = { onCycleTranslation(true) },
                                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                                            )
                                        }
                                    }
                                } else {
                                    AyahTranslationText(translationText, state.translationFontSize)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
                // Mark Read is centred by construction rather than by luck: the
                // two weighted slots either side of it are always the same
                // width, so the pill lands on the card's centre line, and the
                // arrows keep the exact distance from it they had before the
                // bookmark toggle existed. Each slot aligns its own content
                // towards the pill, which is what puts the arrows 10dp away
                // without any of them knowing about the others.
                //
                // The toggle rides inside the right slot, after the right arrow,
                // which is what makes it outermost without displacing anything:
                // a wider right slot's *content* does not move the slot's edge,
                // so neither the pill nor either arrow can feel it.
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        RemArrow(direction = ChevronDirection.LEFT, onClick = onPrevious, contentDescription = stringResource(R.string.cd_prev_ayah))
                    }
                    Spacer(Modifier.width(10.dp))
                    MarkReadPill(
                        marked = state.isMarkedRead,
                        markReadTrigger = markReadTrigger,
                        verseKey = state.ayahLabel,
                        onClick = handleMarkRead,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RemArrow(direction = ChevronDirection.RIGHT, onClick = onNext, contentDescription = stringResource(R.string.cd_next_ayah))
                            // The save control, outermost on the right (see CONTEXT.md).
                            if (onToggleBookmark != null) {
                                Spacer(Modifier.width(10.dp))
                                BookmarkToggle(
                                    saved = state.isSaved,
                                    verseKey = state.ayahLabel,
                                    onClick = onToggleBookmark,
                                )
                            }
                        }
                    }
                }
            }
        }

        bottomBar()
    }

    // Shown whenever every ayah is marked read (a fresh session landing in a
    // finished state, or just having marked the final one). Close only hides
    // it for this session; Start Again / Switch make reading possible again
    // by resetting progress.
    if (!state.isLoading && state.isCompleted) {
        AlertDialog(
            // WaqfahTheme does not override MaterialTheme.colorScheme, so
            // without these the dialog keeps M3 default white surface in
            // every theme.
            containerColor = colors.background,
            titleContentColor = colors.ink,
            textContentColor = colors.inkMuted,
            onDismissRequest = onCompletionDismiss,
            title = { Text(stringResource(R.string.completion_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.completion_body),
                        color = colors.inkMuted,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    WaqfahPrimaryButton(text = stringResource(R.string.start_again), onClick = { confirmStartOver = true })
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { confirmSwitchMode = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.readingMode == ReadingMode.SEQUENTIAL) stringResource(R.string.switch_to_random_mode) else stringResource(R.string.switch_to_sequential_mode),
                            fontSize = 13.5.sp,
                            color = colors.accent,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onCompletionDismiss) { Text(stringResource(R.string.close), color = colors.inkMuted) }
            },
        )
    }

    if (confirmStartOver || confirmSwitchMode) {
        val willSwitch = confirmSwitchMode
        AlertDialog(
            containerColor = colors.background,
            titleContentColor = colors.ink,
            textContentColor = colors.inkMuted,
            onDismissRequest = { confirmStartOver = false; confirmSwitchMode = false },
            title = { Text(stringResource(R.string.reset_dialog_title), fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    stringResource(R.string.reset_dialog_body) +
                        if (willSwitch) stringResource(R.string.reset_mode_note) else "",
                    color = colors.inkMuted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStartOver = false
                    confirmSwitchMode = false
                    if (willSwitch) onSwitchModeAndRestart() else onStartOver()
                }) { Text(stringResource(R.string.yes_reset), color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStartOver = false; confirmSwitchMode = false }) {
                    Text(stringResource(R.string.cancel), color = colors.inkMuted)
                }
            },
        )
    }
}

// A non-interactive rendering of a neighbouring ayah, positioned just off to
// one side and animated in lockstep with the drag gesture.
@Composable
private fun AyahPeekPage(preview: AyahPreview, minHeight: Dp, offsetPx: () -> Float) {
    val colors = WaqfahTheme.colors
    // Keyed so scroll position never leaks into whichever ayah gets peeked next.
    val scrollState = remember(preview.ayahLabel) { ScrollState(0) }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            // Lambda keeps this a layout-phase read — no recomposition per frame.
            .offset { IntOffset(offsetPx().roundToInt(), 0) }
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(14.dp))
        NumDivider(preview.ayahLabel)
        Spacer(Modifier.height(24.dp))
        AyahArabicText(preview.arabicText, preview.arabicFont, preview.arabicFontSize)
        preview.translitText?.let {
            Spacer(Modifier.height(20.dp))
            AyahTranslitText(it, preview.translitFontSize)
        }
        preview.translationText?.let { translationText ->
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.width(32.dp), color = colors.line)
            Spacer(Modifier.height(24.dp))
            AyahTranslationText(translationText, preview.translationFontSize)
        }
        Spacer(Modifier.height(14.dp))
    }
}

// One shared skeleton with one shared pulse for the loading state.
@Composable
private fun ReadingSkeleton(modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    val pulseAlpha = skeletonPulseAlpha()
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

// The collection-scoped session's "nothing saved yet" state: the invitation to
// start a collection (ADR-0005), not an error and not a blank card. Only that
// session can reach it — the mushaf always has an ayah — so the copy is the
// bookmarks tab's, and the ribbon shows the control the message asks the
// reader to look for. No header, no pager, no action row: there is no ayah for
// any of them to be about. Title and body are the pair the tour's own message
// blocks use, so a card-sized message reads the same wherever it appears.
@Composable
private fun BookmarkEmptyState(modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    Column(
        modifier.padding(horizontal = 44.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BookmarkRibbonIcon(filled = false, tint = colors.inkSoft, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.bookmarks_empty_title),
            color = colors.ink,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.bookmarks_empty_body),
            color = colors.inkMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
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

// onClick is suspend because onNext/onPrevious are awaited mid-gesture; the
// scope lives here so call sites stay plain.
@Composable
private fun RemArrow(direction: ChevronDirection, onClick: suspend () -> Unit, contentDescription: String) {
    val scope = rememberCoroutineScope { gestureExceptionHandler }
    IconButton(onClick = { scope.launch { onClick() } }, modifier = Modifier.size(44.dp)) {
        ChevronIcon(
            direction = direction,
            tint = WaqfahTheme.colors.inkMuted,
            modifier = Modifier.size(18.dp).semantics { this.contentDescription = contentDescription },
        )
    }
}

// Smaller, accent-tinted secondary affordance next to the translation text.
@Composable
private fun TranslationSwitchArrow(direction: ChevronDirection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val contentDescription = if (direction == ChevronDirection.LEFT) stringResource(R.string.cd_prev_translation) else stringResource(R.string.cd_next_translation)
    IconButton(onClick = onClick, modifier = modifier.size(28.dp)) {
        ChevronIcon(
            direction = direction,
            tint = WaqfahTheme.colors.accent,
            modifier = Modifier.size(13.dp).semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
private fun MarkReadPill(marked: Boolean, markReadTrigger: Int, verseKey: Any?, onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    val markedReadLabel = stringResource(R.string.cd_marked_read)
    val markReadLabel = stringResource(R.string.mark_read)

    // Swiping to another ayah swaps the marked state too — that swap must SNAP,
    // otherwise the new ayah's pill visibly cross-fades away from the previous
    // ayah's state and reads as lag. Only direct taps get the smooth transition.
    var lastVerseKey by remember { mutableStateOf<Any?>(null) }
    var verseJustChanged by remember { mutableStateOf(false) }
    if (verseKey != lastVerseKey) {
        lastVerseKey = verseKey
        verseJustChanged = true
    } else {
        verseJustChanged = false
    }

    val colorSpec: AnimationSpec<Color> = if (verseJustChanged) snap() else tween(160)
    val alphaSpec: AnimationSpec<Float> = if (verseJustChanged) snap() else tween(160)

    // Pending is the call-to-action (solid accent); marked recedes to soft accent.
    val backgroundColor by animateColorAsState(if (marked) colors.accentSoft else colors.accent, colorSpec, label = "mark_read_bg")
    val contentColor by animateColorAsState(if (marked) colors.accent else colors.accentInk, colorSpec, label = "mark_read_content")

    // Text and icon stay permanently composed in one centered Box; animating
    // only their alpha means nothing moves and each frame is a pure redraw.
    val checkAlpha by animateFloatAsState(if (marked) 1f else 0f, alphaSpec, label = "mark_read_check_alpha")
    val textAlpha by animateFloatAsState(if (marked) 0f else 1f, alphaSpec, label = "mark_read_text_alpha")

    // Bounce on every tap, including repeat taps on an already-marked ayah.
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
            .semantics { contentDescription = if (marked) markedReadLabel else markReadLabel },
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
            // Text keeps the pill's footprint; its semantics are cleared since
            // the Surface announces both states.
            Text(
                markReadLabel,
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

// The bookmark toggle (see CONTEXT.md). A ribbon, not a heart: the action reads
// as "keep this for later", not "like this".
//
// Both ribbon states stay permanently composed in one fixed-size button and
// only their alpha animates — the technique MarkReadPill uses — so the toggle
// never changes width and cannot nudge Mark Read off the centre line as it
// fills and empties. The state itself is never decided here: it arrives in
// `saved`, which the session reads back from the store, so a failed write
// leaves the ribbon showing what is actually saved rather than what the tap
// hoped for.
@Composable
private fun BookmarkToggle(saved: Boolean, verseKey: Any?, onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    val bookmarkLabel = stringResource(R.string.cd_bookmark)
    val savedLabel = stringResource(R.string.cd_bookmark_saved)
    val notSavedLabel = stringResource(R.string.cd_bookmark_not_saved)

    // Same snap-on-ayah-change rule as MarkReadPill: swiping swaps the saved
    // state too, and a cross-fade there would read as the incoming ayah's
    // ribbon animating away from the previous ayah's. Only taps tween.
    var lastVerseKey by remember { mutableStateOf<Any?>(null) }
    var verseJustChanged by remember { mutableStateOf(false) }
    if (verseKey != lastVerseKey) {
        lastVerseKey = verseKey
        verseJustChanged = true
    } else {
        verseJustChanged = false
    }

    val alphaSpec: AnimationSpec<Float> = if (verseJustChanged) snap() else tween(160)
    val filledAlpha by animateFloatAsState(if (saved) 1f else 0f, alphaSpec, label = "bookmark_filled_alpha")
    val outlineAlpha by animateFloatAsState(if (saved) 0f else 1f, alphaSpec, label = "bookmark_outline_alpha")

    IconButton(onClick = onClick, modifier = Modifier.size(ACTION_TOGGLE_SIZE)) {
        // Both ribbons sit in one node whose semantics are replaced wholesale,
        // the way RemArrow hangs its label on the icon rather than on the
        // button: the clickable node merges what is inside it, so a name set
        // on the button's own modifier would sit above the node TalkBack
        // actually focuses. Name and state are announced separately — the name
        // says what the control is, the state says which way it currently
        // points — and only one of the two ribbons ever contributes a word.
        Box(
            Modifier.clearAndSetSemantics {
                contentDescription = bookmarkLabel
                stateDescription = if (saved) savedLabel else notSavedLabel
            },
        ) {
            BookmarkRibbonIcon(
                filled = true,
                tint = colors.accent,
                modifier = Modifier.size(18.dp).alpha(filledAlpha),
            )
            BookmarkRibbonIcon(
                filled = false,
                tint = colors.inkMuted,
                modifier = Modifier.size(18.dp).alpha(outlineAlpha),
            )
        }
    }
}
