package dev.shrekbytes.waqfah.ui.bookmarks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.data.model.ArabicFont
import dev.shrekbytes.waqfah.data.model.ArabicScript
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage
import dev.shrekbytes.waqfah.ui.components.BookmarkEmptyState
import dev.shrekbytes.waqfah.ui.components.ChevronDirection
import dev.shrekbytes.waqfah.ui.components.ChevronIcon
import dev.shrekbytes.waqfah.ui.components.WaqfahBackButton
import dev.shrekbytes.waqfah.ui.components.rowHighlight
import dev.shrekbytes.waqfah.ui.components.skeletonPulseAlpha
import dev.shrekbytes.waqfah.ui.reading.BookmarksViewModel
import dev.shrekbytes.waqfah.ui.reading.arabicTextFor
import dev.shrekbytes.waqfah.ui.reading.ayahWord
import dev.shrekbytes.waqfah.ui.reading.localizeDigits
import dev.shrekbytes.waqfah.ui.reading.surahDisplayName
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dev.shrekbytes.waqfah.ui.theme.toFontFamily
import kotlinx.coroutines.launch

// The Bookmarks list (see CONTEXT.md): the collection as browsable rows, opened
// from the Bookmarks card's header. A real pushed screen, like the surah picker
// — it gets system back and survives process death.
//
// [bookmarksViewModel] is the BOOKMARKS tab's session, not the shared Home one,
// and that is the whole reason it is a parameter here. The surah picker takes
// the Home session because that is the card it retargets; copying that signature
// verbatim would make a row tap move the Home card instead. The host hoists both
// sessions for exactly this reason (see WaqfahNavDisplay).
@Composable
fun BookmarksListScreen(
    bookmarksViewModel: BookmarksViewModel,
    onBack: () -> Unit,
    onJumped: () -> Unit = onBack,
    viewModel: BookmarksListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    val scope = rememberCoroutineScope()

    // One expanded surah at a time, like the picker: the list is a way of
    // getting somewhere, not a place to read. Saveable so it survives rotation.
    var expandedSurahNo by rememberSaveable { mutableIntStateOf(-1) }

    // The jump funnels through here so "retarget the Bookmarks card → pop" has
    // exactly one definition.
    val jumpToVerseId: suspend (Int) -> Unit = { verseId ->
        bookmarksViewModel.session.jumpToVerse(verseId)
        onJumped()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            WaqfahBackButton(onClick = onBack)
            // Title, with the collection's size on the right. Deliberately a
            // step above the per-surah "N saved" each row carries (12.5sp
            // Medium): the two are the same wording about different scopes, so
            // size and weight are what tell the reader which is the total and
            // which is one surah's share.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.bookmarks_list_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (state.savedTotal > 0) {
                    Text(
                        stringResource(R.string.bookmarks_saved_count_fmt, localizeDigits(state.savedTotal, state.surahNameLanguage)),
                        color = colors.inkMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            when {
                state.isLoading -> BookmarksListSkeleton(Modifier.weight(1f))
                // The same explanation the card shows — one definition of what a
                // bookmark is, shared rather than restated.
                state.rows.isEmpty() -> BookmarkEmptyState(Modifier.weight(1f).fillMaxWidth())
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    itemsIndexed(state.rows, key = { _, row -> row.surah.surahNo }) { index, row ->
                        val isLast = index == state.rows.lastIndex
                        val isExpanded = row.surah.surahNo == expandedSurahNo
                        Column(Modifier.fillMaxWidth()) {
                            SurahRowHeader(
                                row = row,
                                lang = state.surahNameLanguage,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedSurahNo = if (isExpanded) -1 else row.surah.surahNo
                                },
                            )
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                // No horizontal padding on the block: each row owns
                                // its own inset, so the highlight it draws and the
                                // hairline between rows come out the same width.
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, bottom = 12.dp),
                                ) {
                                    row.savedAyahs.forEachIndexed { ayahIndex, verse ->
                                        // A hairline between consecutive saved ayahs of
                                        // the same surah: they are separate rows that
                                        // happen to look alike, and without it the block
                                        // reads as one run of text. Inset to the rows'
                                        // own box — 26dp left, 14dp right — so the rule
                                        // and the press highlight are the same width and
                                        // blend, and never drawn after the last.
                                        if (ayahIndex > 0) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 26.dp, end = 14.dp),
                                                color = colors.line.copy(alpha = 0.5f),
                                            )
                                        }
                                        SavedAyahRow(
                                            arabicText = verse.arabicTextFor(state.arabicScript),
                                            ayahNo = verse.ayahNo,
                                            lang = state.surahNameLanguage,
                                            font = state.arabicFont,
                                            onClick = { scope.launch { jumpToVerseId(verse.id) } },
                                        )
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

// A surah the reader has something in: number · name / total · saved · chevron.
// The chevron expands in place — it never navigates.
@Composable
private fun SurahRowHeader(
    row: BookmarkSurahRow,
    lang: NameDisplayLanguage,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = WaqfahTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                localizeDigits(row.surah.surahNo, lang),
                color = colors.inkMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(30.dp),
            )
            Column(Modifier.weight(1f)) {
                // The surah name follows the reader's name language, and an
                // Arabic name reads right-to-left.
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (lang == NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    Text(
                        surahDisplayName(row.surah, lang),
                        color = colors.ink,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    "${localizeDigits(row.surah.ayahCount, lang)} ${ayahWord(lang)}",
                    color = colors.inkMuted,
                    fontSize = 12.sp,
                )
            }
            Text(
                stringResource(R.string.bookmarks_saved_count_fmt, localizeDigits(row.savedCount, lang)),
                color = colors.inkMuted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                ChevronIcon(
                    direction = ChevronDirection.RIGHT,
                    tint = colors.inkSoft,
                    modifier = Modifier
                        .size(12.dp)
                        .then(if (isExpanded) Modifier.rotate(90f) else Modifier),
                )
            }
        }
    }
}

