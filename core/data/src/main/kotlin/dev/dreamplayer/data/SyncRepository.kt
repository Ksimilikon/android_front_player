package dev.dreamplayer.data

import android.content.Context
import dev.dreamplayer.core.ConDevice
import dev.dreamplayer.core.ConProcess
import dev.dreamplayer.core.ConState
import dev.dreamplayer.core.ImportFileKind
import dev.dreamplayer.core.SyncEvent
import dev.dreamplayer.covers.CoverLoader
import dev.dreamplayer.core.SyncEventKind
import dev.dreamplayer.core.SyncStatus
import dev.dreamplayer.core.SyncWhat
import dev.dreamplayer.core.PlaylistInfo
import dev.dreamplayer.core.TrackMeta
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** what a sync session does once connected. */
sealed interface SyncMode {
    /** two-way library sync (the other side syncs too). */
    data object Sync : SyncMode

    /** send a selection with the export flags (the other side receives). */
    data class Send(val what: ExportWhat, val flags: ExportFlags) : SyncMode

    /** accept a send; [force] — even playlists older than the local ones. */
    data class Receive(val force: Boolean) : SyncMode
}

/** a device this one has paired with. */
data class Peer(val id: Long, val name: String, val fingerprint: String)

/** the connection entry of the running session, as the core sees it. */
data class LinkView(
    /** `ConState`. */
    val state: Int,
    /** `PairFailure`. */
    val failure: Int,
    /** `ConProcess`. */
    val process: Int,
    /** the peer's name once paired. */
    val peer: String?,
) {
    /** "сопряжено с Pixel · приём". */
    fun describe(): String {
        val link = when (state) {
            ConState.UNCONNECTED -> "отключено"
            ConState.CONNECTED -> "подключено, сопряжение…"
            ConState.PAIRED -> "сопряжено" + (peer?.let { " с $it" } ?: "")
            ConState.PAIR_FAILED -> "сопряжение не удалось: ${describePairFailure(failure)}"
            else -> "состояние $state"
        }
        val doing = when (process) {
            ConProcess.RECEIVING -> " · приём"
            ConProcess.SENDING -> " · отправка"
            else -> ""
        }
        return link + doing
    }
}

/** data portions moved by the session. */
data class Portions(val songs: Int = 0, val playlists: Int = 0) {
    operator fun plus(e: SyncEvent) = Portions(songs + e.songs, playlists + e.playlists)
}

/** what an entry of [Transferred] is. */
enum class MovedKind { Song, Playlist, DeletedPlaylist, Portion }

/**
 * one entry of what moved: a song / playlist by name, or (when the name is
 * unknown — the core reports only the size of what was sent) a whole portion.
 */
data class Moved(
    /** epoch ms. */
    val at: Long,
    val kind: MovedKind,
    /** a name, or "12 песен" for a [MovedKind.Portion]. */
    val title: String,
    /** a [MovedKind.Song] in the local library. */
    val trackId: Long? = null,
)

/** what the session moved, by line of the DEVICES tab. */
data class Transferred(
    val sentSongs: List<Moved> = emptyList(),
    val sentPlaylists: List<Moved> = emptyList(),
    val receivedSongs: List<Moved> = emptyList(),
    val receivedPlaylists: List<Moved> = emptyList(),
)

/** the state of the sync worker for the interface. */
data class SyncView(
    val running: Boolean = false,
    /** a short line: what is happening now. */
    val stage: String = "не запущено",
    /** the hotspot this device started (to show to the other one). */
    val hotspot: HotspotInfo? = null,
    /** where this device waits for a connection. */
    val addresses: List<String> = emptyList(),
    val port: Int? = null,
    val link: LinkView? = null,
    val sent: Portions = Portions(),
    val received: Portions = Portions(),
    val moved: Transferred = Transferred(),
)

/**
 * library sync / one-way transfer with another device. The core runs the
 * protocol (pairing, comparing libraries, encryption, import); here the
 * transport is brought up ([SyncTransport]) and the bytes carried between it
 * and the core session, received files written like an import. One session
 * at a time; it runs independently of the interface and posts its log to
 * [log], the core log and, at the end, the status banner.
 */
