// cover loading: a separate cover file or the picture embedded in the audio
// tags (MediaMetadataRetriever), downscaled and cached.
plugins {
    id("dream.android.library")
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
