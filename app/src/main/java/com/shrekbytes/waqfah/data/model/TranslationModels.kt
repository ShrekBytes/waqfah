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
        TranslationMeta("hilalimuhsinkhan", "Hilali & Muhsin Khan", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/en/hilalimuhsinkhan.db"),
        TranslationMeta("yusufali", "Yusuf Ali", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/yusufali.db"),
        TranslationMeta("pickthall", "Pickthall", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/en/pickthall.db"),
        TranslationMeta("maududi", "Al Maududi", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/en/maududi.db"),
        TranslationMeta("arberry", "Arberry", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/en/arberry.db"),
        TranslationMeta("taisirul", "Taisirul Quran", TranslationLanguage.BENGALI, isBundled = true, downloadUrl = null),
        TranslationMeta("rawaialbayan", "Rawai Al-Bayan", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/bn/rawaialbayan.db"),
        TranslationMeta("fathulmajid", "Fathul Majid", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/bn/fathulmajid.db"),
        TranslationMeta("mujiburrahman", "Sheikh Mujibur Rahman", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/bn/mujiburrahman.db"),
        TranslationMeta("muhiuddinkhan", "Muhiuddin Khan", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/bn/muhiuddinkhan.db"),
        TranslationMeta("johurulhaque", "Johurul Haque", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah/main/translations/bn/johurulhaque.db"),
    )
}