import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** an Android library module (no UI). */
class DreamLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            if (file("consumer-rules.pro").exists()) {
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
            }
        }
    }
}

/** enables Compose in an Android module (library or application). */
class DreamComposePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.findLibrary("compose-bom").get())
            add("implementation", bom)
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-foundation").get())
            add("implementation", libs.findLibrary("compose-material3").get())
            add("implementation", libs.findLibrary("compose-material-icons").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}

/** a tab / screen: Compose + the interface kit + the core repositories. */
class DreamFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dream.android.library")
        pluginManager.apply("dream.android.compose")
        dependencies {
            add("implementation", project(":ui:kit"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":lib:common"))
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-activity-compose").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
        }
    }
}

class DreamApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig.targetSdk = Sdk.TARGET
        }
        pluginManager.apply("dream.android.compose")
    }
}
