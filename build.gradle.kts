// Root build file — plugin declarations only, no configuration here.
//
// NOTE: there is deliberately no `kotlin-android` plugin here.
// Since AGP 9.0, Kotlin support is built into the Android Gradle Plugin, and
// applying org.jetbrains.kotlin.android alongside it is a fatal error, not a
// warning. AGP carries its own KGP runtime (2.2.10 as of AGP 9.0) and upgrades
// it if the project asks for something newer.
//
// Kotlin *compiler plugins* are unaffected and still applied explicitly —
// compose, serialization, and parcelize all continue to need their own entry.
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt)                 apply false
    alias(libs.plugins.ksp)                  apply false
}
