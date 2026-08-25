package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.PermissionCatalog
import com.shrekbytes.waqfah.data.model.PermissionKey
import com.shrekbytes.waqfah.data.model.PreferenceLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndLimitsTest {

    @Test
    fun permissionCatalog_requiredRowsAreExactlyTheIndispensableOnes() {
        val requiredKeys = PermissionCatalog.all.map { it.key }
        // Only usage access and overlay are indispensable: without either,
        // monitoring cannot function at all. Battery (reliability on aggressive
        // OEMs) and notifications (visibility on 13+) deliberately stay out so
        // they never gate onboarding's Continue button.
        assertEquals(setOf(PermissionKey.USAGE_ACCESS, PermissionKey.OVERLAY), requiredKeys.toSet())
        assertEquals("Keys must be unique", requiredKeys.size, requiredKeys.toSet().size)
    }

    @Test
    fun permissionCatalog_optionalRowsCoverEverythingElse_exactlyOnce() {
        val optionalKeys = PermissionCatalog.recommended.map { it.key }
        val expected = PermissionKey.entries.toSet() - PermissionCatalog.all.map { it.key }.toSet()
        assertEquals(expected, optionalKeys.toSet())
        assertEquals("Keys must be unique", optionalKeys.size, optionalKeys.toSet().size)
    }

    @Test
    fun preferenceLimits_areSane() {
        assertTrue(PreferenceLimits.FONT_SIZE_MIN < PreferenceLimits.FONT_SIZE_MAX)
        assertTrue(PreferenceLimits.COOLDOWN_MIN_MINUTES < PreferenceLimits.COOLDOWN_MAX_MINUTES)
        // 0 minutes is the documented "Off" value for the interval stepper.
        assertEquals(0, PreferenceLimits.COOLDOWN_MIN_MINUTES)
    }
}