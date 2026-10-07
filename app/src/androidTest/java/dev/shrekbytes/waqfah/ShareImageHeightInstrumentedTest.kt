package dev.shrekbytes.waqfah

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.test.core.app.ActivityScenario
import dev.shrekbytes.waqfah.ui.reading.ReadingUiState
import dev.shrekbytes.waqfah.ui.sharing.renderShareImage
import dev.shrekbytes.waqfah.ui.theme.BasePalettes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// A long ayah must survive the share, whole (ADR-0007: "There is no height cap
// … completeness wins over a tidy aspect ratio, and cropping an ayah is never
// an option").
//
// The cap that broke that promise is not written in the share code. It arrives
// from Android's measure pass: the page is composed off-screen inside the
// hosting activity's content view with a WRAP_CONTENT height, and
// ViewGroup.getChildMeasureSpec turns WRAP_CONTENT into AT_MOST(the window) —
// "Child wants to determine its own size. It can't be bigger than us." The
// bitmap is then created at exactly the height the page reports, so
// Al-Baqarah 2:25 came out cut off at the screen height with its translation
// missing, while the short 2:18 exported fine. UnboundedHeightHost in
// ShareImageSender is what breaks that constraint, and this test is what keeps
// it broken.
//
// It drives the real renderer rather than a composition, because the fix cannot
// live in Compose and a test at that level cannot see it. The obvious
// Compose-level answer — wrapContentHeight(unbounded = true) on the page's root
// — was tried on this device and does not work: it relaxes the constraints it
// hands down, but its own result is coerced into the incoming range, so the
// page still reported exactly the window height. A test that composed the page
// would therefore pass whether or not the bug was fixed. Only the real path
// tells them apart.
//
// AndroidUiDispatcher.Main is the context the real share flow runs in (the
// card's rememberCoroutineScope), and the renderer's withFrameNanos needs it:
// it carries the frame clock and lets the main looper run between frames so the
// off-screen view can actually lay out. Instrumented test bodies run on the
// instrumentation thread, not the main thread, so blocking it here is safe.
class ShareImageHeightInstrumentedTest {

    @Test
    fun longAyah_rendersTallerThanTheWindow() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var activity: ComponentActivity
            var windowHeightPx = 0
            scenario.onActivity {
                activity = it
                // The real hosts always have content on screen — MainActivity
                // and TriggerActivity both call setContent — and the ViewTree
                // owners the off-screen composition resolves its recomposer from
                // arrive with the content view. A bare activity has neither, so
                // an empty one matches the real environment rather than faking
                // it.
                it.setContentView(FrameLayout(it))
                windowHeightPx = it.findViewById<ViewGroup>(android.R.id.content).height
            }

            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue("the host window has not been laid out", windowHeightPx > 0)

            val bitmap = runBlocking(AndroidUiDispatcher.Main) {
                renderShareImage(
                    activity = activity,
                    state = LONG_AYAH,
                    colors = BasePalettes.Light,
                    fontScale = 1f,
                )
            }

            // The image's fixed width, so a degenerate bitmap cannot make the
            // height assertion below pass for the wrong reason.
            assertEquals(1080, bitmap.width)

            assertTrue(
                "the share image came out ${bitmap.height} px against a " +
                    "$windowHeightPx px window — a long ayah is being clamped to the screen",
                bitmap.height > windowHeightPx,
            )
        }
    }

    private companion object {
        // Al-Baqarah 2:25 — the ayah from the report that exported truncated,
        // with the translation the reader shares by default. Its length is the
        // point rather than a coincidence: at the default 26sp, with the
        // Arabic's doubled line height, it runs past any phone screen on its
        // own before the translation is added.
        val LONG_AYAH = ReadingUiState(
            isLoading = false,
            surahName = "Al-Baqarah",
            ayahLabel = "2:25",
            arabicText = "وَبَشِّرِ ٱلَّذِينَ ءَامَنُوا۟ وَعَمِلُوا۟ ٱلصَّـٰلِحَـٰتِ أَنَّ لَهُمْ جَنَّـٰتٍ تَجْرِى مِن تَحْتِهَا ٱلْأَنْهَـٰرُ ۖ كُلَّمَا رُزِقُوا۟ مِنْهَا مِن ثَمَرَةٍ رِّزْقًا ۙ قَالُوا۟ هَـٰذَا ٱلَّذِى رُزِقْنَا مِن قَبْلُ ۖ وَأُتُوا۟ بِهِۦ مُتَشَـٰبِهًا ۖ وَلَهُمْ فِيهَآ أَزْوَٰجٌ مُّطَهَّرَةٌ ۖ وَهُمْ فِيهَا خَـٰلِدُونَ",
            translationText = "And give good tidings to those who believe and do righteous " +
                "deeds that they will have gardens [in Paradise] beneath which rivers flow. " +
                "Whenever they are provided with a provision of fruit therefrom, they will " +
                "say, \"This is what we were provided with before.\" And it is given to them " +
                "in likeness. And they will have therein purified spouses, and they will " +
                "abide therein eternally.",
        )
    }
}
