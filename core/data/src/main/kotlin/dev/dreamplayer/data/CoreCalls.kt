package dev.dreamplayer.data

import android.content.Context
import dev.dreamplayer.core.CdcStatus
import dev.dreamplayer.core.ConDevice
import dev.dreamplayer.core.ConStatus
import dev.dreamplayer.core.CoreResult
import dev.dreamplayer.core.DecodeResult
import dev.dreamplayer.core.EngineStatus
import dev.dreamplayer.core.IdxStatus
import dev.dreamplayer.core.ImportPrepared
import dev.dreamplayer.core.InitStatus
import dev.dreamplayer.core.Native
import dev.dreamplayer.core.PairedDevice
import dev.dreamplayer.core.PlaylistInfo
import dev.dreamplayer.core.SyncEvent
import dev.dreamplayer.core.SyncFile
import dev.dreamplayer.core.SyncStatus
import dev.dreamplayer.core.TrackEndListener
import dev.dreamplayer.core.TrackMeta
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.StatusKind

/**
 * [Native] with every call written to the [CoreLog]: arguments, status and
 * time. The same names and signatures, so code reads as if it called the core
 * directly. Getters the player polls twice a second are logged only when they
 * fail, and slider calls (volume, seek) in a row merge into one line —
 * otherwise they would bury everything else.
 */
internal class CoreCalls(private val log: CoreLog) {
    /** core panics already reported (see [newPanic]). */
    @Volatile
    private var panicsSeen = 0L

    // ── init / diagnostics ──

    fun init(
        context: Context,
        cacheDir: String,
        configDir: String,
        dataDir: String,
        musicDir: String,
        needDb: Boolean,
    ): Int = code(Sys.Init, "init(${q(dataDir)}, music=${q(musicDir)}, db=$needDb)") {
        Native.init(context, cacheDir, configDir, dataDir, musicDir, needDb)
    }

    fun setTrackEndListener(listener: TrackEndListener?) {
        Native.setTrackEndListener(listener)
        log.add(LogSource.Call, StatusKind.Info, "setTrackEndListener(${if (listener == null) "null" else "set"})")
    }

    /**
     * the last panic if the core panicked since the previous check without it
     * being returned as a `PANIC` status (an audio thread, say); null — none.
     */
    fun newPanic(): String? {
        val n = Native.corePanicCount()
        if (n <= panicsSeen) return null
        panicsSeen = n
        return Native.coreLastPanic() ?: "нет данных"
    }

    // ── engine ──

    fun engineAddTrackId(id: Long) = code(Sys.Engine, "engineAddTrackId($id)") { Native.engineAddTrackId(id) }
    fun engineAddTrackPath(path: String) = code(Sys.Engine, "engineAddTrackPath(${q(path)})") { Native.engineAddTrackPath(path) }
    fun enginePlayPause() = code(Sys.Engine, "enginePlayPause()") { Native.enginePlayPause() }
    fun enginePlay() = code(Sys.Engine, "enginePlay()") { Native.enginePlay() }
    fun enginePause() = code(Sys.Engine, "enginePause()") { Native.enginePause() }
    fun engineStop() = code(Sys.Engine, "engineStop()") { Native.engineStop() }
    fun engineSetSpeed(speed: Float) = code(Sys.Engine, "engineSetSpeed($speed)") { Native.engineSetSpeed(speed) }
    fun engineSetSeek(sec: Long) = code(Sys.Engine, "engineSetSeek($sec)", merge = true) { Native.engineSetSeek(sec) }
    fun engineGetSeek() = result(Sys.Engine, "engineGetSeek()", quiet = true) { Native.engineGetSeek() }
    fun engineGetDuration() = result(Sys.Engine, "engineGetDuration()", quiet = true) { Native.engineGetDuration() }
    fun engineIsPause() = result(Sys.Engine, "engineIsPause()", quiet = true) { Native.engineIsPause() }
    fun engineIsEmpty() = result(Sys.Engine, "engineIsEmpty()", quiet = true) { Native.engineIsEmpty() }
    fun engineSetVolumeTrack(volume: Float) =
        code(Sys.Engine, "engineSetVolumeTrack($volume)", merge = true) { Native.engineSetVolumeTrack(volume) }
    fun engineSetVolumeMaster(volume: Float) =
        code(Sys.Engine, "engineSetVolumeMaster($volume)", merge = true) { Native.engineSetVolumeMaster(volume) }
    fun engineGetVolumeTrack() = result(Sys.Engine, "engineGetVolumeTrack()", quiet = true) { Native.engineGetVolumeTrack() }
    fun engineGetVolumeMaster() = result(Sys.Engine, "engineGetVolumeMaster()") { Native.engineGetVolumeMaster() }
    fun engineGetOutputDevices() = result(Sys.Engine, "engineGetOutputDevices()", show = { l -> l.joinToString { q(it) } }) {
        Native.engineGetOutputDevices()
    }
    fun engineGetOutputDevice() = result(Sys.Engine, "engineGetOutputDevice()") { Native.engineGetOutputDevice() }
    fun engineSetOutputDevice(name: String) =
        code(Sys.Engine, "engineSetOutputDevice(${q(name)})") { Native.engineSetOutputDevice(name) }