// One saved ayah inside an expanded surah: a single line of the reader's own
// script, truncated so a long ayah can't dominate the list, with its ayah
// number on the right. Tapping it is the jump.
//
// rowHighlight is the app's single press treatment for flat list rows: no clip,
// no border, no second treatment invented for this screen.
//
// The row is a child of the surah above it, so its box is inset further on the
// left than the surah's content — 26dp against 14dp — while its right edge lines
// up with the surah's: nested on one side, flush on the other. That inset sits
// *before* rowHighlight, so the highlight itself is that box, and the hairline
// between ayahs is inset to the same two values, so the pressed row and the rule
// are the same width and blend. The 10dp inside is what keeps the text off the
// highlight's own edges; flush text there reads as a mistake.
//
// What must not come back is wrapping the helper in clip(RoundedCornerShape(…)),
// which an earlier pass did: that turns the highlight into a pill and it stops
// matching every other row in the app.
@Composable
private fun SavedAyahRow(
    arabicText: String,
    ayahNo: Int,
    lang: NameDisplayLanguage,
    font: ArabicFont,
    onClick: () -> Unit,
) {
    val colors = WaqfahTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 14.dp)
            .rowHighlight(onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Box carries the weight: CompositionLocalProvider takes no modifier,
            // and the text must stay a weighted Row child to ellipsise.
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        arabicText,
                        color = colors.ink,
                        fontFamily = font.toFontFamily(),
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                localizeDigits(ayahNo, lang),
                color = colors.inkMuted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Loading skeleton sized to match a surah row so nothing visibly jumps when real
// rows swap in. Shares the app-wide pulsing rhythm (see SkeletonPulse.kt).
@Composable
private fun BookmarksListSkeleton(modifier: Modifier = Modifier) {
    val colors = WaqfahTheme.colors
    val pulseAlpha = skeletonPulseAlpha()
    val barColor = colors.line.copy(alpha = pulseAlpha)

    Column(modifier) {
        repeat(8) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(Modifier.width(26.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                Box(Modifier.width(44.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
            }
        }
    }
}
