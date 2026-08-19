// Root build file — plugin declarations only.
// AGP 9 provides Kotlin itself, so there is no kotlin-android plugin. Compiler
// plugins (compose, serialization) are still applied per module.
plugins {
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.kmp.library)   apply false
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.compose)        apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
