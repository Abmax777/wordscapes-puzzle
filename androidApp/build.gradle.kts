import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android: AGP 9 provides Kotlin itself.
    alias(libs.plugins.kotlin.compose)
}

// Credentials live in a gitignored keystore.properties at the repo root, the
// keystore itself outside the repo. Absent file leaves the APK unsigned rather
// than failing, so the project clones and builds without a key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace  = "com.wordscapes.puzzle"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.wordscapes.puzzle"
        minSdk        = libs.versions.minSdk.get().toInt()
        targetSdk     = libs.versions.compileSdk.get().toInt()
        versionCode   = 1
        versionName   = "1.0"
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
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
}
