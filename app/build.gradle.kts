import java.util.Properties

// the application: tab host, root screen, activity, permissions.
plugins {
    id("dream.android.application")
}

// the core repository (as the core plugin finds it): its version and
// license go into the ABOUT tab.
val coreDir: File = run {
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.isFile }?.reader()?.use(::load)
    }
    val path = local.getProperty("dream.core.dir") ?: providers.gradleProperty("dream.core.dir").orNull ?: "../rs_dream_player"
    rootProject.file(path)
}
/** the `[package]` version of the core's Cargo.toml (the `dream_player` crate). */
val coreVersion: String = coreDir.resolve("Cargo.toml").takeIf { it.isFile }?.readText()
    ?.let { Regex("""(?m)^version\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
    ?: "?"

// the license texts of the app and the core, as assets (licenses/app.txt, licenses/core.txt).
val licensesDir = layout.buildDirectory.dir("generated/licenses")

val copyLicenses = tasks.register<Copy>("copyLicenses") {
    from(rootProject.file("LICENSE")) { rename { "app.txt" } }
    from(coreDir.resolve("LICENSE")) { rename { "core.txt" } }
    into(licensesDir.map { it.dir("licenses") })
}
tasks.named("preBuild") { dependsOn(copyLicenses) }

android {
    defaultConfig {
        applicationId = "dev.dreamplayer"
        versionCode = 3
        versionName = "0.2"
        buildConfigField("String", "CORE_VERSION", "\"$coreVersion\"")
        // only the ABIs the core .so is built for (dream.abis): on any other
        // the app would install and crash on System.loadLibrary.
        ndk {
            abiFilters += providers.gradleProperty("dream.abis").get().split(',').map(String::trim)
        }
    }
    buildFeatures { buildConfig = true }
    // a plain path: AGP takes no Provider here (copyLicenses runs before preBuild).
    sourceSets["main"].assets.srcDir(licensesDir.get().asFile)
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
