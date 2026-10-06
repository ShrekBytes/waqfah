import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing — see README "Releasing". Read from keystore.properties at
// the repo root (gitignored), with WAQFAH_* environment variables as overrides
// so CI can inject secrets without a file on disk. No configuration at all
// leaves the release build unsigned (CI's compile-only assembleRelease relies
// on that); a PARTIAL configuration fails the build instead of quietly
// producing an artifact that can't install or update.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

fun releaseSigningProperty(property: String, envVar: String): String? =
    System.getenv(envVar) ?: keystoreProperties.getProperty(property)

android {
    namespace = "dev.shrekbytes.waqfah"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.shrekbytes.waqfah"
        minSdk = 28
        targetSdk = 37
        versionCode = 5
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two store channels, two application IDs. F-Droid signs with its own key,
    // which can never match the Play signing key, so the two builds must not
    // share an application ID — otherwise installing one over the other is
    // impossible and the user has to uninstall first. The `play` flavour keeps
    // the bare ID; `fdroid` takes the conventional `.fdroid` suffix.
    //
    // `namespace` and `applicationId` are the same string again, but they are
    // still independent knobs: the namespace is what the Kotlin packages, the
    // generated R class, the Hilt wiring, the Room schema directory and
    // TriggerActivity::class.java.name (which the interstitial-return rule
    // matches on) key off, while the application ID is the install identity the
    // flavours vary. Changing one does not require changing the other — see the
    // note in AGENTS.md before moving the namespace, because the Room schema
    // directory under app/schemas/ is named after it.
    flavorDimensions += "store"

    productFlavors {
        create("play") {
            dimension = "store"
        }
        create("fdroid") {
            dimension = "store"
            applicationIdSuffix = ".fdroid"
        }
    }

    signingConfigs {
        if (releaseSigningProperty("storeFile", "WAQFAH_STORE_FILE") != null) {
            create("release") {
                storeFile = releaseSigningProperty("storeFile", "WAQFAH_STORE_FILE")
                    ?.let { rootProject.file(it) }
                storePassword = releaseSigningProperty("storePassword", "WAQFAH_STORE_PASSWORD")
                keyAlias = releaseSigningProperty("keyAlias", "WAQFAH_KEY_ALIAS")
                keyPassword = releaseSigningProperty("keyPassword", "WAQFAH_KEY_PASSWORD")
                require(storePassword != null && keyAlias != null && keyPassword != null) {
                    "Release signing is half-configured: set all of storeFile, " +
                        "storePassword, keyAlias, and keyPassword in keystore.properties " +
                        "or as WAQFAH_* environment variables (see README \"Releasing\")"
                }
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // Signed only when a keystore is configured (see signingConfigs);
            // otherwise assembleRelease emits app-release-unsigned.apk.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Room's migration test helper reads the exported schema JSONs from
        // androidTest assets; without this AppStateMigrationTest fails on load
        // before running a single test.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

ksp {
    // Room migration tests need a real "before" schema to diff against — this
    // writes each version's schema to JSON on every build. WaqfahAppDatabase
    // holds real user progress and its own docs call for hand-written
    // Migrations if it ever changes, so this is the safety net for those.
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Store release notes are keyed by versionCode, not versionName: a name can be
// reused and a code cannot. F-Droid reads them for the listing's "What's new"
// and Play's notes should mirror the same text, so a versionCode with no
// changelog ships with nothing to show and nothing to flag it — the failure is
// silent by construction, which is the whole reason this exists.
//
// Every locale under fastlane/metadata/android/ that carries store metadata
// must carry a changelog for the version being released, so adding a locale
// later cannot leave a silent hole behind. The release artifact tasks depend on
// this, which is also how CI gets it for free: the R8 check assembles both
// release variants and the AAB check bundles play.
//
// Deliberately declares no outputs — a task with no outputs is never up to
// date, so it re-checks on every release build instead of being skipped.
val storeMetadataDir = rootProject.layout.projectDirectory.dir("fastlane/metadata/android")
val releasedVersionCode = android.defaultConfig.versionCode
    ?: error("versionCode is not set in defaultConfig")

val checkChangelog by tasks.registering {
    group = "verification"
    description = "Fails unless every store locale has release notes for this versionCode."

    val metadataDir = storeMetadataDir
    val versionCode = releasedVersionCode
    val versionName = android.defaultConfig.versionName
    inputs.property("versionCode", versionCode)

    doLast {
        val locales = metadataDir.asFile.listFiles { file -> file.isDirectory }.orEmpty()
        check(locales.isNotEmpty()) {
            "No store locales found under ${metadataDir.asFile}."
        }

        val missing = locales.sortedBy { it.name }
            .map { it.resolve("changelogs/$versionCode.txt") }
            .filterNot { it.isFile }
            .map { it.relativeTo(metadataDir.asFile).path }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "No release notes for version $versionName (versionCode $versionCode). " +
                    "Add " + missing.joinToString(", ") + " — the store listing shows them " +
                    "as \"What's new\", and a release with none ships silently. " +
                    "See docs/RELEASING.md.",
            )
        }
    }
}

tasks.matching { task ->
    task.name.endsWith("Release") &&
        (task.name.startsWith("assemble") || task.name.startsWith("bundle"))
}.configureEach { dependsOn(checkChangelog) }

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Compose icon set (Icons.Default.*)
    implementation(libs.androidx.compose.material.icons.core)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.core)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Android tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.testing)

    // Debug
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
