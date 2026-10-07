package dev.shrekbytes.waqfah.ui.sharing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.FileProvider
import dev.shrekbytes.waqfah.ui.reading.ReadingSession
import dev.shrekbytes.waqfah.ui.reading.ReadingUiState
import dev.shrekbytes.waqfah.ui.theme.LocalWaqfahColors
import dev.shrekbytes.waqfah.ui.theme.WaqfahColors
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dev.shrekbytes.waqfah.ui.theme.WaqfahTypography
import dev.shrekbytes.waqfah.ui.theme.findActivity
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// The share control's tap flow, end to end (see CONTEXT.md's "Share control"):
// render the current ayah as a share image, write it to the cache, and hand it
// to the system share sheet — no preview step, no permission prompt. Returns a
// callback for the card's `onShare`; every reading host passes one (the tour's
// practice card passes none, like its other controls).
//
// The image is a function of the session's state plus the theme, both captured
// at tap time: the palette is this composition's, and the font scale is this
// composition's, so what gets shared is exactly what the reader was looking at
// — including a translation peeked for comparison, which the reading state
// already reflects.
@Composable
fun rememberShareAyahLauncher(session: ReadingSession): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Read fresh at tap time: the theme can change while this is composed.
    val latestColors by rememberUpdatedState(WaqfahTheme.colors)
    val latestFontScale by rememberUpdatedState(LocalDensity.current.fontScale)

    // The file currently offered to the share sheet, deleted the moment the
    // sheet returns — the image does not survive the share. (A sheet that
    // never returns — a crashed chooser, an opaque one that stops the host on
    // some OEM — is covered by wiping the staging directory before each write,
    // so nothing accumulates.)
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val chooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingFile?.delete()
        pendingFile = null
    }

    return remember(session) {
        {
            scope.launch {
                try {
                    val state = session.uiState.value
                    if (state.isLoading || state.isEmpty) return@launch

                    val file = stagedShareFile(context)
                    val bitmap = renderShareImage(
                        activity = context.findActivity(),
                        state = state,
                        colors = latestColors,
                        fontScale = latestFontScale,
                    )
                    withContext(Dispatchers.Default) {
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }

                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    pendingFile = file
                    chooserLauncher.launch(Intent.createChooser(share, null))
                } catch (e: Exception) {
                    // A failed share must never take the app down: log and
                    // leave the reader where they were, on the same card.
                    Log.e("ShareImage", "Failed to share ayah", e)
                }
            }
        }
    }
}

// The staging directory under the cache, wiped before every write: the share
// image is a one-off, not a gallery (see the cleanup contract above).
private fun stagedShareFile(context: Context): File {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, "waqfah-ayah.png")
}

// Renders the share image at the image's own width (1080px) rather than
// capturing the on-screen card: a composition is run off-screen, attached to
// the hosting activity's window so it can actually compose and draw, measured
// at the image's exact pixel width and at the render density (the card's
// nominal 360dp at 3x, carrying the reader's system font scale), then drawn
// once into a bitmap. Software layer type so the manual draw is deterministic;
// the view sits far off-screen and lives for a frame or two, invisible either
// way.
private suspend fun renderShareImage(
    activity: Activity?,
    state: ReadingUiState,
    colors: WaqfahColors,
    fontScale: Float,
): Bitmap {
    checkNotNull(activity) { "Sharing needs an activity host" }
    val widthPx = (ShareImageGeometry.pageWidth.value * ShareImageGeometry.SCALE).roundToInt()

    val content = activity.findViewById<ViewGroup>(android.R.id.content)
    val view = ComposeView(activity).apply {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        translationX = -widthPx * 2f
        // The page's own palette and render density — not the window's: the
        // image is a function of the reader's theme and font scale, composed
        // at the image's fixed scale. MaterialTheme's typography is provided
        // because the shared text components inherit its defaults.
        setContent {
            CompositionLocalProvider(
                LocalWaqfahColors provides colors,
                LocalDensity provides ShareImageGeometry.renderDensity(fontScale),
            ) {
                MaterialTheme(typography = WaqfahTypography) {
                    ShareImagePage(state)
                }
            }
        }
    }
    content.addView(
        view,
        FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    try {
        // The first layout with content is the captureable one: wrap height
        // > 0 means the composition has measured. One further frame lets the
        // window's draw settle over it.
        withTimeout(5_000L) {
            while (!(view.isLaidOut && view.width == widthPx && view.height > 0)) withFrameNanos { it }
            withFrameNanos { it }
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    } finally {
        content.removeView(view)
    }
}
