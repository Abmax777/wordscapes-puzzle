# ─────────────────────────────────────────────────────────────────────────────
# R8 / ProGuard rules for the release build.
#
# Debug does not minify, so nothing here is exercised until assembleRelease.
# That is exactly why release-only breakage shows up late: everything below
# guards against R8 removing or renaming something that is only ever reached
# reflectively, which the shrinker cannot see.
# ─────────────────────────────────────────────────────────────────────────────

# ── kotlinx.serialization ────────────────────────────────────────────────────
# Serializers are generated as synthetic $$serializer classes and looked up
# reflectively through the Companion. R8 sees no call site and strips them, so
# levels.json parsing and type-safe navigation both fail at RUNTIME in release
# while compiling perfectly. This is the single most likely release-only
# failure in this project.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the generated serializer for anything annotated @Serializable.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our own serializable types, named explicitly so a rule change upstream
# cannot silently drop them.
-keep,includedescriptorclasses class com.wordscapes.puzzle.data.level.** { *; }
-keep,includedescriptorclasses class com.wordscapes.puzzle.data.level.**$$serializer { *; }

# ── Navigation Compose type-safe routes ──────────────────────────────────────
# Destination subclasses are encoded and decoded by name. Obfuscate them and
# the back stack cannot round-trip a route, so restoring after process death
# throws instead of returning to the level.
-keep class com.wordscapes.puzzle.ui.navigation.Destination { *; }
-keep class com.wordscapes.puzzle.ui.navigation.Destination$* { *; }
-keep,includedescriptorclasses class com.wordscapes.puzzle.ui.navigation.**$$serializer { *; }

# ── Domain models ────────────────────────────────────────────────────────────
# Not reflective, but keeping them makes release stack traces readable, which
# matters far more than the handful of kilobytes it costs.
-keep class com.wordscapes.puzzle.domain.model.** { *; }

# ── Kotlin metadata ──────────────────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,Exceptions,EnclosingMethod

# ── Crash readability ────────────────────────────────────────────────────────
# Without this, a release stack trace is unusable line-number soup. Keep the
# mapping file that assembleRelease writes to
# app/build/outputs/mapping/release/ if you ever need to deobfuscate.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Hilt, Dagger, Compose and Coroutines all ship consumer rules in their own
# artifacts. Do not duplicate them here — duplicates drift out of date and
# start suppressing warnings that matter.
