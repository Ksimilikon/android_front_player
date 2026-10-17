package dev.dreamplayer.data

import dev.dreamplayer.common.moved
import dev.dreamplayer.core.EngineStatus
import dev.dreamplayer.core.Native
import dev.dreamplayer.core.TrackEndListener
import dev.dreamplayer.model.Library
import dev.dreamplayer.model.PlaybackState
import dev.dreamplayer.model.Queue
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
 * playback orchestration over the core engine (as `App` in the TUI): the
 * queue, next / previous, autoplay at the end of a track, volumes. Every
 * command runs on the core thread, so the state is changed by one thread only.
 */
class PlayerController internal constructor(
    private val rt: CoreRuntime,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val status: StatusBus,
) {
    private val state = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = state.asStateFlow()

    init {
        // the core reports the natural end of a track on its audio thread.
        Native.setTrackEndListener(TrackEndListener { launch { stepTrack(+1) } })
        launch { state.update { it.copy(masterVolume = Native.engineGetVolumeMaster().or(it.masterVolume)) } }
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
     * (only for a real playlist, not the pool).
     */
    fun remove(index: Int, fromDb: Boolean) = launch {
        val q = state.value.queue ?: return@launch
        if (index !in q.tracks.indices) return@launch
        if (fromDb && q.isPool) {
            status.warn("из пула нельзя удалить через плейлист")
            return@launch
        }
        val tracks = q.tracks.toMutableList().apply { removeAt(index) }
        var current = if (index < q.current) q.current - 1 else q.current
        current = current.coerceAtMost((tracks.size - 1).coerceAtLeast(0))
        state.update { it.copy(queue = q.copy(tracks = tracks, current = current)) }
        if (fromDb) library.savePlaylist(q.name, tracks.mapNotNull(Track::id))
    }

    // ───────────────────────── transport ─────────────────────────

    fun playPause() = launch {
        if (isPaused() && needsReconnect) resumeOnFreshStream() else Native.enginePlayPause()
        syncTransport()
    }

    fun play() = launch {
        if (needsReconnect) resumeOnFreshStream() else Native.enginePlay()
        syncTransport()
    }
    fun pause() = launch { Native.enginePause(); syncTransport() }

    /** stops playback and forgets the queue. */
    fun stop() = launch {
        Native.engineStop()
        state.update { PlaybackState(masterVolume = it.masterVolume) }
    }

    fun seek(seconds: Long) = launch {
        val code = Native.engineSetSeek(seconds)
        if (code == EngineStatus.OK) state.update { it.copy(positionS = seconds) } else status.warn(describeEngine(code))
    }

    // ───────────────────────── volume ─────────────────────────

    fun setMasterVolume(v: Float) = launch {
        val vol = v.coerceIn(0f, 1f)
        when (val code = Native.engineSetVolumeMaster(vol)) {
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
            if (Native.engineSetVolumeTrack(vol) == EngineStatus.OK) state.update { it.copy(trackVolume = vol) }
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
        val code = Native.engineSetOutputDevice(name)
        if (code != EngineStatus.OK) {
            status.error("не удалось переключиться на: $name (${describeEngine(code)})")
            return@launch
        }
        needsReconnect = false
        if (state.value.queue != null) {
            playCurrent()
            if (pos > 0) Native.engineSetSeek(pos)
            if (paused) Native.enginePause()
            syncTransport()
        }
        status.info("устройство вывода: $name")
    }

    // ───────────────────── output route changes ─────────────────────
    //
    // Android disconnects the core's AAudio stream when the output route
    // changes (headphones / Bluetooth plugged in or out). The core doesn't
    // notice — rodio only logs the stream error — so every following track
    // goes into a dead mixer and nothing plays. The only way to get a new
    // stream through the current core API is `engineSetOutputDevice`, after
    // which the current track is restored (as on a manual device switch).

    /** the engine's stream may be dead: reopen it before the next play. */
    private var needsReconnect = false
    private var lastPos = -1L
    private var stalledPolls = 0
    private var lastRecoveryAt = 0L

    /** an external output (headphones, Bluetooth, USB) went away: pause. */
    fun onOutputRemoved(name: String) = launch {
        needsReconnect = true
        if (state.value.queue != null && !isPaused()) {
            Native.enginePause()
            syncTransport()
            status.info("$name: отключено — пауза")
        }
    }

    /** a new external output appeared: move the sound to it right away if playing. */
    fun onOutputAdded(name: String) = launch {
        needsReconnect = true
        if (state.value.queue == null || isPaused()) return@launch
        delay(ROUTE_SETTLE_MS) // let the system finish switching the route
        if (!reconnect(preferred = name, resume = true)) {
            // the core can't reopen a stream: keep going and let the stall check judge.
            needsReconnect = false
        }
    }

    /** play after a route change: on a fresh stream if the core can open one. */
    private fun resumeOnFreshStream() {
        if (!reconnect(preferred = null, resume = true)) {
            needsReconnect = false
            Native.enginePlay()
        }
    }

    /**
     * opens a new output stream and restores the current track, position and
     * pause. Tries the system default device first (routes wherever Android
     * routes), then [preferred] if its name is unambiguous — cpal names
     * built-in devices after the phone model, so several share one name and the
     * core switches by name. false — no stream could be opened.
     */
    private fun reconnect(preferred: String?, resume: Boolean): Boolean {
        val pos = position()
        val names = Native.engineGetOutputDevices().or(emptyList())
        val candidates = buildList {
            add(DEFAULT_DEVICE)
            if (preferred != null && names.count { it == preferred } == 1) add(preferred)
        }
        val opened = candidates.firstOrNull { Native.engineSetOutputDevice(it) == EngineStatus.OK } ?: return false
        needsReconnect = false
        if (state.value.queue != null) {
            playCurrent()
            if (pos > 0) Native.engineSetSeek(pos)
            if (!resume) Native.enginePause()
            syncTransport()
        }
        if (opened != DEFAULT_DEVICE) status.info("устройство вывода: $opened")
        return true
    }

    /**
     * the safety net: "playing" but the position stands still — the stream is
     * dead. One recovery attempt; if it can't help, pause and say so instead
     * of pretending to play.
     */
    private fun checkStall() {
        val s = state.value
        val playing = s.queue != null && !s.isPaused
        stalledPolls = if (playing && s.positionS == lastPos) stalledPolls + 1 else 0
        lastPos = s.positionS
        if (stalledPolls < STALL_POLLS) return
        stalledPolls = 0
        val now = System.currentTimeMillis()
        val recovered = now - lastRecoveryAt > RECOVERY_INTERVAL_MS && reconnect(preferred = null, resume = true)
        lastRecoveryAt = now
        if (!recovered) {
            Native.enginePause()
            needsReconnect = true
            syncTransport()
            status.error("звук пропал: сменилось устройство вывода. Выберите устройство в настройках или перезапустите приложение")
        }
    }

    // ───────────────────────── internals ─────────────────────────

    /** runs [block] on the core thread (commands are serialized). */
    private fun launch(block: suspend Native.() -> Unit) = scope.launch(rt.dispatcher) { Native.block() }

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
        val code = when {
            track.id != null -> Native.engineAddTrackId(track.id!!)
            track.path != null -> Native.engineAddTrackPath(track.path!!)
            else -> null // neither id nor path: nothing to play
        }
        if (code != EngineStatus.OK) {
            val why = code?.let { " (${describeEngine(it)})" } ?: ""
            status.error("не удалось воспроизвести: ${track.title}$why")
        }
        state.update { it.copy(positionS = 0) }
        syncTransport()
    }

    /** a failed engine read keeps the previous value. */
    private fun syncTransport() {
        state.update {
            it.copy(
                isPaused = isPaused() || Native.engineIsEmpty().or(true),
                positionS = Native.engineGetSeek().or(it.positionS),
                durationS = Native.engineGetDuration().or(it.durationS),
                trackVolume = Native.engineGetVolumeTrack().or(it.trackVolume),
            )
        }
    }

    /** a failed read counts as paused: nothing is known to be playing. */
    private fun isPaused(): Boolean = Native.engineIsPause().or(true)

    private fun position(): Long = Native.engineGetSeek().or(0L)

    /** the position for the progress bar; cheap, but only while there is a queue. */
    private suspend fun pollLoop() {
        while (scope.isActive) {
            delay(POLL_MS)
            if (state.value.queue != null) {
                syncTransport()
                checkStall()
            }
        }
    }

    /**
     * rebuilds the queue tracks from the updated library (as `refresh_playing`
     * in the TUI): when the playlist's set of tracks changed in the db, the
     * queue is taken from it again; otherwise the queue keeps its own
     * (temporary) order and only refreshes the track data.
     */
    private fun refreshQueue(lib: Library) {
        val q = state.value.queue ?: return
        val src = lib.find(q.name)
        val byId = lib.pool.tracks.filter { it.id != null }.associateBy { it.id }
        val ids = { l: List<Track> -> l.mapNotNull(Track::id).toSet() }
        val tracks = when {
            src != null && ids(src.tracks) != ids(q.tracks) -> src.tracks
            else -> q.tracks.map { t -> t.id?.let(byId::get) ?: t }
        }
        val cur = q.currentTrack?.key
        val current = tracks.indexOfFirst { it.id != null && it.id == cur?.id }.takeIf { it >= 0 }
            ?: q.current.coerceAtMost((tracks.size - 1).coerceAtLeast(0))
        state.update { it.copy(queue = q.copy(tracks = tracks, current = current)) }
    }

    private companion object {
        const val POLL_MS = 500L

        /** cpal's name of the system default output device. */
        const val DEFAULT_DEVICE = "Default Device"

        /** ~3 s of a frozen position while playing = a dead stream. */
        const val STALL_POLLS = 6
        const val RECOVERY_INTERVAL_MS = 10_000L
        const val ROUTE_SETTLE_MS = 700L
    }
}
