package com.shrekbytes.waqfah.data.model

import android.graphics.Bitmap

// icon is a small, pre-downscaled bitmap (see
// MonitoredAppsRepository.loadIconBitmap()) rather than the raw Drawable —
// keeps memory bounded even with 150+ installed apps in the list, and means
// AppRow can render it directly with no per-recomposition conversion work.
// Null falls back to the letter-avatar AppRow already had (icon loading can
// fail for a handful of odd/system launcher entries).
data class InstalledApp(val packageName: String, val label: String, val icon: Bitmap? = null)
