package dev.dreamplayer.data

import android.content.Context
import dev.dreamplayer.core.InitStatus
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.StatusKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * the player core for the interface: one instance per process, created in
 * `Application.onCreate` ([init]). Initializes the native core with the app
 * directories and wires the repositories together.
 */
class DreamCore private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob())
    /** every call into the core, its events and the messages to the user. */
    val log = CoreLog()
    private val rt = CoreRuntime(log)

    val paths = StoragePaths(context)
    val status = StatusBus(log)
    val covers = CoverStore(context, paths)
    val library = LibraryRepository(rt, status, paths.music, covers)
    val player: PlayerController
    val exchange: ExchangeRepository

    /** library sync / transfer with other devices. */
    val sync: SyncRepository

    /** deleting songs from the device (file + library). */
    val files: TrackFiles
    val settings = AppSettings(context)

    /**
     * why the native core failed to start (null — it is running). Without the
     * core every call fails with `NOT_INITIALIZED`; the interface shows this
     * once on start, since nobody listens to [status] yet at this point.
     */
    val initError: String?

    /**
     * a core panic that wasn't turned into a status in the previous run (it
     * killed a core thread or the app), from the core's `core_panic.log`;
     * null — none. Shown once on start, like [initError].
     */
    val previousCrash: String? = takePreviousCrash(paths.data)

    init {
        // the core needs a db; it restores the master volume from its own config.
        val code = rt.native.init(context, paths.cache.path, paths.config.path, paths.data.path, paths.music.path, true)
        initError = when (code) {
            InitStatus.OK, InitStatus.ALREADY_INITIALIZED -> null
            else -> "ядро не запустилось: ${describeInit(code)}"
        }
        previousCrash?.let { log.add(LogSource.Event, StatusKind.Warn, it) }
        initError?.let { log.add(LogSource.Event, StatusKind.Error, it) }
        player = PlayerController(rt, library, scope, status, log)
        exchange = ExchangeRepository(context, rt, paths, library, log, covers)
        sync = SyncRepository(context, rt, library, exchange, status, log, scope)
        files = TrackFiles(context, rt, library, player, status)
        AudioRouteWatcher(context, player, log).start()
        scope.launch { library.reload() }
    }

    /**
     * runs a long operation (import, export, scan) independently of the
     * interface: it isn't cancelled when its tab leaves the screen.
     */
    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    /**
     * syncs the library with the music directory (call once the media
     * permission is granted): only new / gone files reach the core.
     */
    fun scanMusicOnStart() {
        scope.launch { library.scan(null, announce = false) }
    }

    companion object {
        /** the core's panic log in the `data` directory (`api::panic`). */
        private const val PANIC_LOG = "core_panic.log"

        /**
         * moves the core's panic log of the previous run aside (so it's
         * reported once; the last copy stays in `core_panic.prev.log`) and
         * returns its last uncaught panic. Called before the core init: the
         * core appends to the log from then on.
         */
        private fun takePreviousCrash(dataDir: File): String? {
            val log = File(dataDir, PANIC_LOG)
            if (!log.exists()) return null
            val lines = runCatching { log.readLines() }.getOrDefault(emptyList())
            runCatching { log.renameTo(File(dataDir, "core_panic.prev.log").apply { delete() }) }
            // `<unix secs> [UNCAUGHT] thread '…' panicked at …: message`
            val last = lines.lastOrNull { "[UNCAUGHT]" in it } ?: return null
            return "в прошлом запуске ядро аварийно завершилось: ${last.substringAfter("[UNCAUGHT] ").take(300)}"
        }

        @Volatile
        private var instance: DreamCore? = null

        fun init(context: Context): DreamCore =
            instance ?: synchronized(this) { instance ?: DreamCore(context.applicationContext).also { instance = it } }

        val get: DreamCore get() = checkNotNull(instance) { "DreamCore.init was not called" }
    }
}
