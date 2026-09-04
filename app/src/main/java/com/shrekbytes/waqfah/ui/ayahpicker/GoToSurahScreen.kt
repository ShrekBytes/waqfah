package com.shrekbytes.waqfah.ui.ayahpicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.ui.components.ChevronDirection
import com.shrekbytes.waqfah.ui.components.ChevronIcon
import com.shrekbytes.waqfah.ui.components.EmptyListNote
import com.shrekbytes.waqfah.ui.components.WaqfahBackButton
import com.shrekbytes.waqfah.ui.components.WaqfahSearchField
import com.shrekbytes.waqfah.ui.components.skeletonPulseAlpha
import com.shrekbytes.waqfah.ui.reading.ReadingViewModel
import com.shrekbytes.waqfah.ui.reading.ayahWord
import com.shrekbytes.waqfah.ui.reading.localizeDigits
import com.shrekbytes.waqfah.ui.reading.surahDisplayName
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import kotlinx.coroutines.launch

@Composable
fun GoToSurahScreen(
    readingViewModel: ReadingViewModel,
    onBack: () -> Unit,
    onJumped: () -> Unit = onBack,
    viewModel: GoToSurahViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    val scope = rememberCoroutineScope()

    var expandedSurahNo by rememberSaveable { mutableIntStateOf(-1) }
    var ayahInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(expandedSurahNo) { ayahInput = "" }

    // Every jump action (Go / First / Continue) funnels through these two so
    // the "resolve verse → shared ReadingSession.jumpToVerse → pop screen"
    // sequence exists exactly once.
    val jumpToVerseId: suspend (Int) -> Unit = { verseId ->
        readingViewModel.session.jumpToVerse(verseId)
        onJumped()
    }
    val jumpToAyah: suspend (Int, Int) -> Unit = { surahNo, ayahNo ->
        viewModel.getVerse(surahNo, ayahNo)?.let { jumpToVerseId(it.id) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            WaqfahBackButton(onClick = onBack)
            Text(
                stringResource(R.string.goto_surah_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                stringResource(R.string.goto_surah_hint),
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            // Search – forced LTR via WaqfahSearchField itself
            WaqfahSearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.goto_search_hint),
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading -> SurahListSkeleton(Modifier.weight(1f).padding(top = 4.dp))
                state.rows.isEmpty() -> EmptyListNote(stringResource(R.string.goto_no_results))
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    itemsIndexed(state.rows, key = { _, row -> row.surah.surahNo }) { index, row ->
                        val isLast = index == state.rows.lastIndex
                        val isExpanded = row.surah.surahNo == expandedSurahNo
                        val lang = state.surahNameLanguage
                        val total = row.surah.ayahCount
                        // Expansion-only values: collapsed rows skip the input
                        // parsing and string lookups on every recomposition.
                        val parsed = if (isExpanded) ayahInput.toIntOrNull() else null
                        val isOutOfRange = parsed != null && (parsed < 1 || parsed > total)
                        val isValid = parsed != null && parsed in 1..total
                        val rangeLabel = if (isExpanded) stringResource(R.string.goto_ayah_range_fmt, localizeDigits(total, lang)) else ""
                        val currentError: String? = when {
                            !isExpanded || ayahInput.isBlank() -> null
                            parsed == null || isOutOfRange -> rangeLabel
                            else -> null
                        }

                        // Minimal – no card border, just divider between rows (more minimal, matches Settings/Translations lists)
                        Column(
                            Modifier.fillMaxWidth(),
                        ) {
                            // Header – full bleed, minimal click (no ripple)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { expandedSurahNo = if (isExpanded) -1 else row.surah.surahNo },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Surah number
                                    Text(
                                        localizeDigits(row.surah.surahNo, lang),
                                        color = colors.inkMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(30.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides if (lang == NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                                            Text(surahDisplayName(row.surah, lang), color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Text(
                                            "${localizeDigits(total, lang)} ${ayahWord(lang)}",
                                            color = colors.inkMuted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.goto_ayah_progress_fmt, localizeDigits(row.readCount, lang), localizeDigits(total, lang)),
                                        color = colors.inkMuted,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Box(
                                        Modifier.size(20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ChevronIcon(
                                            direction = ChevronDirection.RIGHT,
                                            tint = colors.inkSoft,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .then(
                                                    if (isExpanded) Modifier.rotate(90f) else Modifier
                                                ),
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 8.dp)
                                ) {
                                    // Input + Go side by side – Go is compact primary, input is hairline field
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Input field – weight 1, consistent with WaqfahSearchField but denser
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Row(
                                                Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.background)
                                                    .border(1.dp, if (isOutOfRange) colors.accent else colors.line, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(Modifier.weight(1f)) {
                                                    if (ayahInput.isEmpty()) {
                                                        Text(stringResource(R.string.goto_ayah_field_placeholder), color = colors.inkSoft, fontSize = 13.5.sp)
                                                    }
                                                    BasicTextField(
                                                        value = ayahInput,
                                                        onValueChange = { new ->
                                                            val filtered = new.filter { it.isDigit() }.take(3)
                                                            ayahInput = filtered
                                                        },
                                                        singleLine = true,
                                                        textStyle = TextStyle(
                                                            color = colors.ink,
                                                            fontSize = 13.5.sp,
                                                            textDirection = TextDirection.Ltr,
                                                        ),
                                                        cursorBrush = SolidColor(colors.accent),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                                                        // The keyboard's Go acts like the Go pill —
                                                        // same guarded jump, no separate code path.
                                                        keyboardActions = KeyboardActions(
                                                            onGo = {
                                                                val targetAyah = parsed
                                                                if (targetAyah != null && isValid) scope.launch { jumpToAyah(row.surah.surahNo, targetAyah) }
                                                            },
                                                        ),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }
                                                Text(
                                                    "/ ${localizeDigits(total, lang)}",
                                                    color = colors.inkMuted,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(start = 8.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        // Go – compact pill, matches the app's rounded action idiom
                                        Surface(
                                            onClick = {
                                                val targetAyah = parsed
                                                if (targetAyah == null || !isValid) return@Surface
                                                scope.launch { jumpToAyah(row.surah.surahNo, targetAyah) }
                                            },
                                            enabled = isValid,
                                            shape = RoundedCornerShape(50),
                                            color = if (isValid) colors.accent else colors.accent.copy(alpha = 0.35f),
                                            contentColor = if (isValid) colors.accentInk else colors.accentInk.copy(alpha = 0.7f),
                                            modifier = Modifier.height(42.dp).width(64.dp),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.goto_ayah_go), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    if (currentError != null) {
                                        Text(currentError, color = colors.accent, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    // Two equal quiet pills (matches the app's Grant/chip idiom)
                                    // instead of a clashing two-tone button pair.
                                    Row(Modifier.fillMaxWidth()) {
                                        // First ayah – quiet secondary pill
                                        Surface(
                                            onClick = {
                                                scope.launch { jumpToAyah(row.surah.surahNo, 1) }
                                            },
                                            shape = RoundedCornerShape(50),
                                            color = colors.accentSoft,
                                            contentColor = colors.accent,
                                            modifier = Modifier.weight(1f).height(42.dp),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.goto_ayah_first), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Surface(
                                            onClick = {
                                                scope.launch {
                                                    val readIds = viewModel.getReadIds()
                                                    val verse = viewModel.getFirstUnreadInSurah(row.surah.surahNo, readIds)
                                                        ?: viewModel.getVerse(row.surah.surahNo, 1)
                                                    if (verse != null) jumpToVerseId(verse.id)
                                                }
                                            },
                                            shape = RoundedCornerShape(50),
                                            color = colors.accentSoft,
                                            contentColor = colors.accent,
                                            modifier = Modifier.weight(1f).height(42.dp),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.goto_ayah_continue), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                            if (!isLast) HorizontalDivider(color = colors.line.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

// Loading skeleton sized to match a compact row so nothing visibly jumps when
// real rows swap in. Shares the app-wide pulsing rhythm (see SkeletonPulse.kt).
@Composable
private fun SurahListSkeleton(modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    val pulseAlpha = skeletonPulseAlpha()
    val barColor = colors.line.copy(alpha = pulseAlpha)

    Column(modifier) {
        repeat(10) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(Modifier.width(26.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                Box(Modifier.width(36.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
            }
        }
    }
}
