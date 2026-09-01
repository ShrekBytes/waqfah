package com.shrekbytes.waqfah.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.shrekbytes.waqfah.R

// GitHub/email/credit links all assume some app is registered to handle the
// intent (a browser, a mail client). That's not guaranteed on every device
// (no email app configured is common) — this is the one guarded path instead
// of repeating the same try/catch at every call site.
fun Context.launchExternal(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show()
    }
}
