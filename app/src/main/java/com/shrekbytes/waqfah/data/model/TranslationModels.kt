package com.shrekbytes.waqfah.data.model

enum class TranslationLanguage(val code: String) { ENGLISH("en"), BENGALI("bn") }

// Which catalog language corresponds to an aid display language; null when no
// aid text should be shown at all.
fun AidLanguage.toTranslationLanguage(): TranslationLanguage? = when (this) {
    AidLanguage.NONE -> null
    AidLanguage.ENGLISH -> TranslationLanguage.ENGLISH
    AidLanguage.BENGALI -> TranslationLanguage.BENGALI
}

data class TranslationMeta(
    val id: String,
    val name: String,
    val language: TranslationLanguage,
    val isBundled: Boolean,
    val downloadUrl: String?, // null for bundled — comes from assets instead
    // SHA-256 of the published .db file, verified before a download is moved
    // into place (see TranslationRepository.verifyChecksum). Null for bundled
    // translations — APK signing already covers their integrity.
    val checksumSha256: String? = null,
)

object TranslationCatalog {
    // MAINTENANCE: when a .db file in the ShrekBytes/waqfah-translations repo
    // changes, regenerate its checksum (`sha256sum <id>.db`) and update the
    // matching entry below — downloads fail their integrity check until the
    // pinned value matches.
    val all = listOf(
        TranslationMeta("sahih", "Sahih International", TranslationLanguage.ENGLISH, isBundled = true, downloadUrl = null),
        TranslationMeta("pickthall", "Pickthall", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/en/pickthall.db", checksumSha256 = "41f7ee525abe40f87c3dfc342b681b11e2ab78866d58803b9971ef2dad4127df"),
        TranslationMeta("yusufali", "Yusuf Ali", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/en/yusufali.db", checksumSha256 = "2b63fc9868938124e9ac814b58c133cd2b0754753293eaef09193ec760c7742e"),
        TranslationMeta("hilalimuhsinkhan", "Hilali & Muhsin Khan", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/en/hilalimuhsinkhan.db", checksumSha256 = "fa2df58d7c910f73a81110c2756c55f3a0ac2274b840957e12c76df966f34d18"),
        TranslationMeta("maududi", "Al Maududi", TranslationLanguage.ENGLISH, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/en/maududi.db", checksumSha256 = "96fe2ebdf718937ccda5ad74eb01f37adc8241e78a540d9721eafc5e9283dcfa"),
        TranslationMeta("taisirul", "Taisirul Quran", TranslationLanguage.BENGALI, isBundled = true, downloadUrl = null),
        TranslationMeta("rawaialbayan", "Rawai Al-Bayan", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/bn/rawaialbayan.db", checksumSha256 = "149ac219cae22b181fee46f5694d4611147a90a02bebe57ce706ddda1378f01c"),
        TranslationMeta("fathulmajid", "Fathul Majid", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/bn/fathulmajid.db", checksumSha256 = "1fa7f6f9d782e7a71522a786a7711bc43bd7c62b0ea48fb11189828619ab7355"),
        TranslationMeta("mujiburrahman", "Sheikh Mujibur Rahman", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/bn/mujiburrahman.db", checksumSha256 = "c89346bf409fd546e3d071c07844da97265861bf7e54a2ccb123df31d56d1bed"),
        TranslationMeta("muhiuddinkhan", "Muhiuddin Khan", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/bn/muhiuddinkhan.db", checksumSha256 = "097a37ad4b1f0fa76586455fb026a5262077c16d1523299332f7200aeb3ed8a2"),
        TranslationMeta("johurulhaque", "Johurul Haque", TranslationLanguage.BENGALI, isBundled = false, downloadUrl = "https://raw.githubusercontent.com/ShrekBytes/waqfah-translations/main/bn/johurulhaque.db", checksumSha256 = "638aa3b2138f5f80e09d5e6b03ddfe72a0079a316c76630b317ce02d7bf4f1f6"),
    )
}