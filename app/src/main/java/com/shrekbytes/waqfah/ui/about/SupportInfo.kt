package com.shrekbytes.waqfah.ui.about

import com.shrekbytes.waqfah.R

// Editable content for the About/Donate/Gratitude pages, kept in one place so
// updating numbers or names never touches UI code.
object SupportInfo {
    const val REPO_URL = "https://github.com/ShrekBytes/waqfah"
    const val CONTACT_EMAIL = "shrekbytes@duck.com"

    data class DonationAccount(
        val method: String,
        val number: String,
        val accountType: String,
        val iconRes: Int,
    )

    val donations = listOf(
        DonationAccount("bKash", "01725-522837", "Personal", R.drawable.ic_bkash),
        // DonationAccount("Rocket", "01725-000000", "Personal", R.drawable.ic_rocket),
        // DonationAccount("Nagad", "01725-000000", "Personal", R.drawable.ic_nagad),
    )

    data class Contributor(val name: String, val role: String, val url: String? = null)

    val contributors = listOf(
        Contributor("ShrekBytes", "Design & development", "https://github.com/shrekbytes"),
        Contributor("Md. Mahbob Alam", "Testing & feedback", "https://github.com/emptymahbob"),
        Contributor("Tahmid Alam Tamim", "Testing & feedback", "https://github.com/Mr-Explorer142"),
        Contributor("Md. Walid Ahmed", "Testing & feedback"),
    )
}