    // ── library ──

    fun idxGetPlaylists(): CoreResult<List<PlaylistInfo>> = result(Sys.Idx, "idxGetPlaylists()") { Native.idxGetPlaylists() }
    fun idxGetTracks(): CoreResult<List<TrackMeta>> = result(Sys.Idx, "idxGetTracks()") { Native.idxGetTracks() }
    fun idxGetOrphanTracks(filter: Int): CoreResult<List<TrackMeta>> =
        result(Sys.Idx, "idxGetOrphanTracks($filter)") { Native.idxGetOrphanTracks(filter) }
    fun idxSetTrackIgnoreOrphan(id: Long, ignore: Boolean) =
        code(Sys.Idx, "idxSetTrackIgnoreOrphan($id, $ignore)") { Native.idxSetTrackIgnoreOrphan(id, ignore) }
    fun idxGetTrack(id: Long, path: String?): CoreResult<TrackMeta> =
        result(Sys.Idx, "idxGetTrack($id, ${q(path)})", show = { q(it.title) }) { Native.idxGetTrack(id, path) }
    fun idxIndexDir(dir: String?) = code(Sys.Idx, "idxIndexDir(${q(dir)})") { Native.idxIndexDir(dir) }
    fun idxSetTrackMeta(id: Long, title: String?, artists: Array<String>?) =
        code(Sys.Idx, "idxSetTrackMeta($id, ${q(title)}, ${arr(artists)})") { Native.idxSetTrackMeta(id, title, artists) }
    fun idxSetTrackGenres(id: Long, genres: Array<String>?) =
        code(Sys.Idx, "idxSetTrackGenres($id, ${arr(genres)})") { Native.idxSetTrackGenres(id, genres) }
    fun idxSetTrackAlbum(id: Long, album: String?) =
        code(Sys.Idx, "idxSetTrackAlbum($id, ${q(album)})") { Native.idxSetTrackAlbum(id, album) }
    fun idxSetTrackCover(id: Long, coverPath: String?) =
        code(Sys.Idx, "idxSetTrackCover($id, ${q(coverPath)})") { Native.idxSetTrackCover(id, coverPath) }
    fun idxSetTrackColor(id: Long, color: String?) =
        code(Sys.Idx, "idxSetTrackColor($id, ${q(color)})") { Native.idxSetTrackColor(id, color) }
    fun idxSetTrackLabel(id: Long, label: String?) =
        code(Sys.Idx, "idxSetTrackLabel($id, ${q(label)})") { Native.idxSetTrackLabel(id, label) }
    fun idxSetTrackPath(id: Long, path: String) =
        code(Sys.Idx, "idxSetTrackPath($id, ${q(path)})") { Native.idxSetTrackPath(id, path) }
    fun idxSetTrackInvalid(id: Long, invalid: Boolean) =
        code(Sys.Idx, "idxSetTrackInvalid($id, $invalid)") { Native.idxSetTrackInvalid(id, invalid) }
    fun idxSetTracksInvalid(ids: LongArray, invalid: Boolean) =
        code(Sys.Idx, "idxSetTracksInvalid([${ids.size}], $invalid)") { Native.idxSetTracksInvalid(ids, invalid) }
    fun idxAddTracks(paths: Array<String>) =
        result(Sys.Idx, "idxAddTracks([${paths.size}])") { Native.idxAddTracks(paths) }
    fun idxSetTrackVolume(id: Long, volume: Float) =
        code(Sys.Idx, "idxSetTrackVolume($id, $volume)") { Native.idxSetTrackVolume(id, volume) }
    fun idxRemoveTrack(id: Long) = code(Sys.Idx, "idxRemoveTrack($id)") { Native.idxRemoveTrack(id) }
    fun idxRemoveInvalidTracks() = result(Sys.Idx, "idxRemoveInvalidTracks()") { Native.idxRemoveInvalidTracks() }
    fun idxSavePlaylist(name: String, trackIds: LongArray, cover: String?) =
        code(Sys.Idx, "idxSavePlaylist(${q(name)}, [${trackIds.size}], ${q(cover)})") {
            Native.idxSavePlaylist(name, trackIds, cover)
        }
    fun idxRemovePlaylist(name: String) = code(Sys.Idx, "idxRemovePlaylist(${q(name)})") { Native.idxRemovePlaylist(name) }

