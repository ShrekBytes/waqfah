package com.shrekbytes.waqfah.data.model

import android.graphics.Bitmap

// Small pre-downscaled bitmap (see PackageManagerInstalledAppCatalog.loadIconBitmap()) so
// memory stays bounded with large app lists and AppRow needs no conversion work.
data class InstalledApp(val packageName: String, val label: String, val icon: Bitmap? = null)
