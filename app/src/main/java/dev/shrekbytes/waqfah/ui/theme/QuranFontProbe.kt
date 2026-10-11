package dev.shrekbytes.waqfah.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import dev.shrekbytes.waqfah.BuildConfig
import dev.shrekbytes.waqfah.data.model.ArabicFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Loads both bundled Quran fonts once per process and logs loudly if either
 * fails to load.
 *
 * A Compose font resource fails silently: when the platform can't load a
 * `Font(R.font.…)` — the outline format isn't parseable on that device, the
 * read fails under memory pressure — the text renders in whatever Arabic font
 * the system falls back to instead, with no crash and no signal the app can
 * observe. On Quranic text that failure mode is invisible in the code and
 * obvious in the UI: diacritics placed on the wrong letters, Quranic
 * annotation marks as tofu. That's exactly the Realme C12 report behind the
 * CFF2 → TrueType conversion this probe accompanies.
 *
 * Startup is the right hook: the interstitial and the home reading card
 * render the same fonts, so one pass covers every place an ayah is drawn. The
 * cost is one read per font (~850 KB total) on a background dispatcher. The
 * permanent guarantee lives in `QuranFontCoverageTest` (font format and
 * glyph coverage, checked in CI); this makes the next device-specific failure
 * diagnosable from a bug report's logcat instead of from a screenshot — the
 * error line names the font and the device it died on.
 */
internal object QuranFontProbe {

    private const val TAG = "QuranFontProbe"

    // Never cancelled on purpose: same shape as TranslationRepository's
    // initScope — short file reads that finish long before anything else
    // matters, then the scope sits idle.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun checkAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            // The font list and file names come from ArabicFont, so the probe
            // can't drift from what the reading UI actually renders.
            ArabicFont.entries.forEach { font ->
                val label = "${font.displayName()} (${font.script.name.lowercase()} script)"
                val callback = object : ResourcesCompat.FontCallback() {
                    override fun onFontRetrieved(typeface: Typeface) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Loaded Quran font: $label")
                    }

                    override fun onFontRetrievalFailed(reason: Int) {
                        Log.e(TAG, failureMessage(label, reason))
                    }
                }
                try {
                    ResourcesCompat.getFont(appContext, font.fontResId, callback, null)
                } catch (e: Exception) {
                    // getFont is documented to throw NotFoundException past this
                    // point; without the catch the coroutine would take the
                    // process down. Same wording as the callback's failure.
                    Log.e(TAG, failureMessage(label, "threw ${e.javaClass.simpleName}"), e)
                }
            }
        }
    }

    private fun failureMessage(label: String, reason: Any): String =
        "Quran font failed to load ($reason): $label — ayah text will render in " +
            "the device's fallback font [${Build.MODEL}, Android ${Build.VERSION.RELEASE}]"
}
