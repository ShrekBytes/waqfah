package dev.shrekbytes.waqfah

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.shrekbytes.waqfah.ui.theme.QuranFontProbe

@HiltAndroidApp
class WaqfahApp : Application() {
    override fun onCreate() {
        super.onCreate()
        QuranFontProbe.checkAsync(this)
    }
}
