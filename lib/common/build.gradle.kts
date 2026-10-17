// UI-independent helpers: formatting, search, list moves.
plugins {
    id("dream.android.library")
}

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.kotlin.test)
}
