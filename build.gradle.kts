buildscript {
    dependencies {
        // AGP 9 ships with built-in Kotlin pinned to an older KGP; put the
        // catalog version on the classpath so the Compose compiler plugin and
        // Kotlin agree.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