    // ── packets ──

    fun cdcPackLibrary(version: Int, withAudio: Boolean, withNames: Boolean, withCovers: Boolean, fd: Int) =
        code(Sys.Cdc, "cdcPackLibrary(v$version, audio=$withAudio, names=$withNames, covers=$withCovers)") {
            Native.cdcPackLibrary(version, withAudio, withNames, withCovers, fd)
        }

    fun cdcPackTracks(version: Int, ids: LongArray, withAudio: Boolean, withNames: Boolean, fd: Int) =
        code(Sys.Cdc, "cdcPackTracks(v$version, [${ids.size}], audio=$withAudio, names=$withNames)") {
            Native.cdcPackTracks(version, ids, withAudio, withNames, fd)
        }

    fun cdcPackPlaylists(
        version: Int,
        names: Array<String>,
        withAudio: Boolean,
        withNames: Boolean,
        withCovers: Boolean,
        fd: Int,
    ) = code(Sys.Cdc, "cdcPackPlaylists(v$version, ${arr(names)}, audio=$withAudio, names=$withNames, covers=$withCovers)") {
        Native.cdcPackPlaylists(version, names, withAudio, withNames, withCovers, fd)
    }

    fun cdcDecode(data: ByteArray, withAudio: Boolean, withNames: Boolean, withCovers: Boolean): DecodeResult {
        val start = System.nanoTime()
        val res = Native.cdcDecode(data, withAudio, withNames, withCovers)
        val summary = res.packet?.let { "песен ${it.songs.size}, плейлистов ${it.playlists.size}" }
        report(Sys.Cdc, "cdcDecode(${data.size} B, audio=$withAudio)", res.status, summary, start)
        return res
    }

    fun cdcImportPrepare(fd: Int, force: Boolean): ImportPrepared {
        val start = System.nanoTime()
        val res = Native.cdcImportPrepare(fd, force)
        report(Sys.Cdc, "cdcImportPrepare(force=$force)", res.status, "сессия ${res.session}, файлов ${res.files.size}", start)
        return res
    }

    fun cdcImportWriteFile(session: Long, index: Int, fd: Int) =
        code(Sys.Cdc, "cdcImportWriteFile($session, #$index)") { Native.cdcImportWriteFile(session, index, fd) }

    fun cdcImportCommit(session: Long, paths: Array<String?>) =
        code(Sys.Cdc, "cdcImportCommit($session, [${paths.count { it != null }}/${paths.size}])") {
            Native.cdcImportCommit(session, paths)
        }

    fun cdcImportCancel(session: Long) {
        Native.cdcImportCancel(session)
        log.add(LogSource.Call, StatusKind.Info, "cdcImportCancel($session)")
    }

    // ── devices ──

    fun conGetPubKey() = result(Sys.Con, "conGetPubKey()", show = { "${it.size} B" }) { Native.conGetPubKey() }
    fun conAdd() = result(Sys.Con, "conAdd()") { Native.conAdd() }
    fun conConnect(id: Long) = code(Sys.Con, "conConnect($id)") { Native.conConnect(id) }
    fun conDisconnect(id: Long) = code(Sys.Con, "conDisconnect($id)") { Native.conDisconnect(id) }
    fun conRemove(id: Long) = code(Sys.Con, "conRemove($id)") { Native.conRemove(id) }
    /** polled while a transfer runs. */
    fun conGetDevices(): CoreResult<List<ConDevice>> = result(Sys.Con, "conGetDevices()", quiet = true) { Native.conGetDevices() }
    fun conGetPaired(): CoreResult<List<PairedDevice>> = result(Sys.Con, "conGetPaired()") { Native.conGetPaired() }
    fun conRename(pairedId: Long, name: String) = code(Sys.Con, "conRename($pairedId, ${q(name)})") { Native.conRename(pairedId, name) }
    fun conForget(pairedId: Long) = code(Sys.Con, "conForget($pairedId)") { Native.conForget(pairedId) }

    // ── sync ──

    fun syncNew(con: Long, host: Boolean) = result(Sys.Sync, "syncNew($con, host=$host)") { Native.syncNew(con, host) }

    fun syncNewSend(
        con: Long,
        host: Boolean,
        what: Int,
        names: Array<String>?,
        ids: LongArray?,
        withAudio: Boolean,
        withNames: Boolean,
        withCovers: Boolean,
    ) = result(
        Sys.Sync,
        "syncNewSend($con, host=$host, what=$what, [${names?.size ?: ids?.size ?: 0}], audio=$withAudio, names=$withNames, covers=$withCovers)",
    ) { Native.syncNewSend(con, host, what, names, ids, withAudio, withNames, withCovers) }

