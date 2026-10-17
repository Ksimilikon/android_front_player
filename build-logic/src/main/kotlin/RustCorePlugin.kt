import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.util.Properties
import javax.inject.Inject

/**
 * builds the player core (`rs_dream_player`, crate `dream_player`) with
 * cargo-ndk into `libdream_player.so` for every ABI and adds it to the
 * module's jniLibs. Settings (gradle.properties / local.properties):
 * - `dream.core.dir` — the core repository (default `../rs_dream_player`);
 * - `dream.abis` — comma-separated ABIs;
 * - `dream.ndk.dir` — NDK (falls back to `ANDROID_NDK_HOME`);
 * - `dream.cargo` — cargo binary (default `cargo`; cargo-ndk is found in ~/.cargo/bin).
 *
 * Also checks that the Kotlin JNI contract in this module matches
 * `bindings/android` of the core (`syncCoreBindings` copies it over).
 */
class RustCorePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val local = Properties().apply {
            val text = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
                .asText.orNull
            if (text != null) load(text.reader())
        }
        fun prop(name: String): String? =
            local.getProperty(name) ?: providers.gradleProperty(name).orNull

        val coreDir = rootProject.layout.projectDirectory.dir(prop("dream.core.dir") ?: "../rs_dream_player")
        val bindingsSrc = coreDir.dir("bindings/android/dev/dreamplayer/core")
        val bindingsDst = layout.projectDirectory.dir("src/main/kotlin/dev/dreamplayer/core")

        tasks.register<Sync>("syncCoreBindings") {
            group = "dream"
            description = "copies the Kotlin JNI contract from the core repository"
            from(bindingsSrc)
            into(bindingsDst)
        }
        val verify = tasks.register<VerifyBindingsTask>("verifyCoreBindings") {
            group = "dream"
            expected.from(bindingsSrc.asFileTree)
            actual.from(bindingsDst.asFileTree)
        }

        val cargoNdk = tasks.register<CargoNdkTask>("cargoNdkBuild") {
            group = "dream"
            description = "builds libdream_player.so with cargo-ndk"
            dependsOn(verify)
            this.coreDir.set(coreDir)
            abis.set((prop("dream.abis") ?: "arm64-v8a,x86_64").split(',').map(String::trim).filter(String::isNotEmpty))
            cargo.set(prop("dream.cargo") ?: "cargo")
            ndkDir.set(prop("dream.ndk.dir") ?: System.getenv("ANDROID_NDK_HOME") ?: "")
            platform.set(Sdk.MIN)
            outputDir.set(layout.buildDirectory.dir("rustJniLibs"))
        }

        extensions.getByType<LibraryAndroidComponentsExtension>().onVariants { variant ->
            variant.sources.jniLibs?.addGeneratedSourceDirectory(cargoNdk, CargoNdkTask::outputDir)
        }
    }
}

abstract class CargoNdkTask : DefaultTask() {
    @get:Internal
    abstract val coreDir: DirectoryProperty

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val cargo: Property<String>

    @get:Input
    abstract val ndkDir: Property<String>

    @get:Input
    abstract val platform: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    init {
        // cargo has its own incremental build: always ask it.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() {
        val ndk = ndkDir.get()
        if (ndk.isEmpty()) {
            throw GradleException("NDK not found: set dream.ndk.dir in local.properties or ANDROID_NDK_HOME")
        }
        val args = buildList {
            add("ndk")
            abis.get().forEach { add("-t"); add(it) }
            add("-P"); add(platform.get().toString())
            add("-o"); add(outputDir.get().asFile.absolutePath)
            add("build"); add("--release"); add("--lib")
        }
        exec.exec {
            workingDir = coreDir.get().asFile
            environment("ANDROID_NDK_HOME", ndk)
            // cargo subcommands (cargo-ndk) live in ~/.cargo/bin, often not on PATH.
            val cargoBin = System.getProperty("user.home") + "/.cargo/bin"
            environment("PATH", cargoBin + java.io.File.pathSeparator + System.getenv("PATH").orEmpty())
            commandLine(listOf(cargo.get()) + args)
        }
        // cargo-ndk copies every cdylib of the build (e.g. libapi.so of the
        // `api` crate); the app loads only the core library.
        outputDir.get().asFile.walk()
            .filter { it.isFile && it.name != LIBRARY }
            .forEach { it.delete() }
    }

    private companion object {
        const val LIBRARY = "libdream_player.so"
    }
}

abstract class VerifyBindingsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val expected: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val actual: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val want = expected.files.associateBy { it.name }
        val have = actual.files.associateBy { it.name }
        val diff = want.filter { (name, f) -> have[name]?.readText() != f.readText() }.keys
        if (diff.isNotEmpty()) {
            throw GradleException(
                "the JNI contract differs from the core bindings: $diff. Run ./gradlew :core:ffi:syncCoreBindings"
            )
        }
    }
}
