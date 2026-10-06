package dev.shrekbytes.waqfah

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import dev.shrekbytes.waqfah.ui.reading.ReadingCard
import dev.shrekbytes.waqfah.ui.reading.ReadingUiState
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme
import org.junit.Rule
import org.junit.Test

// What the reading card announces, and nothing else.
//
// This is the codebase's only Compose test, and it is deliberately narrow. The
// card's *behaviour* is already covered where it lives — the session and store
// seams have JVM suites — so a test that tapped a control and asserted a
// callback would buy nothing. What no JVM test can reach is the announcement:
// which node TalkBack focuses, what name it carries, and whether the state is
// stated separately from the name.
//
// That gap is real rather than theoretical. Every control that carries a name
// sets it *inside* the clickable node rather than on it, and a clickable node
// merges what is inside it — so the name reaches the node TalkBack focuses, and
// the click action lives on that same node. A name that landed anywhere else
// would be invisible to TalkBack, and nothing else in the repo fails when that
// regresses.
//
// Not covered here, on purpose: dp values, pill width, colour, the clip on a
// narrow card. Those are pixels, and this is not a screenshot test — asserting
// bounds would be the wrong instrument, and it would pass while the card looks
// wrong. `androidTest` also needs a device, so this rides the emulator job in
// CI; keep it small for the same reason.
class ReadingCardAccessibilityInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Strings are read from the app's resources rather than hardcoded, so a copy
    // change moves the test with it — the same reason StringsParityTest exists.
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(@StringRes id: Int): String = context.getString(id)

    @Test
    fun actionRow_namesEveryControl() {
        renderCard()

        // Each of the four controls is reachable by name *and* carries the click
        // action itself — which is the whole point: a name that landed on a node
        // other than the focused one would be found here only in the unmerged
        // tree, and TalkBack reads the merged one.
        composeTestRule.onNodeWithContentDescription(string(R.string.cd_prev_ayah))
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(string(R.string.cd_next_ayah))
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(string(R.string.mark_read))
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(string(R.string.cd_bookmark))
            .assertHasClickAction()
    }

    @Test
    fun bookmarkToggle_announcesSavedState() {
        renderCard(state = defaultState(isSaved = true))

        // Name and state are separate announcements: the name says what the
        // control is, the state says which way it currently points.
        composeTestRule.onNodeWithContentDescription(string(R.string.cd_bookmark))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.cd_bookmark_saved),
                ),
            )
    }

    @Test
    fun bookmarkToggle_announcesNotSavedState() {
        renderCard(state = defaultState(isSaved = false))

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_bookmark))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.cd_bookmark_not_saved),
                ),
            )
    }

    @Test
    fun bookmarkToggle_isAbsent_whenHostPassesNoToggle() {
        // The tour's practice card is the one host that passes none, and that is
        // settled rather than pending (see CONTEXT.md) — so the absence is a
        // contract, not an oversight.
        renderCard(onToggleBookmark = null)

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_bookmark))
            .assertDoesNotExist()
    }

    @Test
    fun header_announcesTheHostsDestination() {
        // The destination is the host's, so the announcement has to be too: this
        // card's header opens the bookmarks list, and Home's opens the surah
        // picker. A hardcoded "Surahs & ayahs" would name the wrong screen here.
        renderCard(
            onGoToAyah = {},
            goToAyahLabelRes = R.string.cd_bookmarks_header,
        )

        val expected = listOf(
            string(R.string.cd_bookmarks_header),
            STATE.surahName,
            STATE.totalLabel,
        ).joinToString(", ")

        composeTestRule.onNodeWithContentDescription(expected)
            .assertHasClickAction()
    }

    @Test
    fun header_isNotATapTarget_whenHostPassesNoDestination() {
        renderCard(onGoToAyah = null)

        val expected = listOf(
            string(R.string.cd_goto_header),
            STATE.surahName,
            STATE.totalLabel,
        ).joinToString(", ")

        composeTestRule.onNodeWithContentDescription(expected)
            .assertDoesNotExist()
    }

    private fun renderCard(
        state: ReadingUiState = defaultState(),
        onGoToAyah: (() -> Unit)? = null,
        @StringRes goToAyahLabelRes: Int = R.string.cd_goto_header,
        onToggleBookmark: (() -> Unit)? = { },
    ) {
        composeTestRule.setContent {
            WaqfahTheme {
                ReadingCard(
                    state = state,
                    onMarkRead = { },
                    onNext = { },
                    onPrevious = { },
                    onCycleTranslation = { },
                    onResetTranslation = { },
                    onCompletionDismiss = { },
                    onStartOver = { },
                    onSwitchModeAndRestart = { },
                    onGoToAyah = onGoToAyah,
                    goToAyahLabelRes = goToAyahLabelRes,
                    onToggleBookmark = onToggleBookmark,
                    bottomBar = { },
                )
            }
        }
        // The v2 rule drives the composition on a StandardTestDispatcher, which
        // queues work instead of running it immediately, so composition and
        // layout are not finished when setContent returns. Every test below reads
        // the semantics tree, so it has to be quiescent first. The card settles:
        // nothing in it animates forever, and the skeleton (which does) is behind
        // isLoading.
        composeTestRule.waitForIdle()
    }

    private fun defaultState(isSaved: Boolean = false): ReadingUiState = ReadingUiState(
        isLoading = false,
        surahName = STATE.surahName,
        ayahLabel = STATE.ayahLabel,
        totalLabel = STATE.totalLabel,
        arabicText = STATE.arabicText,
        translationText = STATE.translationText,
        isSaved = isSaved,
    )

    private companion object {
        // An ordinary first ayah. The card only needs values realistic enough to
        // render its normal, non-empty shape — but the surah name and the total
        // are read back verbatim by the header tests, because both are parts of
        // the announcement the header composes.
        val STATE = ReadingUiState(
            surahName = "Al-Fatihah",
            ayahLabel = "1:1",
            totalLabel = "7 ayahs",
            arabicText = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            translationText = "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
        )
    }
}