class SyncRepository internal constructor(
    context: Context,
    private val rt: CoreRuntime,
    private val library: LibraryRepository,
    private val exchange: ExchangeRepository,
    private val status: StatusBus,
    private val coreLog: CoreLog,
    private val scope: CoroutineScope,
) {
    private val native = rt.native
    private val transport = SyncTransport(context)

    private val viewState = MutableStateFlow(SyncView())
    val view: StateFlow<SyncView> = viewState.asStateFlow()

    private val logState = MutableStateFlow(listOf(StatusMessage("выберите режим и способ подключения", StatusKind.Info)))
    val log: StateFlow<List<StatusMessage>> = logState.asStateFlow()

    /**
     * a mode another tab asks the sync tab to start with (EXPORT: send the
     * marked items, IMPORT: receive); the sync tab takes it once.
     */
    val preset = MutableStateFlow<SyncMode?>(null)

    private var job: Job? = null

    /** the mode of the running session (names what is sent where it can). */
    private var sending: SyncMode = SyncMode.Sync

    /** the library as it was before the last received portion. */
    private var before: Snapshot? = null

    /** songs written by the last import (their paths). */
    private var written: List<String> = emptyList()

    /** fingerprint of this device's key (the other user compares it), null — unavailable. */
    suspend fun ownFingerprint(): String? = rt.call { conGetPubKey() }.let { r -> r.value?.takeIf { r.isOk }?.let(::fingerprint) }

    suspend fun paired(): List<Peer> = rt.call { conGetPaired() }.or(emptyList()).map { Peer(it.id, it.name, fingerprint(it.pubKey)) }

    suspend fun rename(peer: Peer, name: String): Boolean {
        val code = rt.call { conRename(peer.id, name) }
        if (code == 0) status.ok("устройство переименовано: $name") else status.error("не удалось переименовать: ${describeCon(code)}")
        return code == 0
    }

    suspend fun forget(peer: Peer): Boolean {
        val code = rt.call { conForget(peer.id) }
        if (code == 0) status.ok("устройство забыто: ${peer.name}") else status.error("не удалось забыть устройство: ${describeCon(code)}")
        return code == 0
    }

    /**
     * starts a session: brings [target] up and runs [mode]; received songs go
     * to `Music/[subdir]`. false — one is already running.
     */
    fun start(target: SyncTarget, mode: SyncMode, subdir: String): Boolean {
        if (job?.isActive == true) return false
        logState.value = emptyList()
        viewState.value = SyncView(running = true, stage = "подключение")
        sending = mode
        job = scope.launch(Dispatchers.IO) {
            val complete = try {
                runSession(target, mode, subdir)
            } catch (e: CancellationException) {
                say(StatusKind.Warn, "отменено")
                false
            } catch (e: IOException) {
                say(StatusKind.Error, e.message ?: "ошибка соединения")
                false
            } catch (e: Exception) {
                say(StatusKind.Error, "сбой: ${e.message ?: e.javaClass.simpleName}")
                false
            }
            viewState.update { it.copy(running = false, stage = if (complete) "готово" else "остановлено", hotspot = null, addresses = emptyList()) }
            if (complete) status.ok("${modeLabel(mode)}: готово") else status.warn("${modeLabel(mode)}: не завершено, подробности во вкладке «Устройства»")
        }
        return true
    }

    /** cancels the running session (the peer is told). */
    fun cancel() {
        job?.cancel()
    }

    // ───────────────────────── the session ─────────────────────────

    private val ui = object : TransportUi {
        override fun info(text: String) = say(StatusKind.Info, text)
        override fun stage(text: String) = viewState.update { it.copy(stage = text) }
        override fun hotspot(info: HotspotInfo) {
            viewState.update { it.copy(hotspot = info) }
            say(StatusKind.Ok, "точка доступа: «${info.ssid}», пароль ${info.password}")
        }
        override fun listening(addresses: List<String>, port: Int) {
            viewState.update { it.copy(addresses = addresses, port = port, stage = "ожидание подключения") }
        }
    }

    /** true — the session completed. */
    private suspend fun runSession(target: SyncTarget, mode: SyncMode, subdir: String): Boolean {
        ownFingerprint()?.let { say(StatusKind.Info, "это устройство: $it") }
        transport.establish(target, ui).use { link ->
            viewState.update { it.copy(stage = "подключено", hotspot = it.hotspot, addresses = emptyList()) }
            val con = native.conAdd().takeIf { it.isOk }?.value
                ?: throw IOException("не удалось создать подключение в ядре")
            try {
                val connected = native.conConnect(con)
                if (connected != 0) throw IOException("ядро не приняло подключение: ${describeCon(connected)}")
                return drive(link, con, mode, subdir)
            } finally {
                native.conDisconnect(con)
                native.conRemove(con)
            }
        }
    }

    private fun open(con: Long, host: Boolean, mode: SyncMode) = when (mode) {
        SyncMode.Sync -> native.syncNew(con, host)
        is SyncMode.Receive -> native.syncNewReceive(con, host, mode.force)
        is SyncMode.Send -> {
            val f = mode.flags
            when (val w = mode.what) {
                ExportWhat.Library -> native.syncNewSend(con, host, SyncWhat.LIBRARY, null, null, f.audio, f.names, f.covers)
                is ExportWhat.Playlists ->
                    native.syncNewSend(con, host, SyncWhat.PLAYLISTS, w.names.toTypedArray(), null, f.audio, f.names, f.covers)
                is ExportWhat.Tracks ->
                    native.syncNewSend(con, host, SyncWhat.TRACKS, null, w.ids.toLongArray(), f.audio, f.names, f.covers)
            }
        }
    }

    /** carries bytes between the link and the core session until it is over. */
    private suspend fun drive(link: Link, con: Long, mode: SyncMode, subdir: String): Boolean {
        before = snapshot()
        val opened = open(con, link.host, mode)
        val session = opened.value?.takeIf { opened.isOk } ?: throw IOException("сессия не открылась: ${describeSync(opened.status)}")
        viewState.update { it.copy(stage = if (link.host) "обмен (это устройство — хост)" else "обмен") }
        val buf = ByteArray(READ_BUFFER)
        var changed = false
        var complete = false
        var lastLink = 0L
        try {
            while (true) {
                if (native.syncWriteOutput(session, link.outFd) == SyncStatus.IO_FAILED) throw IOException("соединение оборвалось")
                while (true) {
                    val ev = native.syncPollEvent(session) ?: break
                    // each round of a two-way sync ends with its own `DONE`.
                    if (ev.kind == SyncEventKind.DONE && complete) continue
                    when (ev.kind) {
                        SyncEventKind.IMPORT -> written = import(session, subdir)
                        SyncEventKind.RECEIVED -> changed = true
                        SyncEventKind.DONE -> complete = true
                    }
                    report(ev)
                }
                val now = System.currentTimeMillis()
                if (now - lastLink > LINK_POLL_MS) {
                    lastLink = now
                    refreshLink(con)
                }
                if (native.syncFinished(session)) break
                currentCoroutineContext().let { if (!it.isActive) throw CancellationException() }
                val n = link.read(buf)
                if (n < 0) throw IOException("соединение закрыто другой стороной")
                if (n > 0) native.syncFeed(session, buf, n)
            }
        } catch (e: Throwable) {
            // tell the peer why (the Abort goes out with the last write).
            withContext(NonCancellable) { native.syncCancel(session) }
            throw e
        } finally {
            withContext(NonCancellable) {
                native.syncWriteOutput(session, link.outFd)
                refreshLink(con)
                native.syncFree(session)
                if (changed) library.reload()
            }
        }
        return complete
    }

    /**
     * writes the files of a received portion: a song over the local file with
     * the same name if the app may write it, otherwise into `Music/[subdir]`;
     * covers (of playlists and, v0.12+, the songs' own cover files) into the
     * app's storage.
     */
    private suspend fun import(session: Long, subdir: String): List<String> {
        val files = native.syncImportFiles(session).or(emptyList())
        val paths = files.mapIndexed { i, f ->
            val fill = ExchangeRepository.Fill { fd -> native.syncImportWriteFile(session, i, fd) == SyncStatus.OK }
            val replace = f.replace
            when {
                f.kind != ImportFileKind.SONG -> exchange.saveCover(f.name, fill).getOrNull()?.path
                replace != null && exchange.overwrite(replace, fill) -> replace
                else -> {
                    if (replace != null) say(StatusKind.Warn, "нельзя заменить $replace — песня сохранена рядом")
                    exchange.saveSong(f.name, subdir, fill).getOrNull()?.path
                }
            }.also { if (it == null) say(StatusKind.Warn, "не удалось записать ${f.name}") }
        }
        val code = native.syncImportDone(session, paths.toTypedArray())
        if (code != SyncStatus.OK) say(StatusKind.Error, "импорт: ${describeSync(code)}")
        // a song written over its old file may carry another cover: forget what was read from these files.
        CoverLoader.invalidate(paths.filterNotNull())
        return files.indices.mapNotNull { i -> paths[i]?.takeIf { files[i].kind == ImportFileKind.SONG } }
    }

    private suspend fun report(ev: SyncEvent) {
        fun portion(verb: String) = buildList {
            if (ev.songs > 0) add("песен ${ev.songs}")
            if (ev.playlists > 0) add("плейлистов ${ev.playlists}")
        }.joinToString(", ").ifEmpty { "пустая порция" }.let { "$verb: $it" }
        when (ev.kind) {
            SyncEventKind.PAIRED -> {
                val peer = paired().firstOrNull { it.id == ev.pairedId }
                say(StatusKind.Ok, "сопряжено с #${ev.pairedId} ${peer?.let { "${it.name} [${it.fingerprint}]" } ?: ""}".trim())
                viewState.update { it.copy(stage = "сопряжено, сравнение библиотек") }
            }
            SyncEventKind.SENT -> {
                val (songs, playlists) = sentItems(ev)
                viewState.update {
                    it.copy(
                        sent = it.sent + ev,
                        stage = "передача",
                        moved = it.moved.copy(sentSongs = it.moved.sentSongs + songs, sentPlaylists = it.moved.sentPlaylists + playlists),
                    )
                }
                say(StatusKind.Info, portion("отправлено"))
            }
            SyncEventKind.RECEIVED -> {
                val (songs, playlists) = receivedItems(ev)
                viewState.update {
                    it.copy(
                        received = it.received + ev,
                        stage = "передача",
                        moved = it.moved.copy(
                            receivedSongs = it.moved.receivedSongs + songs,
                            receivedPlaylists = it.moved.receivedPlaylists + playlists,
                        ),
                    )
                }
                say(StatusKind.Info, portion("получено"))
            }
            SyncEventKind.PEER_REJECTED -> say(StatusKind.Warn, "другая сторона не приняла часть данных: ${describeWire(ev.status)}")
            SyncEventKind.DONE -> say(StatusKind.Ok, "готово")
            SyncEventKind.FAILED ->
                say(StatusKind.Error, "ошибка (${if (ev.byPeer) "на другом устройстве" else "здесь"}): ${describeWire(ev.status)}")
        }
    }

    // ───────────────────────── what moved ─────────────────────────

    /** the library at a moment: tracks by id and playlists by name. */
    private class Snapshot(val tracks: Map<Long, TrackMeta>, val playlists: Map<String, PlaylistInfo>)

    private fun snapshot(): Snapshot? {
        val tracks = native.idxGetTracks()
        val playlists = native.idxGetPlaylists()
        if (!tracks.isOk || !playlists.isOk) return null
        return Snapshot(
            tracks.or(emptyList()).mapNotNull { t -> t.id?.let { it to t } }.toMap(),
            playlists.or(emptyList()).mapNotNull { p -> p.name?.let { it to p } }.toMap(),
        )
    }

    /**
     * the core tells only how much was sent: names only where this side
     * knows them — the playlists of a send (the playlists portion is all of
     * them), otherwise the portion as a whole.
     */
    private fun sentItems(ev: SyncEvent): Pair<List<Moved>, List<Moved>> {
        val now = System.currentTimeMillis()
        val songs = if (ev.songs > 0) listOf(Moved(now, MovedKind.Portion, songsLabel(ev.songs))) else emptyList()
        val chosen = ((sending as? SyncMode.Send)?.what as? ExportWhat.Playlists)?.names
        val playlists = when {
            ev.playlists == 0 -> emptyList()
            chosen != null && chosen.size == ev.playlists -> chosen.map { Moved(now, MovedKind.Playlist, it) }
            else -> listOf(Moved(now, MovedKind.Portion, playlistsLabel(ev.playlists)))
        }
        return songs to playlists
    }

    /**
     * what a received portion changed: the library is compared with the
     * one before it (the core tells only how much); the songs whose files
     * were written count even if their data didn't change.
     */
    private fun receivedItems(ev: SyncEvent): Pair<List<Moved>, List<Moved>> {
        val now = System.currentTimeMillis()
        val old = before
        val new = snapshot() ?: return fallback(ev, now)
        before = new
        if (old == null) return fallback(ev, now)
        val paths = written.toSet()
        written = emptyList()
        val songs = new.tracks.values
            .filter { t ->
                val was = old.tracks[t.id]
                was == null || was.copy(listenCount = 0) != t.copy(listenCount = 0) || t.path in paths
            }
            .map { Moved(now, MovedKind.Song, it.title.ifBlank { it.path?.substringAfterLast('/') ?: "без названия" }, it.id) }
        val changed = new.playlists.values
            .filter { p ->
                val was = old.playlists[p.name]
                was == null || was.updatedAt != p.updatedAt || was.coverArt != p.coverArt || !was.trackIds.contentEquals(p.trackIds)
            }
            .map { Moved(now, MovedKind.Playlist, it.name.orEmpty()) }
        val gone = (old.playlists.keys - new.playlists.keys).map { Moved(now, MovedKind.DeletedPlaylist, it) }
        val (fs, fp) = fallback(ev, now)
        return songs.ifEmpty { fs } to (changed + gone).ifEmpty { fp }
    }

    /** the portion as a whole when the library can't be compared. */
    private fun fallback(ev: SyncEvent, now: Long): Pair<List<Moved>, List<Moved>> =
        (if (ev.songs > 0) listOf(Moved(now, MovedKind.Portion, songsLabel(ev.songs))) else emptyList()) to
            (if (ev.playlists > 0) listOf(Moved(now, MovedKind.Portion, playlistsLabel(ev.playlists))) else emptyList())

    private fun songsLabel(n: Int) = "порция: песен $n"
    private fun playlistsLabel(n: Int) = "порция: плейлистов $n"

    private fun refreshLink(con: Long) {
        val d: ConDevice? = native.conGetDevices().or(emptyList()).firstOrNull { it.id == con }
        viewState.update { v ->
            v.copy(link = d?.let { LinkView(it.state, it.failure, it.process, it.name.takeIf { _ -> it.pairedId != null }) } ?: v.link)
        }
    }

    private fun say(kind: StatusKind, text: String) {
        coreLog.add(LogSource.Status, kind, "синхронизация: $text")
        logState.update { (it + StatusMessage(text, kind)).takeLast(LOG_LIMIT) }
    }

    private fun modeLabel(mode: SyncMode) = when (mode) {
        SyncMode.Sync -> "синхронизация"
        is SyncMode.Send -> "отправка"
        is SyncMode.Receive -> "приём"
    }

    private companion object {
        const val READ_BUFFER = 256 * 1024
        const val LINK_POLL_MS = 500L
        const val LOG_LIMIT = 300
    }
}

/** short fingerprint of a public key for the user: `ab12 cd34 ef56 7890` (as in the TUI). */
fun fingerprint(key: ByteArray): String =
    key.take(8).chunked(2).joinToString(" ") { pair -> pair.joinToString("") { "%02x".format(it) } }
