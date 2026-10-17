import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** shared SDK levels of every module. minSdk 26: the core plays through AAudio. */
object Sdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 26
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** `:feature:song` -> `dev.dreamplayer.feature.song`. */
internal val Project.dreamNamespace: String
    get() = "dev.dreamplayer" + path.replace(':', '.').replace('-', '_')

/** settings shared by the application and the libraries. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.apply {
        namespace = dreamNamespace
        compileSdk = Sdk.COMPILE
        defaultConfig.minSdk = Sdk.MIN
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
