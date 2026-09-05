package com.shrekbytes.waqfah.detection

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// One walk of UsageStatsManager, shared by the two consumers of
// resumed-activity facts: MonitorSession's event feed (via AppMonitorService)
// and the interstitial's re-assert check (via TriggerActivity). The
// latest-in-window derivation is a pure core, tested directly.
class ResumedActivityReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resumedActivities(from: Long, to: Long): List<ResumedActivity> =
        query(from, to).map { it.activity }

    // True when the most recent ACTIVITY_RESUMED event belongs to
    // [packageName]. Used by TriggerActivity to tell "the user left" apart
    // from "the triggered app raised itself back over the interstitial".
    fun isLatestForeground(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val latest = latestIn(query(now - RECENT_EVENT_WINDOW_MS, now))
        return latest?.activity?.packageName == packageName
    }

    private fun query(from: Long, to: Long): List<TimedResumedActivity> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        val resumed = mutableListOf<TimedResumedActivity>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                resumed += TimedResumedActivity(ResumedActivity(event.packageName, event.className), event.timeStamp)
            }
        }
        return resumed
    }

    internal data class TimedResumedActivity(val activity: ResumedActivity, val atMs: Long)

    companion object {
        // Lookback for isLatestForeground().
        private const val RECENT_EVENT_WINDOW_MS = 3_000L

        // Pure core of isLatestForeground: the most recent event by
        // timestamp. A tie keeps the LAST one seen (>=, matching the walk's
        // order), never the first.
        internal fun latestIn(events: List<TimedResumedActivity>): TimedResumedActivity? {
            var latest: TimedResumedActivity? = null
            for (event in events) {
                if (latest == null || event.atMs >= latest.atMs) latest = event
            }
            return latest
        }
    }
}