    fun syncNewReceive(con: Long, host: Boolean, force: Boolean) =
        result(Sys.Sync, "syncNewReceive($con, host=$host, force=$force)") { Native.syncNewReceive(con, host, force) }

    fun syncFree(session: Long) {
        Native.syncFree(session)
        log.add(LogSource.Call, StatusKind.Info, "syncFree($session)")
    }

    // the transfer loop: logged only when they fail.
    fun syncFeed(session: Long, data: ByteArray, len: Int) =
        code(Sys.Sync, "syncFeed($session, $len B)", quiet = true) { Native.syncFeed(session, data, len) }
    fun syncWriteOutput(session: Long, fd: Int) =
        code(Sys.Sync, "syncWriteOutput($session)", quiet = true) { Native.syncWriteOutput(session, fd) }
    fun syncPollEvent(session: Long): SyncEvent? = Native.syncPollEvent(session)
    fun syncFinished(session: Long): Boolean = Native.syncFinished(session)

    fun syncImportFiles(session: Long): CoreResult<List<SyncFile>> =
        result(Sys.Sync, "syncImportFiles($session)") { Native.syncImportFiles(session) }
    fun syncImportWriteFile(session: Long, index: Int, fd: Int) =
        code(Sys.Sync, "syncImportWriteFile($session, #$index)") { Native.syncImportWriteFile(session, index, fd) }
    fun syncImportDone(session: Long, paths: Array<String?>) =
        code(Sys.Sync, "syncImportDone($session, [${paths.count { it != null }}/${paths.size}])") {
            Native.syncImportDone(session, paths)
        }
    fun syncCancel(session: Long) = code(Sys.Sync, "syncCancel($session)") { Native.syncCancel(session) }

    // ───────────────────────── internals ─────────────────────────

    /** a subsystem: its status texts, the codes that are warnings, its `PANIC`. */
    private enum class Sys(val describe: (Int) -> String, val panic: Int, val warnings: Set<Int>) {
        Init(::describeInit, InitStatus.PANIC, setOf(InitStatus.ALREADY_INITIALIZED)),
        Engine(::describeEngine, EngineStatus.PANIC, setOf(EngineStatus.CONFIG_WRITE_FAILED)),
        Idx(::describeIdx, IdxStatus.PANIC, emptySet()),
        Cdc(::describeCdc, CdcStatus.PANIC, setOf(CdcStatus.OUTDATED)),
        Con(::describeCon, ConStatus.PANIC, emptySet()),
        Sync(::describeSync, SyncStatus.PANIC, emptySet()),
    }

    private inline fun code(sys: Sys, call: String, merge: Boolean = false, quiet: Boolean = false, block: () -> Int): Int {
        val start = System.nanoTime()
        val status = block()
        if (!(quiet && status == 0)) report(sys, call, status, null, start, merge)
        return status
    }

    private inline fun <T> result(
        sys: Sys,
        call: String,
        quiet: Boolean = false,
        noinline show: (T) -> String = ::summary,
        block: () -> CoreResult<T>,
    ): CoreResult<T> {
        val start = System.nanoTime()
        val res = block()
        if (!(quiet && res.isOk)) report(sys, call, res.status, res.value?.let(show), start)
        return res
    }

    private fun report(sys: Sys, call: String, status: Int, value: String?, startNs: Long, merge: Boolean = false) {
        val ms = (System.nanoTime() - startNs) / 1_000_000
        val kind = when (status) {
            0 -> StatusKind.Info
            in sys.warnings -> StatusKind.Warn
            else -> StatusKind.Error
        }
        val outcome = if (status == 0) "OK" + (value?.let { ": $it" } ?: "") else "${sys.describe(status)} [$status]"
        val key = if (merge) call.substringBefore('(') else null
        log.add(LogSource.Call, kind, "$call → $outcome · $ms мс", merge = key)
        // reported through the status: not a new panic for [newPanic].
        if (status == sys.panic) panicsSeen = Native.corePanicCount()
    }

    private fun summary(v: Any?): String = when (v) {
        null -> "null"
        is List<*> -> "${v.size} шт."
        is String -> q(v)
        else -> v.toString().take(MAX_VALUE)
    }

    private fun q(s: String?): String = if (s == null) "null" else "\"${s.take(MAX_VALUE)}\""

    private fun arr(a: Array<String>?): String = a?.joinToString(prefix = "[", postfix = "]") { q(it) } ?: "null"

    private companion object {
        const val MAX_VALUE = 80
    }
}
