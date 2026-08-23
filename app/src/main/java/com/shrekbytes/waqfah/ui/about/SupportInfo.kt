package com.shrekbytes.waqfah.ui.about

import com.shrekbytes.waqfah.R

// Editable content for the About/Donate/Gratitude pages, kept in one place so
// updating numbers or names never touches UI code.
object SupportInfo {
    const val REPO_URL = "https://github.com/ShrekBytes/waqfah"
    const val CONTACT_EMAIL = "shrekbytes@duck.com"

    // TODO(user): replace placeholder numbers with real accounts, and the
    // drawable placeholders (ic_bkash/ic_rocket/ic_nagad) with official icons.
    data class DonationAccount(
        val method: String,
        val number: String,
        val accountType: String,
        val iconRes: Int,
    )

    val donations = listOf(
        DonationAccount("bKash", "01XXXXXXXXX", "Personal", R.drawable.ic_bkash),
        DonationAccount("Rocket", "01XXXXXXXXX-X", "Personal", R.drawable.ic_rocket),
        DonationAccount("Nagad", "01XXXXXXXXX", "Personal", R.drawable.ic_nagad),
    )

    // TODO(user): swap the example.com placeholders for real profiles.
    data class Contributor(val name: String, val role: String, val url: String? = null)

    val contributors = listOf(
        Contributor("ShrekBytes", "Design & development", "https://github.com/shrekbytes"),
        Contributor("Md. Mahbob Alam", "Testing & feedback", "https://example.com"),
        Contributor("Tahmid Alam Tamim", "Testing & feedback", "https://example.com"),
    )
}
