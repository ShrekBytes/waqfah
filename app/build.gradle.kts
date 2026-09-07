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
    namespace = "com.shrekbytes.waqfah"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shrekbytes.waqfah"
        minSdk = 28
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
