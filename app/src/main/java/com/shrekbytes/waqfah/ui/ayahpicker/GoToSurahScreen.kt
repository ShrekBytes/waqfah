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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.EmptyListNote
import com.shrekbytes.waqfah.ui.components.WaqfahBackButton
import com.shrekbytes.waqfah.ui.components.WaqfahSearchField
import com.shrekbytes.waqfah.ui.reading.localizeDigits
import com.shrekbytes.waqfah.ui.reading.surahDisplayName
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import kotlinx.coroutines.launch

@Composable
fun GoToSurahScreen(
    readingViewModel: com.shrekbytes.waqfah.ui.reading.ReadingViewModel,
    onBack: () -> Unit,
    onJumped: () -> Unit = onBack,
    viewModel: GoToSurahViewModel = hiltViewModel(),
    ayahViewModel: GoToAyahViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    val scope = rememberCoroutineScope()

    var expandedSurahNo by rememberSaveable { mutableIntStateOf(-1) }
    var ayahInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(expandedSurahNo) { ayahInput = "" }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            WaqfahBackButton(onClick = onBack)
            Text(
                stringResource(R.string.goto_surah_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
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
                state.isLoading -> {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.goto_search_hint), color = colors.inkSoft, fontSize = 13.sp)
                }
                state.rows.isEmpty() -> EmptyListNote(stringResource(R.string.goto_no_results))
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                ) {
                    items(state.rows, key = { it.surah.surahNo }) { row ->
                        val isExpanded = row.surah.surahNo == expandedSurahNo
                        val lang = state.surahNameLanguage
                        val total = row.surah.ayahCount
                        val parsed = ayahInput.toIntOrNull()
                        val isOutOfRange = isExpanded && parsed != null && (parsed < 1 || parsed > total)
                        val isValid = isExpanded && parsed != null && parsed in 1..total
                        val rangeLabel = if (isExpanded && total > 0) stringResource(R.string.goto_ayah_range_fmt, localizeDigits(total, lang)) else ""
                        val currentError: String? = when {
                            !isExpanded || ayahInput.isBlank() -> null
                            parsed == null -> rangeLabel
                            isOutOfRange -> rangeLabel
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
                                        CompositionLocalProvider(LocalLayoutDirection provides if (lang == com.shrekbytes.waqfah.data.model.NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                                            Text(surahDisplayName(row.surah, lang), color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Text(
                                            "${localizeDigits(total, lang)} ${com.shrekbytes.waqfah.ui.reading.ayahWord(lang)}",
                                            color = colors.inkMuted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.goto_ayah_progress_fmt, localizeDigits(row.readCount, lang), localizeDigits(row.total, lang)),
                                        color = colors.inkMuted,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Box(
                                        Modifier.size(20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        com.shrekbytes.waqfah.ui.components.ChevronIcon(
                                            direction = com.shrekbytes.waqfah.ui.components.ChevronDirection.RIGHT,
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
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        // Go – side to box, compact primary pill (matches WaqfahPrimaryButton but smaller)
                                        Surface(
                                            onClick = {
                                                if (!isValid || parsed == null) return@Surface
                                                scope.launch {
                                                    val verse = ayahViewModel.getVerse(row.surah.surahNo, parsed)
                                                    if (verse != null) {
                                                        readingViewModel.jumpToVerse(verse.id)
                                                        onJumped()
                                                    }
                                                }
                                            },
                                            enabled = isValid,
                                            shape = RoundedCornerShape(12.dp),
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
                                    // Two secondary actions – First ayah + Continue (last read)
                                    Row(Modifier.fillMaxWidth()) {
                                        // First ayah – secondary (accentSoft) to stay subtle, matches onboarding Grant style
                                        Surface(
                                            onClick = {
                                                scope.launch {
                                                    val verse = ayahViewModel.getVerse(row.surah.surahNo, 1)
                                                    if (verse != null) {
                                                        readingViewModel.jumpToVerse(verse.id)
                                                        onJumped()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(50),
                                            color = colors.accentSoft,
                                            contentColor = colors.accent,
                                            modifier = Modifier.weight(1f).height(44.dp),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.goto_ayah_first), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Surface(
                                            onClick = {
                                                scope.launch {
                                                    val readIds = ayahViewModel.getReadIds()
                                                    val verse = ayahViewModel.getFirstUnreadInSurah(row.surah.surahNo, readIds)
                                                        ?: ayahViewModel.getVerse(row.surah.surahNo, 1)
                                                    if (verse != null) {
                                                        readingViewModel.jumpToVerse(verse.id)
                                                        onJumped()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(50),
                                            color = colors.accent,
                                            contentColor = colors.accentInk,
                                            modifier = Modifier.weight(1f).height(44.dp),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.goto_ayah_continue), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.line.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
