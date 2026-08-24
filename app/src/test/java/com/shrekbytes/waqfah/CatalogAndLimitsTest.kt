package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.PermissionCatalog
import com.shrekbytes.waqfah.data.model.PermissionKey
import com.shrekbytes.waqfah.data.model.PreferenceLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndLimitsTest {

    @Test
    fun permissionCatalog_coversEveryKeyExactlyOnce() {
        val keys = PermissionCatalog.all.map { it.key }
        assertEquals(PermissionKey.entries.toSet(), keys.toSet())
        assertEquals("Keys must be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun preferenceLimits_areSane() {
        assertTrue(PreferenceLimits.FONT_SIZE_MIN < PreferenceLimits.FONT_SIZE_MAX)
        assertTrue(PreferenceLimits.COOLDOWN_MIN_MINUTES < PreferenceLimits.COOLDOWN_MAX_MINUTES)
        // 0 minutes is the documented "Off" value for the interval stepper.
        assertEquals(0, PreferenceLimits.COOLDOWN_MIN_MINUTES)
    }
}