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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ColorFilter
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
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import com.shrekbytes.waqfah.ui.reading.ReadingViewModel
import com.shrekbytes.waqfah.ui.reading.WaqfahReadingContent
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

private const val TOUR_LINK_TAG = "tour_translation_link"

private enum class TaskKind { MARK_READ, CHANGE_AYAH, SWITCH_TRANSLATION, GO_TO_AYAH }

// One stop of the feature tour. Flow shows how Waqfah works as a
// blockquote-style chain, TryIt asks the user to perform the real action on the live
// reading card, SettingRows/Checklist visualize where things live.
private sealed interface TourStep {
    val titleRes: Int

    data class Flow(
        @StringRes override val titleRes: Int,
        val steps: List<Int>,
    ) : TourStep

    data class Info(
        @StringRes override val titleRes: Int,
        @StringRes val bodyRes: Int,
        val icon: ImageVector,
    ) : TourStep

    data class TryIt(
        val kind: TaskKind,
        @StringRes override val titleRes: Int,
        @StringRes val bodyRes: Int,
    ) : TourStep

    data class SettingRows(
        @StringRes override val titleRes: Int,
        @StringRes val hintRes: Int,
        val icon: ImageVector,
        val rows: List<SettingRow>,
    ) : TourStep
}

private data class SettingRow(
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
)

private val TOUR_STEPS = listOf<TourStep>(
    // The whole idea as a scannable chain instead of a paragraph.
    TourStep.Flow(
        R.string.tour_flow_title,
        listOf(
            R.string.tour_f1,
            R.string.tour_f2,
            R.string.tour_f3,
            R.string.tour_f4,
            R.string.tour_f5,
        ),
    ),
    TourStep.TryIt(TaskKind.MARK_READ, R.string.tour_t_mark_title, R.string.tour_t_mark_body),
    TourStep.TryIt(TaskKind.CHANGE_AYAH, R.string.tour_t_move_title, R.string.tour_t_move_body),
    TourStep.TryIt(TaskKind.SWITCH_TRANSLATION, R.string.tour_t_trans_title, R.string.tour_t_trans_body),
    TourStep.TryIt(TaskKind.GO_TO_AYAH, R.string.tour_t_goto_title, R.string.tour_t_goto_body),
    TourStep.SettingRows(
        R.string.tour_set_title,
        R.string.tour_set_hint,
        Icons.Filled.Settings,
        listOf(
            SettingRow(R.string.tour_r_mode_t, R.string.tour_r_mode_d),
            SettingRow(R.string.tour_r_script_t, R.string.tour_r_script_d),
            SettingRow(R.string.tour_r_size_t, R.string.tour_r_size_d),
            SettingRow(R.string.tour_r_trans_t, R.string.tour_r_trans_d),
        ),
    ),
    // Check (not a more "celebratory" icon): this closing step is really just
    // pointing at where to find FAQ/troubleshooting, so it should read as
    // "you're set up" rather than promise something more than that.
    TourStep.Info(R.string.tour_p5_title, R.string.tour_p5_body, Icons.Filled.Check),
)

