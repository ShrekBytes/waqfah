package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.AppMonitorService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The package/class half of the call-UI rule (see AppMonitorService.isCallForeground
// for the audio-routing half, which needs a live AudioManager and isn't pure).
// Getting this wrong one way shows the reading screen over an incoming/ongoing
// call; getting it wrong the other way silently swallows a real app open.
class CallDetectionTest {

    @Test
    fun systemDialerPackages_areCallUi_regardlessOfClassName() {
        assertTrue(AppMonitorService.isKnownCallUi("com.android.dialer", null))
        assertTrue(AppMonitorService.isKnownCallUi("com.google.android.dialer", "com.google.android.dialer.SomeMainActivity"))
        assertTrue(AppMonitorService.isKnownCallUi("com.samsung.android.incallui", "AnythingAtAll"))
    }

    @Test
    fun messagingApps_ownCallScreen_isCallUi() {
        // Real-world examples of what these apps' call activities look like.
        assertTrue(AppMonitorService.isKnownCallUi("com.whatsapp", "com.whatsapp.voipcalling.VoipActivityV2"))
        assertTrue(AppMonitorService.isKnownCallUi("com.facebook.orca", "com.facebook.rtc.fbwebrtc.RtcCallActivity"))
        assertTrue(AppMonitorService.isKnownCallUi("org.telegram.messenger", "org.telegram.messenger.voip.VoIPActivity"))
        assertTrue(AppMonitorService.isKnownCallUi("org.thoughtcrime.securesms", "org.thoughtcrime.securesms.calls.WebRtcCallActivity"))
        assertTrue(AppMonitorService.isKnownCallUi("com.viber.voip", "com.viber.voip.phone.ViberOutgoingCallActivity"))
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertTrue(AppMonitorService.isKnownCallUi("com.example.app", "com.example.app.INCOMINGCALLACTIVITY"))
    }

    @Test
    fun ordinaryAppScreens_areNotCallUi() {
        assertFalse(AppMonitorService.isKnownCallUi("com.whatsapp", "com.whatsapp.HomeActivity"))
        assertFalse(AppMonitorService.isKnownCallUi("com.instagram.android", "com.instagram.android.activity.MainTabActivity"))
        assertFalse(AppMonitorService.isKnownCallUi("com.android.chrome", "com.google.android.apps.chrome.Main"))
    }

    @Test
    fun nullClassName_onAnUnknownPackage_isNotCallUi() {
        assertFalse(AppMonitorService.isKnownCallUi("com.instagram.android", null))
    }
}
