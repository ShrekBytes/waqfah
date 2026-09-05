package com.shrekbytes.waqfah.ui.tour

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.ayahpicker.GoToSurahScreen
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import com.shrekbytes.waqfah.ui.reading.ReadingViewModel
import com.shrekbytes.waqfah.ui.reading.WaqfahReadingContent
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

private const val TOUR_LINK_TAG = "tour_translation_link"

// Full-screen overlay hosting the guided tour — the tour machine's rendering
// adapter. Steps, TryIt completion, back ordering and dismissal live in
// TourSession (see CONTEXT.md); this composable pushes it the reading card's
// facts and renders its uiState. Rendered ONLY over the Home tab of
// MainActivity (see MainScreen) — never over TriggerActivity's interstitial.
// The TryIt steps embed the REAL home reading card (same ReadingViewModel as
// the Home tab), so what the user practices here is the actual thing; the
// Go-to step opens the REAL surah/ayah picker (GoToSurahScreen) INSIDE the
// sandbox instead of pushing a full screen over the tour, so the tour never
// gets disposed mid-step. Steps navigate via AnimatedContent rather than
// HorizontalPager on purpose: the pager's own horizontal drag would steal the
// card's swipe-to-change-ayah gesture.
@Composable
fun FeatureTourOverlay(
    tourSession: TourSession,
    onBrowseTranslations: () -> Unit = {},
    viewModel: ReadingViewModel = hiltViewModel(),
) {
    val colors = WaqfahTheme.colors
    val state by viewModel.session.uiState.collectAsStateWithLifecycle()
    val tour by tourSession.uiState.collectAsStateWithLifecycle()

    // The machine's only input from the card: raw facts; derivation (anchors,
    // the shown translation, the switch hints) happens in the session.
    LaunchedEffect(state) {
        tourSession.onReadingState(
            TourReadingFacts(
                isLoading = state.isLoading,
                ayahLabel = state.ayahLabel,
                translationSourceName = state.translationSourceName,
                translationText = state.translationText,
                isMarkedRead = state.isMarkedRead,
                hasTranslationAlternates = state.translationHasAlternates,
            ),
        )
    }

    // Back closes the in-sandbox Go-to picker first, then goes a step back;
    // from the first step it dismisses early (a skip) — the session decides.
    // GoToSurahScreen has no BackHandler of its own, so this one catches back
    // while the picker shows.
    BackHandler { tourSession.back() }

    val isLast = tour.stepIndex == TOUR_STEPS.lastIndex

    // Dimmer swallows taps so nothing underneath reacts while touring.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.background,
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 22.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                // Header: round-dot progress track + dismiss controls.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderDots(selected = tour.stepIndex, count = TOUR_STEPS.size)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = tourSession::skip, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.tour_skip), color = colors.inkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                AnimatedContent(
                    targetState = tour.stepIndex,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 1.03f, animationSpec = tween(140)))
                    },
                    label = "tour_step_content",
                ) { index ->
                    when (val step = TOUR_STEPS[index]) {
                        is TourStep.Flow -> FlowPage(step)
                        is TourStep.Info -> InfoPage(step)
                        is TourStep.SettingRows -> SettingsPage(step)
                        is TourStep.TryIt -> TryItPage(
                            step = step,
                            done = index == tour.stepIndex && tour.taskDone,
                            isTranslationDisabled = step.kind == TourTaskKind.SWITCH_TRANSLATION && tour.isTranslationDisabled,
                            showTranslationFallback = step.kind == TourTaskKind.SWITCH_TRANSLATION && tour.showTranslationFallback,
                            onBrowseTranslations = onBrowseTranslations,
                            showGoToPicker = tour.goToPickerOpen,
                            onOpenPicker = tourSession::openGoToPicker,
                            onClosePicker = tourSession::closeGoToPicker,
                            onJumpedInPicker = tourSession::onJumpedInPicker,
                            viewModel = viewModel,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Back lives beside the primary action where thumb navigation
                // already happens; Skip alone stays up in the header.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tour.stepIndex > 0) {
                        TextButton(
                            onClick = { tourSession.back() },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.tour_back), color = colors.inkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        WaqfahPrimaryButton(
                            text = stringResource(if (isLast) R.string.tour_finish else R.string.tour_next),
                            onClick = { tourSession.next() },
                            modifier = Modifier.weight(2f),
                        )
                    } else {
                        WaqfahPrimaryButton(
                            text = stringResource(if (isLast) R.string.tour_finish else R.string.tour_next),
                            onClick = { tourSession.next() },
                        )
                    }
                }
            }
        }
    }
}

