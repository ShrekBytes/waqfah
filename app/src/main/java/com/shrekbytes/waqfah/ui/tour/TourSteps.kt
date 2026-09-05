package com.shrekbytes.waqfah.ui.tour

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.shrekbytes.waqfah.R

// One stop of the feature tour. Flow shows how Waqfah works as a
// blockquote-style chain, TryIt asks the user to perform the real action on the live
// reading card, SettingRows/Checklist visualize where things live.
sealed interface TourStep {
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
        val kind: TourTaskKind,
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

data class SettingRow(
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
)

val TOUR_STEPS = listOf<TourStep>(
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
    TourStep.TryIt(TourTaskKind.MARK_READ, R.string.tour_t_mark_title, R.string.tour_t_mark_body),
    TourStep.TryIt(TourTaskKind.CHANGE_AYAH, R.string.tour_t_move_title, R.string.tour_t_move_body),
    TourStep.TryIt(TourTaskKind.SWITCH_TRANSLATION, R.string.tour_t_trans_title, R.string.tour_t_trans_body),
    TourStep.TryIt(TourTaskKind.GO_TO_AYAH, R.string.tour_t_goto_title, R.string.tour_t_goto_body),
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

// One entry per tour stop, index-aligned with TOUR_STEPS: the TryIt task that
// stop practices, or null for a read-only stop. The machine's copy of the
// itinerary — TourStep above carries the rendering content.
val TOUR_STEP_TASKS: List<TourTaskKind?> = TOUR_STEPS.map { (it as? TourStep.TryIt)?.kind }
