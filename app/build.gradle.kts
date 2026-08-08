plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android: AGP 9+ provides Kotlin itself. See root build.gradle.kts.
    alias(libs.plugins.kotlin.compose)       // Compose compiler plugin
    alias(libs.plugins.kotlin.serialization) // type-safe nav routes + level JSON
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)                  // Hilt code-gen — do NOT mix with kapt
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
            // Day 7: wire the keystore here. Read credentials from a gitignored
            // properties file — never commit them.
            // signingConfig = signingConfigs.getByName("release")
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

    // ── Unit tests ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
