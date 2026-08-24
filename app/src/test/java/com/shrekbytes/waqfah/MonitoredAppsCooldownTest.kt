package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoredAppsCooldownTest {

    private val now = 1_000_000_000L

    private fun within(lastShownAt: Long?, cooldownMinutes: Int = 30) =
        MonitoredAppsRepository.isWithinCooldown(lastShownAt, now, cooldownMinutes)

    @Test
    fun neverShown_neverCoolsDown() {
        assertFalse(within(null))
    }

    @Test
    fun insideWindow_coolsDown() {
        assertTrue(within(now - 1))
        assertTrue(within(now - 29 * 60_000L))
    }

    @Test
    fun windowBoundary_isExpired_exactlyAtLimit() {
        // elapsed == window means the interval has fully passed.
        assertFalse(within(now - 30 * 60_000L))
    }

    @Test
    fun pastWindow_isExpired() {
        assertFalse(within(now - 31 * 60_000L))
        assertFalse(within(now - 10 * 24 * 60 * 60_000L))
    }

    @Test
    fun clockRollback_countsAsExpired_neverPermanentlyCooling() {
        assertFalse(within(now + 5 * 60_000L))
    }

    @Test
    fun zeroInterval_off_coolsNothingDown() {
        assertFalse(within(now - 1, cooldownMinutes = 0))
    }
}