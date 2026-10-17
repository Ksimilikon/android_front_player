plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "dream.android.library"
            implementationClass = "DreamLibraryPlugin"
        }
        register("androidCompose") {
            id = "dream.android.compose"
            implementationClass = "DreamComposePlugin"
        }
        register("androidFeature") {
            id = "dream.android.feature"
            implementationClass = "DreamFeaturePlugin"
        }
        register("androidApplication") {
            id = "dream.android.application"
            implementationClass = "DreamApplicationPlugin"
        }
        register("rustCore") {
            id = "dream.rust.core"
            implementationClass = "RustCorePlugin"
        }
    }
}
