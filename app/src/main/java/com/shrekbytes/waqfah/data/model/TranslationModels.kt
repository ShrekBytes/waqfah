package com.shrekbytes.waqfah.data.model

enum class TranslationLanguage(val code: String) { ENGLISH("en"), BENGALI("bn") }

data class TranslationMeta(
    val id: String,
    val name: String,
    val language: TranslationLanguage,
    val isBundled: Boolean,
    val downloadUrl: String?, // null for bundled — comes from assets instead
)

object TranslationCatalog {
    val all = listOf(
        TranslationMeta("sahih", "Sahih International", TranslationLanguage.ENGLISH, isBundled = true, downloadUrl = null),
        TranslationMeta("clear", "The Clear Quran", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://example.com/translations/en/clear.db"),
        TranslationMeta("yusufali", "Yusuf Ali", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/yusufali.db"),
        TranslationMeta("pickthall", "Pickthall", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://example.com/translations/en/pickthall.db"),
        TranslationMeta("ghali", "Dr. Ghali", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://example.com/translations/en/ghali.db"),
        TranslationMeta("arberry", "Arberry", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/arberry.db"),
        TranslationMeta("taisirul", "Taisirul Quran", TranslationLanguage.BENGALI, isBundled = true, downloadUrl = null),
        TranslationMeta("bayan", "Al-Bayan", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://example.com/translations/bn/taisirul.db"),
        TranslationMeta("fathulmajid", "Fathul Majid", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://example.com/translations/bn/fathulmajid.db"),
        TranslationMeta("ibnkathir", "Ibn Kathir (BN)", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://example.com/translations/bn/ibnkathir.db"),
    )
}
