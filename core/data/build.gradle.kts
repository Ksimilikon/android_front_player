// repositories over the core: library, playback, import / export. Every core
// call goes through one background thread (the core is a global mutex anyway).
plugins {
    id("dream.android.library")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:ffi"))
    implementation(project(":lib:common"))
    implementation(project(":lib:covers"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
