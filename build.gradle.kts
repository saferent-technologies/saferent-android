// AGP 9 bundles Kotlin Gradle plugin 2.2.10. Firebase Auth 25 is compiled with Kotlin 2.4,
// which a 2.2 compiler can't read — so use a newer KGP (AGP's documented way to override it).
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0") // keep = kotlin in libs.versions.toml
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}