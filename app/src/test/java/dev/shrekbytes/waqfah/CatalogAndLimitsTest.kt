package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.model.PermissionCatalog
import dev.shrekbytes.waqfah.data.model.PermissionKey
import dev.shrekbytes.waqfah.data.model.PreferenceLimits
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
    fun permissionCatalog_deviceSpecificRowsNeverGateOnboarding() {
        // ADR-0008: the vendor background-start row cannot report its own
        // state, so it must never join the rows gated behind Continue. This
        // assertion exists to stop that decision being undone by accident.
        val requiredKeys = PermissionCatalog.all.map { it.key }.toSet()
        val deviceKeys = PermissionCatalog.deviceSpecific.map { it.key }.toSet()
        assertTrue(
            "device-specific rows must stay out of the Continue gate, found: ${requiredKeys intersect deviceKeys}",
            (requiredKeys intersect deviceKeys).isEmpty(),
        )
    }

    @Test
    fun permissionCatalog_everyKeyBelongsToExactlyOneGroup() {
        val groups = PermissionCatalog.all + PermissionCatalog.recommended + PermissionCatalog.deviceSpecific
        val keys = groups.map { it.key }
        assertEquals("A key must not appear in two groups", keys.size, keys.toSet().size)
        assertEquals("Every key must be in exactly one group", PermissionKey.entries.toSet(), keys.toSet())
    }

    @Test
    fun preferenceLimits_areSane() {
        assertTrue(PreferenceLimits.FONT_SIZE_MIN < PreferenceLimits.FONT_SIZE_MAX)
        assertTrue(PreferenceLimits.COOLDOWN_MIN_MINUTES < PreferenceLimits.COOLDOWN_MAX_MINUTES)
        // 0 minutes is the documented "Off" value for the interval stepper.
        assertEquals(0, PreferenceLimits.COOLDOWN_MIN_MINUTES)
    }
}
