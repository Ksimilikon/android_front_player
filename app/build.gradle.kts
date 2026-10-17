// the application: tab host, root screen, activity, permissions.
plugins {
    id("dream.android.application")
}

android {
    defaultConfig {
        applicationId = "dev.dreamplayer"
        versionCode = 1
        versionName = "0.1.0"
        // only the ABIs the core .so is built for (dream.abis): on any other
        // the app would install and crash on System.loadLibrary.
        ndk {
            abiFilters += providers.gradleProperty("dream.abis").get().split(',').map(String::trim)
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // a local build without a release key: sign with the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:playback"))
    implementation(project(":lib:common"))
    implementation(project(":ui:kit"))
    implementation(project(":feature:playlists"))
    implementation(project(":feature:queue"))
    implementation(project(":feature:song"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:meta"))
    implementation(project(":feature:exchange"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:logs"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
}
