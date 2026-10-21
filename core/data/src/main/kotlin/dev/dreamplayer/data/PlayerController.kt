package dev.dreamplayer.data

import dev.dreamplayer.common.moved
import dev.dreamplayer.core.EngineStatus
import dev.dreamplayer.core.TrackEndListener
import dev.dreamplayer.model.Library
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.PlaybackState
import dev.dreamplayer.model.Queue
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * the core's name of the system default output device: its stream follows the
 * system routing (headphones plugged in / out).
 */
const val DEFAULT_OUTPUT = "Default Device"

/**
 * playback orchestration over the core engine (as `App` in the TUI): the
 * queue, next / previous, autoplay at the end of a track, volumes. Every
 * command runs on the core thread, so the state is changed by one thread only.
 */
class PlayerController internal constructor(
    private val rt: CoreRuntime,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val status: StatusBus,
    private val log: CoreLog,
) {
    private val native = rt.native

    private val state = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = state.asStateFlow()

    init {
        // the core reports the natural end of a track on its audio thread.
        native.setTrackEndListener(
            TrackEndListener {
                log.add(LogSource.Event, StatusKind.Info, "ядро: трек доиграл")
                launch { stepTrack(+1) }
            },
        )
        launch { state.update { it.copy(masterVolume = native.engineGetVolumeMaster().or(it.masterVolume)) } }
        // the library changed (edits, import) — rebuild the queue tracks.
        scope.launch { library.library.drop(1).collect { lib -> this@PlayerController.launch { refreshQueue(lib) } } }
        scope.launch(rt.dispatcher) { pollLoop() }
    }

    // ───────────────────────── queue ─────────────────────────

    /** starts playlist [name] from track [start]. */
    fun playPlaylist(name: String, start: Int) = launch {
        val pl = library.library.value.find(name)
        if (pl == null || pl.tracks.isEmpty()) {
            status.warn("плейлист пуст")
            return@launch
        }
        val queue = Queue(pl.name, pl.isPool, pl.tracks, start.coerceIn(0, pl.tracks.lastIndex))
        state.update { it.copy(queue = queue) }
        playCurrent()
    }

    /** plays queue track [index]. */
    fun select(index: Int) = launch {
        val q = state.value.queue ?: return@launch
        if (index !in q.tracks.indices) return@launch
        state.update { it.copy(queue = q.copy(current = index)) }
        playCurrent()
    }

    fun next() = launch { stepTrack(+1) }
    fun previous() = launch { stepTrack(-1) }

    /** moves a queue track (temporarily, not written to the db); playback goes on. */
    fun move(from: Int, to: Int) = launch {
        val q = state.value.queue ?: return@launch
        val cur = q.currentTrack?.key
        val tracks = q.tracks.moved(from, to)
        val current = tracks.indexOfFirst { it.key == cur }.takeIf { it >= 0 } ?: q.current
        state.update { it.copy(queue = q.copy(tracks = tracks, current = current)) }
    }

    /**
     * removes queue track [index]; [fromDb] — also from the playlist in the db
     * (only for a real playlist, not the pool). The rest of the queue keeps
     * its temporary order.
     */
    fun remove(index: Int, fromDb: Boolean) = launch {
        val q = state.value.queue ?: return@launch
        if (index !in q.tracks.indices) return@launch
        if (fromDb && q.isPool) {
            status.warn("из пула нельзя удалить через плейлист")
            return@launch
        }
        val removed = q.tracks[index]
        val tracks = q.tracks.toMutableList().apply { removeAt(index) }
        var current = if (index < q.current) q.current - 1 else q.current
        current = current.coerceAtMost((tracks.size - 1).coerceAtLeast(0))
        // the db list without the track: the coming library refresh sees no change.
        val sourceIds = if (fromDb) q.sourceIds.toMutableList().apply { removed.id?.let(::remove) } else q.sourceIds
        state.update { it.copy(queue = q.copy(tracks = tracks, current = current, sourceIds = sourceIds)) }
        if (fromDb) removed.id?.let { library.removeFromPlaylist(q.name, it) }
    }

    /**
     * saves the queue in its current order as playlist [name] (the one it
     * came from — overwritten, another name — a new playlist, which the queue
     * follows from then on, even when it came from the pool).
     */
    fun saveQueueAs(name: String) = launch {
        val q = state.value.queue ?: return@launch
        val ids = q.tracks.mapNotNull(Track::id)
        // before the save, so the library refresh finds the saved order equal to the queue.
        state.update { it.copy(queue = q.copy(name = name, isPool = false, sourceIds = ids)) }
        if (!library.savePlaylist(name, ids, cover = library.library.value.find(name)?.coverArt)) {
            state.update { s -> s.queue?.takeIf { it.name == name }?.let { s.copy(queue = it.copy(name = q.name, isPool = q.isPool, sourceIds = q.sourceIds)) } ?: s }
        }
    }

    /** playlist [from] was renamed to [to]: a queue taken from it follows the name. */
    fun renamePlaylist(from: String, to: String) = launch {
        state.update { s -> s.queue?.takeIf { it.name == from }?.let { s.copy(queue = it.copy(name = to)) } ?: s }
    }

    // ───────────────────────── transport ─────────────────────────

    fun playPause() = launch {
        when {
            isPaused() && needsReconnect -> resumeOnFreshStream()
            // nothing loaded (the track failed to load): try it again.
            isEngineEmpty() -> playCurrent()
            else -> native.enginePlayPause()
        }
        syncTransport()
    }

    fun play() = launch {
        when {
            needsReconnect -> resumeOnFreshStream()
            isEngineEmpty() -> playCurrent()
            else -> native.enginePlay()
        }
        syncTransport()
    }
    fun pause() = launch { native.enginePause(); syncTransport() }

    /** stops playback and forgets the queue. */
    fun stop() = launch {
        native.engineStop()
        state.update { PlaybackState(masterVolume = it.masterVolume) }
    }

    fun seek(seconds: Long) = launch {
        val code = native.engineSetSeek(seconds)
        if (code == EngineStatus.OK) state.update { it.copy(positionS = seconds) } else status.warn(describeEngine(code))
    }

    // ───────────────────────── volume ─────────────────────────

    fun setMasterVolume(v: Float) = launch {
        val vol = v.coerceIn(0f, 1f)
        when (val code = native.engineSetVolumeMaster(vol)) {
            EngineStatus.OK -> {}
            // applied, only the config on disk wasn't updated.
            EngineStatus.CONFIG_WRITE_FAILED -> status.warn("громкость не сохранится после перезапуска")
            else -> {
                status.error("не удалось изменить громкость: ${describeEngine(code)}")
                return@launch
            }
        }
        state.update { it.copy(masterVolume = vol) }
    }

    /**
     * per-track volume. For the playing track it's applied to the engine
     * immediately; [persist] also writes it to the db (at the end of a drag).
     */
    fun setTrackVolume(track: Track, v: Float, persist: Boolean) = launch {
        val vol = v.coerceIn(Track.VOLUME_MIN, Track.VOLUME_MAX)
        if (state.value.isCurrent(track)) {
            if (native.engineSetVolumeTrack(vol) == EngineStatus.OK) state.update { it.copy(trackVolume = vol) }
        }
        if (persist) track.id?.let { library.setTrackVolume(it, vol) }
    }

    // ───────────────────────── output device ─────────────────────────

    suspend fun outputDevices(): List<String> = rt.call { engineGetOutputDevices().or(emptyList()) }
    suspend fun outputDevice(): String? = rt.call { engineGetOutputDevice().value }

    /**
     * switches the output device with auto-resume: the engine drops the track
     * on a device change, so the position / pause are restored (as in the TUI).
     */
    fun switchDevice(name: String) = launch {
        val pos = position()
        val paused = isPaused()
        val code = native.engineSetOutputDevice(name)
        if (code != EngineStatus.OK) {
            status.error("не удалось переключиться на: $name (${describeEngine(code)})")
            return@launch
        }
        needsReconnect = false
        if (state.value.queue != null) {
            playCurrent()
            if (pos > 0) native.engineSetSeek(pos)
            if (paused) native.enginePause()
            syncTransport()
        }
        status.info("устройство вывода: ${if (name == DEFAULT_OUTPUT) "системное" else name}")
    }

    // ───────────────────── output route changes ─────────────────────
    //
    // Android disconnects the core's AAudio stream when the output route
    // changes (headphones / Bluetooth plugged in or out). The core doesn't
    // notice — rodio only logs the stream error — so every following track
    // goes into a dead mixer and nothing plays. The stream is reopened on the
    // system default device ([DEFAULT_OUTPUT], which follows the routing) and
    // the current track restored, as on a manual device switch.

    /** the engine's stream may be dead: reopen it before the next play. */
    private var needsReconnect = false
    private var lastPos = -1L
    private var stalledPolls = 0
    private var lastRecoveryAt = 0L

    /** an external output (headphones, Bluetooth, USB) went away: pause. */
    fun onOutputRemoved(name: String) = launch {
        needsReconnect = true
        if (state.value.queue != null && !isPaused()) {
            native.enginePause()
            syncTransport()
            status.info("$name: отключено — пауза")
        }
    }

    /** a new external output appeared: move the sound to it right away if playing. */
    fun onOutputAdded(name: String) = launch {
        needsReconnect = true
        if (state.value.queue == null || isPaused()) return@launch
        delay(ROUTE_SETTLE_MS) // let the system finish switching the route
        if (reconnect(resume = true)) {
            status.info("звук переведён: $name")
        } else {
            native.enginePause()
            syncTransport()
            status.error("не удалось перевести звук на $name. Выберите устройство в настройках")
        }
    }

    /** play after a route change: on a fresh stream if the core can open one. */
    private fun resumeOnFreshStream() {
        if (!reconnect(resume = true)) {
            status.error("не удалось открыть устройство вывода. Выберите его в настройках")
        }
    }

    /**
     * opens a new stream on the system default device and restores the current
     * track, position and pause. false — the core couldn't open it.
     */
    private fun reconnect(resume: Boolean): Boolean {
        val pos = position()
        if (native.engineSetOutputDevice(DEFAULT_OUTPUT) != EngineStatus.OK) return false
        needsReconnect = false
        stalledPolls = 0
        if (state.value.queue != null) {
            playCurrent()
            if (pos > 0) native.engineSetSeek(pos)
            if (!resume) native.enginePause()
            syncTransport()
        }
        return true
    }

    /**
     * the safety net: "playing" but the position stands still — the stream is
     * dead (a route change the watcher missed). One recovery attempt; if it
     * can't help, pause and say so instead of pretending to play.
     */
    private fun checkStall() {
        val s = state.value
        val playing = s.queue != null && !s.isPaused
        stalledPolls = if (playing && s.positionS == lastPos) stalledPolls + 1 else 0
        lastPos = s.positionS
        if (stalledPolls < STALL_POLLS) return
        stalledPolls = 0
        log.add(LogSource.System, StatusKind.Warn, "позиция стоит ${STALL_POLLS * POLL_MS / 1000} с — поток вывода мёртв")
        val now = System.currentTimeMillis()
        val recovered = now - lastRecoveryAt > RECOVERY_INTERVAL_MS && reconnect(resume = true)
        lastRecoveryAt = now
        if (!recovered) {
            native.enginePause()
            needsReconnect = true
            syncTransport()
            status.error("звук пропал: сменилось устройство вывода. Выберите устройство в настройках или перезапустите приложение")
        }
    }

    // ───────────────────────── internals ─────────────────────────

    /** runs [block] on the core thread (commands are serialized). */
    private fun launch(block: suspend CoreCalls.() -> Unit) = scope.launch(rt.dispatcher) { native.block() }

    private fun stepTrack(delta: Int) {
        val q = state.value.queue ?: return
        if (q.tracks.isEmpty()) return
        val n = q.tracks.size
        state.update { it.copy(queue = q.copy(current = ((q.current + delta) % n + n) % n)) }
        playCurrent()
    }

    /** plays the track under `queue.current` (by id or path). */
    private fun playCurrent() {
        val track = state.value.currentTrack ?: return
        // a new track into a dead stream would be silent: reopen it first.
        if (needsReconnect && native.engineSetOutputDevice(DEFAULT_OUTPUT) == EngineStatus.OK) needsReconnect = false
        val code = when {
            track.id != null -> native.engineAddTrackId(track.id!!)
            track.path != null -> native.engineAddTrackPath(track.path!!)
            else -> null // neither id nor path: nothing to play
        }
        if (code != EngineStatus.OK) {
            // the core leaves the engine as it was: the previous track would
            // keep playing under the new one's title.
            native.engineStop()
            val why = code?.let { " (${describeEngine(it)})" } ?: ""
            status.error("не удалось воспроизвести: ${track.title}$why")
        }
        state.update { it.copy(positionS = 0) }
        syncTransport()
    }

    /** a failed engine read keeps the previous value. */
    private fun syncTransport() {
        // a stopped engine still reports the last track's position.
        val empty = native.engineIsEmpty().or(true)
        state.update {
            it.copy(
                isPaused = isPaused() || empty,
                positionS = if (empty) 0 else native.engineGetSeek().or(it.positionS),
                durationS = native.engineGetDuration().or(it.durationS),
                trackVolume = native.engineGetVolumeTrack().or(it.trackVolume),
            )
        }
    }

    /** a failed read counts as paused: nothing is known to be playing. */
    private fun isPaused(): Boolean = native.engineIsPause().or(true)

    /** a failed read counts as loaded: don't reload a track over a playing one. */
    private fun isEngineEmpty(): Boolean = state.value.currentTrack != null && native.engineIsEmpty().or(false)

    private fun position(): Long = native.engineGetSeek().or(0L)

    /** the position for the progress bar; cheap, but only while there is a queue. */
    private suspend fun pollLoop() {
        while (scope.isActive) {
            delay(POLL_MS)
            native.newPanic()?.let { why ->
                log.add(LogSource.Event, StatusKind.Error, "паника ядра: $why")
                status.error("сбой в потоке ядра: ${why.take(200)}")
            }
            if (state.value.queue != null) {
                syncTransport()
                checkStall()
            }
        }
    }

    /**
     * rebuilds the queue tracks from the updated library (as `refresh_playing`
     * in the TUI): when the playlist changed in the db (tracks or their
     * order), the queue is taken from it again; otherwise the queue keeps its
     * own (temporary) order and only refreshes the track data.
     */
    private fun refreshQueue(lib: Library) {
        val q = state.value.queue ?: return
        val src = lib.find(q.name)
        val srcIds = src?.tracks?.mapNotNull(Track::id)
        val byId = lib.pool.tracks.filter { it.id != null }.associateBy { it.id }
        val tracks = when {
            src != null && srcIds != q.sourceIds -> src.tracks
            else -> q.tracks.map { t -> t.id?.let(byId::get) ?: t }
        }
        val cur = q.currentTrack?.key
        val current = tracks.indexOfFirst { it.id != null && it.id == cur?.id }.takeIf { it >= 0 }
            ?: q.current.coerceAtMost((tracks.size - 1).coerceAtLeast(0))
        state.update { it.copy(queue = q.copy(tracks = tracks, current = current, sourceIds = srcIds ?: q.sourceIds)) }
    }

    private companion object {
        const val POLL_MS = 500L

        /** ~3 s of a frozen position while playing = a dead stream. */
        const val STALL_POLLS = 6
        const val RECOVERY_INTERVAL_MS = 10_000L
        const val ROUTE_SETTLE_MS = 700L
    }
}
