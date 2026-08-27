package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.AppMonitorService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The interstitial-return rule: finishing TriggerActivity resumes the paused
// app underneath — an event indistinguishable from a fresh open. Getting this
// wrong in one direction traps users in interstitial loops (interval Off,
// reading longer than SWITCH_BACK_GAP_MS); getting it wrong the other way
// swallows legitimate opens that come right after using Waqfah itself. That's
// why the match is on the TriggerActivity CLASS, not just the package.
class TriggerRulesTest {

    private val ownPackage = "com.shrekbytes.waqfah"
    private val triggerClass = "com.shrekbytes.waqfah.TriggerActivity"
    private val mainClass = "com.shrekbytes.waqfah.MainActivity"

    @Test
    fun resumingFromTheInterstitial_isAnInterstitialReturn() {
        assertTrue(AppMonitorService.isReturnFromInterstitial(ownPackage, triggerClass))
    }

    @Test
    fun openingAfterUsingWaqfahItself_isAFreshOpen_notAnInterstitialReturn() {
        // Waqfah's main screen shares the package name — only the trigger
        // activity's class marks an interstitial return.
        assertFalse(AppMonitorService.isReturnFromInterstitial(ownPackage, mainClass))
        assertFalse(AppMonitorService.isReturnFromInterstitial(ownPackage, null))
    }

    @Test
    fun comingFromAnyOtherApp_isAFreshOpen() {
        assertFalse(AppMonitorService.isReturnFromInterstitial("com.android.systemui", triggerClass))
        assertFalse(AppMonitorService.isReturnFromInterstitial("com.android.launcher3", null))
    }

    @Test
    fun noPreviousEvent_isNeverAnInterstitialReturn() {
        assertFalse(AppMonitorService.isReturnFromInterstitial(null, null))
    }
}