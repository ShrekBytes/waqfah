package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.PermissionCatalog
import com.shrekbytes.waqfah.data.model.PermissionKey
import com.shrekbytes.waqfah.data.model.PreferenceLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndLimitsTest {

    @Test
    fun permissionCatalog_requiredRowsCoverEveryNonOptionalKeyExactlyOnce() {
        val requiredKeys = PermissionCatalog.all.map { it.key }
        // NOTIFICATIONS is deliberately excluded from [all]: it's the optional,
        // non-gated visibility toggle (see PermissionsViewModel), so screens
        // iterating [all] must never gate onboarding on it.
        assertEquals(PermissionKey.entries.toSet() - PermissionKey.NOTIFICATIONS, requiredKeys.toSet())
        assertEquals("Keys must be unique", requiredKeys.size, requiredKeys.toSet().size)
    }

    @Test
    fun permissionCatalog_optionalNotificationsRowIsSeparateFromRequiredSet() {
        assertFalse(PermissionCatalog.all.any { it.key == PermissionKey.NOTIFICATIONS })
        assertEquals(PermissionKey.NOTIFICATIONS, PermissionCatalog.notifications.key)
    }

    @Test
    fun preferenceLimits_areSane() {
        assertTrue(PreferenceLimits.FONT_SIZE_MIN < PreferenceLimits.FONT_SIZE_MAX)
        assertTrue(PreferenceLimits.COOLDOWN_MIN_MINUTES < PreferenceLimits.COOLDOWN_MAX_MINUTES)
        // 0 minutes is the documented "Off" value for the interval stepper.
        assertEquals(0, PreferenceLimits.COOLDOWN_MIN_MINUTES)
    }
}