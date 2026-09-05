package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.ResumedActivity
import com.shrekbytes.waqfah.detection.ResumedActivityReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The pure core of ResumedActivityReader: the latest-in-window derivation
// behind the interstitial's buried-retry check.
class ResumedActivityReaderTest {

    private fun event(pkg: String, atMs: Long) =
        ResumedActivityReader.TimedResumedActivity(ResumedActivity(pkg, "$pkg.MainActivity"), atMs)

    @Test
    fun emptyWindow_hasNoLatest() {
        assertNull(ResumedActivityReader.latestIn(emptyList()))
    }

    @Test
    fun mostRecentEventWins_regardlessOfListOrder() {
        val latest = event("com.b", 300)

        assertEquals(
            latest,
            ResumedActivityReader.latestIn(listOf(event("com.a", 100), latest, event("com.c", 200))),
        )
    }

    // The walk sees events in emission order; a tie keeps the last one seen.
    @Test
    fun tiedTimestamps_keepTheLastSeen() {
        val first = event("com.a", 300)
        val second = event("com.b", 300)

        assertEquals(second, ResumedActivityReader.latestIn(listOf(first, second)))
    }

    @Test
    fun singleEvent_isItsOwnLatest() {
        val only = event("com.a", 42)

        assertEquals(only, ResumedActivityReader.latestIn(listOf(only)))
    }
}
