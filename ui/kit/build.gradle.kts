// shared interface building blocks: theme, the unified list, player bar,
// now-playing strip, vertical slider, forms, dialogs, the tab contract.
plugins {
    id("dream.android.library")
    id("dream.android.compose")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":lib:common"))
    implementation(project(":lib:covers"))
}
