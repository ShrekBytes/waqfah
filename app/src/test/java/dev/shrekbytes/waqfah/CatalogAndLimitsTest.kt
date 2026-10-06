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
        val defaultRequiredKeys = PermissionCatalog.required(isXiaomi = false).map { it.key }
        // On standard devices, only usage access and overlay are indispensable.
        assertEquals(setOf(PermissionKey.USAGE_ACCESS, PermissionKey.OVERLAY), defaultRequiredKeys.toSet())
        assertEquals("Keys must be unique", defaultRequiredKeys.size, defaultRequiredKeys.toSet().size)

        // On Xiaomi/HyperOS, background start permission is also strictly required.
        val xiaomiRequiredKeys = PermissionCatalog.required(isXiaomi = true).map { it.key }
        assertEquals(
            setOf(PermissionKey.USAGE_ACCESS, PermissionKey.OVERLAY, PermissionKey.XIAOMI_BACKGROUND_START),
            xiaomiRequiredKeys.toSet(),
        )
        assertEquals("Keys must be unique", xiaomiRequiredKeys.size, xiaomiRequiredKeys.toSet().size)
    }

    @Test
    fun permissionCatalog_optionalRowsCoverEverythingElse_exactlyOnce() {
        val optionalKeys = PermissionCatalog.recommended.map { it.key }
        val expected = PermissionKey.entries.toSet() - PermissionCatalog.required(isXiaomi = true).map { it.key }.toSet()
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