// Round progress dots for the header: the current stop is a larger filled
// accent dot, past stops are solid muted dots, upcoming ones are neutral.
@Composable
private fun HeaderDots(selected: Int, count: Int) {
    val colors = WaqfahTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val isCurrent = index == selected
            val isPast = index < selected
            Spacer(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (isCurrent) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCurrent -> colors.accent
                            isPast -> colors.accent.copy(alpha = 0.45f)
                            else -> colors.line
                        },
                    ),
            )
        }
    }
}

// The whole concept as a scannable chain. Everything readable — title,
// summary line, then the chain — lives inside the shared soft-accent card;
// only the app's logo mark floats just above it (outside the card).
@Composable
private fun FlowPage(step: TourStep.Flow) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // The app's own logo mark (monochrome vector), tinted like every
            // other themed element. No circle — the mark speaks for itself.
            Image(
                painter = painterResource(R.drawable.ic_logo_mark),
                contentDescription = stringResource(R.string.waqfah_logo_cd),
                colorFilter = ColorFilter.tint(colors.accent),
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        TourInstructionCard {
            Text(stringResource(step.titleRes), color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tour_flow_hint),
                color = colors.ink.copy(alpha = 0.72f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            // Plain blockquote: just the rail bar and the chain hanging off it.
            // IntrinsicSize.Min keeps the rail hugging the chain.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.accent.copy(alpha = 0.5f), CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    step.steps.forEach { labelRes ->
                        Text(
                            stringResource(labelRes),
                            color = colors.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPage(step: TourStep.Info) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Bare icon — no circle halo — matching page 1's plain logo mark.
            Icon(step.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        TourInstructionCard {
            Text(stringResource(step.titleRes), color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(step.bodyRes), color = colors.ink.copy(alpha = 0.72f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

// The reading-settings map: every entry scans visually via accent dots instead
// of reading as a wall of text — all inside the shared card; the settings
// glyph floats just above it.
@Composable
private fun SettingsPage(step: TourStep.SettingRows) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Bare icon — no circle halo — matching page 1's plain logo mark.
            Icon(step.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        TourInstructionCard {
            Text(stringResource(step.titleRes), color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(stringResource(step.hintRes), color = colors.inkMuted, fontSize = 12.sp)
            // No explicit spacer here: each row already carries 7dp top padding,
            // so an added gap stacks on top of it and detaches the list from
            // the header (the bug this replaces had 8dp + 7dp = ~15dp+leading).
            step.rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(row.labelRes), color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(1.dp))
                        Text(stringResource(row.descRes), color = colors.inkMuted, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

// Practice sandbox. The instruction banner sits BELOW the card so it rests
// right next to the Next button, and its trailing slot is fixed-size: the
// completion tick appears inside a reserved box, so nothing shifts when it
// pops (and a bare tick can't be mistaken for a pressable Done button).
// For GO_TO_AYAH with the picker open, the REAL GoToSurahScreen replaces the
// whole step content (same screen the Home header pushes, sharing the same
// ReadingViewModel): its back button and a successful jump both close it back
// onto the reading card, which immediately shows the jumped-to ayah. The
// instruction card is intentionally hidden while the picker is open — it
// already did its job (told the user to tap the header) and the picker needs
// the room.
@Composable
private fun TryItPage(
    step: TourStep.TryIt,
    done: Boolean,
    isTranslationDisabled: Boolean,
    showTranslationFallback: Boolean,
    onBrowseTranslations: () -> Unit,
    viewModel: ReadingViewModel,
    showGoToPicker: Boolean = false,
    onOpenPicker: () -> Unit = {},
    onClosePicker: () -> Unit = {},
    onJumpedInPicker: () -> Unit = {},
) {
    val colors = WaqfahTheme.colors
    val isGoToStep = step.kind == TourTaskKind.GO_TO_AYAH

    if (isGoToStep && showGoToPicker) {
        GoToSurahScreen(
            readingViewModel = viewModel,
            onBack = onClosePicker,
            onJumped = onJumpedInPicker,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        // The live practice sandbox: the actual home reading card.
        // For GO_TO_AYAH we wire the header tap so the tour step is truly interactive.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.line.copy(alpha = 0.3f)),
        ) {
            WaqfahReadingContent(
                viewModel = viewModel,
                onGoToAyah = if (isGoToStep) onOpenPicker else null,
                bottomBar = {},
            )
        }

        // Translation switch hint: two distinct cases sharing the same link style.
        // - Disabled (translationText == null): prompt to enable translations.
        // - Single translation: prompt to download more to switch.
        if (isTranslationDisabled) {
            val hint = buildAnnotatedString {
                append(stringResource(R.string.tour_t_trans_disabled))
                append(' ')
                pushStringAnnotation(TOUR_LINK_TAG, TOUR_LINK_TAG)
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) {
                    append(stringResource(R.string.tour_t_trans_disabled_cta))
                }
                pop()
            }
            TranslationTourHint(hint = hint, onBrowseTranslations = onBrowseTranslations)
        } else if (showTranslationFallback) {
            // Only the highlighted middle span opens Settings — the leading
            // words and the trailing "to switch." stay inert.
            val hint = buildAnnotatedString {
                append(stringResource(R.string.tour_t_trans_none))
                append(' ')
                pushStringAnnotation(TOUR_LINK_TAG, TOUR_LINK_TAG)
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) {
                    append(stringResource(R.string.tour_t_trans_none_cta))
                }
                pop()
                append(' ')
                append(stringResource(R.string.tour_t_trans_none_suffix))
            }
            TranslationTourHint(hint = hint, onBrowseTranslations = onBrowseTranslations)
        }

        Spacer(Modifier.height(10.dp))
        TourInstructionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(step.titleRes), color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(step.bodyRes), color = colors.ink.copy(alpha = 0.72f), fontSize = 12.sp, lineHeight = 16.sp)
                }
                // Reserved 22dp slot: the tick never changes layout, and being
                // a plain glyph (not a pill/button) reads as status, not CTA.
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.tour_task_done), tint = colors.accent, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationTourHint(
    hint: AnnotatedString,
    onBrowseTranslations: () -> Unit,
) {
    val colors = WaqfahTheme.colors
    var hintLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Spacer(Modifier.height(6.dp))
    Text(
        hint,
        color = colors.inkMuted,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
        onTextLayout = { hintLayout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onBrowseTranslations) {
                detectTapGestures { position ->
                    hintLayout?.let { layout ->
                        val offset = layout.getOffsetForPosition(position)
                        if (hint.getStringAnnotations(TOUR_LINK_TAG, offset, offset).isNotEmpty()) {
                            onBrowseTranslations()
                        }
                    }
                }
            }
            .padding(vertical = 2.dp),
    )
}

// The one container shared by EVERY tour stop: a rounded soft-accent card
// pinned just above the Back/Next row, holding whatever the user should read
// first on that page. Pages keep only a decorative icon floating above it —
// everything readable goes in here, so the eye always lands on one obvious
// place per stop.
@Composable
private fun TourInstructionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WaqfahTheme.colors.accentSoft,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            content = content,
        )
    }
}
