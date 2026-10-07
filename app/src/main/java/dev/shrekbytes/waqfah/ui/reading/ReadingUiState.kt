package dev.shrekbytes.waqfah.ui.reading

import androidx.compose.ui.unit.LayoutDirection
import dev.shrekbytes.waqfah.data.model.ArabicFont
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage
import dev.shrekbytes.waqfah.data.model.ReadingMode

// The subset of an ayah's rendered content needed to peek at a neighbouring
// ayah mid-swipe (see AyahPeekPage in ReadingCard.kt): no surah header and no
// translation-switcher state — those belong to the ayah actually being read.
data class AyahPreview(
    val ayahLabel: String,
    val arabicText: String,
    val arabicFont: ArabicFont,
    val arabicFontSize: Int,
    val translitText: String?,
    val translitFontSize: Int,
    val translationText: String?,
    val translationFontSize: Int,
)

data class ReadingUiState(
    val isLoading: Boolean = true,
    // The sequence this session walks has nothing to show. Only a
    // collection-scoped session can reach this — an empty bookmark collection
    // is a state to present, not a failure (ADR-0005), while the mushaf
    // always has an ayah. The card renders its empty-state message rather
    // than a blank one; every other field describes a verse that isn't there.
    val isEmpty: Boolean = false,
    val surahName: String = "",
    val surahNameDirection: LayoutDirection = LayoutDirection.Ltr,
    // The language the header is rendered in. The card localises the header's
    // numeric line itself — the surah's ayah count arrives pre-formatted as
    // totalLabel, but the collection total does not — and localizeDigits needs
    // the language to do it.
    val surahNameLanguage: NameDisplayLanguage = NameDisplayLanguage.ENGLISH,
    val ayahLabel: String = "",
    val totalLabel: String = "",
    val arabicText: String = "",
    val arabicFont: ArabicFont = ArabicFont.DIGITAL_KHATT_INDOPAK,
    val arabicFontSize: Int = 26,
    val translitText: String? = null,
    val translitFontSize: Int = 18,
    val translationText: String? = null,
    val translationFontSize: Int = 18,
    // Name of whichever translation translationText currently shows — the
    // user's default unless cycleTranslationSource() swapped it for this ayah.
    val translationSourceName: String? = null,
    // Whether another downloaded translation exists to compare against.
    val translationHasAlternates: Boolean = false,
    val isMarkedRead: Boolean = false,
    // Monotonic count of successful mark-read actions this session made —
    // the tour's TryIt MARK_READ step detects marks through it (#33): with
    // auto-next on, the card moves off the marked ayah at once, so the
    // transient isMarkedRead flip can be conflated away before the tour's
    // facts feed sees it, while the counter rides every later emission.
    // Unmarks never count. Reset only by the empty state, which no tour
    // host reaches.
    val markReadCount: Int = 0,
    // Whether the current ayah is in the bookmark collection. Unlike
    // isMarkedRead this is never flipped ahead of the write: the collection is
    // a lookup surface, so the toggle reflects what the store holds (ADR-0005).
    val isSaved: Boolean = false,
    // How many ayahs the collection holds — the Bookmarks header's second line
    // (see ReadingCard). Published from the collection's own emissions rather
    // than from render(), so saving an ayah updates the header without a swipe
    // paying for the count.
    val savedCount: Int = 0,
    // Every ayah marked read — drives the completion popup.
    val isCompleted: Boolean = false,
    // Auto-next's handshake (#33): the session raised an advance request for
    // the ayah on screen — the card owns the timing, playing the mark
    // confirmation and then firing next() itself. Any committed step clears
    // it, so a manual move supersedes the request; only auto-next-on raises
    // it, so the choreography is invisible while the setting is off.
    val pendingAutoAdvance: Boolean = false,
    // Active mode, echoed here so the completion popup can label its
    // switch-to-the-other-mode action.
    val readingMode: ReadingMode = ReadingMode.SEQUENTIAL,
    val triggeredAppLabel: String? = null, // non-null only on the trigger screen
    // Neighbouring ayahs, kept ready so a swipe reveals real content instead
    // of a blank gap; refreshed on every render().
    val nextPreview: AyahPreview? = null,
    val previousPreview: AyahPreview? = null,
)