// Full-screen overlay hosting the guided tour. Rendered ONLY over the Home tab
// of MainActivity (see MainScreen) — never over TriggerActivity's interstitial.
// The TryIt steps embed the REAL home reading card (same ReadingViewModel as
// the Home tab), so what the user practices here is the actual thing. Steps
// navigate via AnimatedContent rather than HorizontalPager on purpose: the
// pager's own horizontal drag would steal the card's swipe-to-change-ayah
// gesture. onFinish marks the tour completed so it never auto-shows again;
// onSkip keeps it incomplete so it re-offers next launch.
@Composable
fun FeatureTourOverlay(
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onBrowseTranslations: () -> Unit = {},
    onGoToSurah: () -> Unit = {},
    viewModel: ReadingViewModel = hiltViewModel(),
) {
    val colors = WaqfahTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val isLast = stepIndex == TOUR_STEPS.lastIndex

    // Back goes a step back; from the first step it dismisses early (a skip).
    BackHandler {
        if (stepIndex > 0) stepIndex-- else onSkip()
    }

    // Baselines for detecting a completed hands-on task, snapshotted whenever
    // an interactive step becomes current (and re-snapshotted once loading
    // resolves, since the first snapshot would otherwise capture blank state).
    var anchorAyah by remember { mutableStateOf<String?>(null) }
    var anchorTranslation by remember { mutableStateOf<String?>(null) }
    // GO_TO_AYAH trackers must survive the MainScreen disposal that happens
    // while the Go-to screen is pushed over it (Navigation3 disposes covered
    // entries — see MainScreen's rememberSaveable note): plain remember{}
    // silently reset mid-task, so a real jump could never mark the step done.
    var goToHeaderTapped by rememberSaveable { mutableStateOf(false) }
    var goToAnchorAyah by rememberSaveable { mutableStateOf<String?>(null) }
    var goToResetStep by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(stepIndex, state.isLoading) {
        if (!state.isLoading && TOUR_STEPS[stepIndex] is TourStep.TryIt) {
            anchorAyah = state.ayahLabel
            anchorTranslation = state.translationSourceName ?: state.translationText
        }
    }
    // Reset the GO_TO_AYAH trackers only when the step genuinely becomes
    // current. The LaunchedEffect above re-runs on the fresh recomposition
    // after returning from the Go-to screen too (same stepIndex), so the reset
    // can't live there — it would wipe the trackers right after a jump.
    if (goToResetStep != stepIndex) {
        goToResetStep = stepIndex
        goToHeaderTapped = false
        goToAnchorAyah = null
    }
    // Wrapper for tour's header tap – marks that user actually tried Go-to, so
    // swipe-only ayah change doesn't falsely complete the Go-to step. The ayah
    // baseline is captured HERE (pre-jump, pre-disposal); snapshotting it after
    // return would record the already-jumped ayah and defeat the comparison.
    val onGoToSurahForTour: () -> Unit = {
        goToAnchorAyah = state.ayahLabel
        goToHeaderTapped = true
        onGoToSurah()
    }
    val taskDone = when ((TOUR_STEPS[stepIndex] as? TourStep.TryIt)?.kind) {
        TaskKind.MARK_READ -> !state.isLoading && state.isMarkedRead
        TaskKind.CHANGE_AYAH -> anchorAyah != null && state.ayahLabel != anchorAyah
        TaskKind.GO_TO_AYAH -> goToHeaderTapped && goToAnchorAyah != null && state.ayahLabel != goToAnchorAyah
        TaskKind.SWITCH_TRANSLATION ->
            anchorTranslation != null && (state.translationSourceName ?: state.translationText) != anchorTranslation
        null -> false
    }

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
                    HeaderDots(selected = stepIndex, count = TOUR_STEPS.size)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSkip, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.tour_skip), color = colors.inkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                AnimatedContent(
                    targetState = stepIndex,
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
                            done = index == stepIndex && taskDone,
                            isTranslationDisabled = step.kind == TaskKind.SWITCH_TRANSLATION && state.translationText == null && !state.isLoading,
                            showTranslationFallback = step.kind == TaskKind.SWITCH_TRANSLATION && state.translationText != null && !state.translationHasAlternates,
                            onBrowseTranslations = onBrowseTranslations,
                            onGoToSurah = onGoToSurahForTour,
                            viewModel = viewModel,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Back lives beside the primary action where thumb navigation
                // already happens; Skip alone stays up in the header.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stepIndex > 0) {
                        TextButton(
                            onClick = { stepIndex-- },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.tour_back), color = colors.inkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        WaqfahPrimaryButton(
                            text = stringResource(if (isLast) R.string.tour_finish else R.string.tour_next),
                            onClick = { if (isLast) onFinish() else stepIndex++ },
                            modifier = Modifier.weight(2f),
                        )
                    } else {
                        WaqfahPrimaryButton(
                            text = stringResource(if (isLast) R.string.tour_finish else R.string.tour_next),
                            onClick = { if (isLast) onFinish() else stepIndex++ },
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
@Composable
private fun TryItPage(
    step: TourStep.TryIt,
    done: Boolean,
    isTranslationDisabled: Boolean,
    showTranslationFallback: Boolean,
    onBrowseTranslations: () -> Unit,
    viewModel: ReadingViewModel,
    onGoToSurah: () -> Unit = {},
) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxSize()) {
        // The live practice sandbox: the actual home reading card.
        // For GO_TO_AYAH we wire the header tap so the tour step is truly interactive.
        val isGoToStep = step.kind == TaskKind.GO_TO_AYAH
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.line.copy(alpha = 0.3f)),
        ) {
            WaqfahReadingContent(
                viewModel = viewModel,
                onGoToAyah = if (isGoToStep) onGoToSurah else null,
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
