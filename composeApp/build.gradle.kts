import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // AGP 9: the shared module uses com.android.kotlin.multiplatform.library,
    // never com.android.library.
    android {
        namespace = "com.wordscapes.puzzle.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // Run commonTest on the Android JVM too, not only on desktop.
        withHostTest {}
    }

    // Desktop exists to prove the common code is platform-free. It fails on
    // exactly the Android assumptions iOS would, and costs nothing to run.
    jvm("desktop")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.datastore.preferences)
            implementation(libs.okio)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

// Deterministic package for the generated Res class, so imports do not depend
// on the module coordinates.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.wordscapes.puzzle.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.wordscapes.puzzle.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Wordscapes"
            packageVersion = "1.0.0"
        }
    }
}
