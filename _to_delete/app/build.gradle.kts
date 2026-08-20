import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android: AGP 9+ provides Kotlin itself. See root build.gradle.kts.
    alias(libs.plugins.kotlin.compose)       // Compose compiler plugin
    alias(libs.plugins.kotlin.serialization) // type-safe nav routes + level JSON
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)                  // Hilt code-gen — do NOT mix with kapt
}

/**
 * Release signing.
 *
 * Credentials live in keystore.properties at the repo root, which is
 * gitignored, and the keystore itself lives outside the repo entirely. Neither
 * is ever committed — see .gitignore for *.jks, *.keystore and
 * keystore.properties.
 *
 * When the file is absent the release build simply stays unsigned rather than
 * failing, so anyone can clone and `assembleRelease` without needing a key.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace  = "com.wordscapes.puzzle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wordscapes.puzzle"
        minSdk        = 24
        targetSdk     = 36
        versionCode   = 1
        versionName   = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null when keystore.properties is absent, which leaves the APK
            // unsigned rather than failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Under built-in Kotlin, the Kotlin jvmTarget defaults to whatever
    // targetCompatibility is set to here, so there is no separate
    // `kotlin { compilerOptions { jvmTarget = ... } }` block to keep in sync.
    // Setting it in two places is how they drift.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Off by default since AGP 8.0. Needed so debug-only affordances
        // (the wheel sandbox entry point) can be compiled out of release.
        buildConfig = true
    }
}

dependencies {
    // ── Compose BOM (pins every androidx.compose.* artifact together) ────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)   // Canvas / DrawScope
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)    // pointerInput / gestures
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)

    // ── Activity ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)

    // ── Navigation (2.8+ for the type-safe composable<T> API) ────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── DataStore ────────────────────────────────────────────────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Lifecycle / ViewModel ────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Serialization ────────────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── Debug only ───────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Unit tests (JVM, no device) ──────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // ── Instrumented tests (on device) ───────────────────────────────────────
    // The AS template generates ExampleInstrumentedTest.kt in androidTest.
    // Without these it does not compile, and while neither `assembleRelease`
    // nor `testDebugUnitTest` touches that source set, a full Build does — so
    // the breakage only appears when building the APK from the IDE.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
