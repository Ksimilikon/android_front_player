// background playback: a foreground MediaSessionService whose Player is a thin
// adapter over PlayerController (lock screen, notification, headset buttons —
// the Android counterpart of MPRIS/SMTC in the TUI).
plugins {
    id("dream.android.library")
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
