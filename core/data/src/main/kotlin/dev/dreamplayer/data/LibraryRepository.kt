package dev.dreamplayer.data

import dev.dreamplayer.core.IdxStatus
import dev.dreamplayer.core.OrphanFilter
import dev.dreamplayer.covers.CoverLoader
import dev.dreamplayer.model.Library
import dev.dreamplayer.model.MetaEdit
import dev.dreamplayer.model.Playlist
import dev.dreamplayer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/** the library index: the track pool and the playlists of the db. */
class LibraryRepository internal constructor(
    private val rt: CoreRuntime,
    private val status: StatusBus,
    private val musicDir: File,
    private val covers: CoverStore,
) {
    private val state = MutableStateFlow(Library.EMPTY)
    val library: StateFlow<Library> = state.asStateFlow()

    /**
     * re-reads the library: the pool + playlists, whose tracks are assembled
     * from the pool by id (one pool query, as in the TUI). On a core error the
     * previous library stays and the error is reported.
     */
    suspend fun reload() {
        val (lib, code) = rt.call<Pair<Library?, Int>> {
            val tracks = idxGetTracks()
            val infos = idxGetPlaylists()
            val failed = listOf(tracks.status, infos.status).firstOrNull { it != IdxStatus.OK }
            if (failed != null) return@call null to failed
            val pool = tracks.or(emptyList()).map { it.toTrack() }
            val byId = pool.filter { it.id != null }.associateBy { it.id!! }
            val playlists = infos.or(emptyList()).map { info ->
                Playlist(
                    name = info.name ?: "(без имени)",
                    tracks = info.trackIds.asList().mapNotNull(byId::get),
                    isPool = false,
                    coverArt = info.coverArt,
                    createdAt = info.createdAt,
                    updatedAt = info.updatedAt,
                )
            }
            Library(Playlist(Library.POOL_NAME, pool, isPool = true), playlists) to IdxStatus.OK
        }
        if (lib == null) {
            status.error("не удалось прочитать библиотеку: ${describeIdx(code)}")
            return
        }
        state.value = lib
        sweepCovers(lib)
    }

    /**
     * deletes cover files nothing references any more (a cover replaced in
     * the editor or by sync, a removed playlist / track). Only after a
     * successful read: a failed one would make every cover look unused.
     */
    private suspend fun sweepCovers(lib: Library) {
        covers.sweep((lib.pool.tracks.map { it.coverArt } + lib.playlists.map { it.coverArt }).filterNotNull())
    }

    /**
     * syncs the library with the song files under [dir] (null — the music
     * directory). The comparison is done here, the core only writes the
     * difference: the folder is walked, the library pool is read and compared
     * by path; new files go to the core in batches ([BATCH] per transaction,
     * so a tapped song gets through between them), tracks whose file is gone
     * are marked invalid, those whose file came back are unmarked. [full] —
     * re-read the tags of every file, not only the new ones (a manual
     * rescan: tags edited outside the app aren't seen by a path comparison).
     * [announce] — report the result even when nothing changed.
     */
    suspend fun scan(dir: String? = null, announce: Boolean = true, full: Boolean = false): Boolean {
        val root = dir?.let(::File) ?: musicDir
        val where = dir ?: "папка музыки"
        if (!withContext(Dispatchers.IO) { root.isDirectory && root.canRead() }) {
            status.error("$where недоступна")
            return false
        }
        val files = withContext(Dispatchers.IO) { listSongs(root) }
        val pool = rt.call { idxGetTracks() }
        if (!pool.isOk) {
            status.error("не удалось прочитать библиотеку: ${describeIdx(pool.status)}")
            return false
        }
        val prefix = root.path.trimEnd('/') + "/"
        val known = pool.or(emptyList()).filter { it.path?.startsWith(prefix) == true }
        val knownPaths = known.mapNotNull { it.path }.toSet()
        val toAdd = (if (full) files else files - knownPaths).sorted()
        // no file visible at all while the library has some: the folder is
        // more likely unreadable (media permission revoked) than emptied —
        // don't mark the whole library invalid.
        val gone = if (files.isEmpty()) emptyList() else known.filter { !it.invalid && it.path !in files }.mapNotNull { it.id }
        val back = known.filter { it.invalid && it.path in files }.mapNotNull { it.id }

        var added = 0L
        var failed: Int? = null
        for (batch in toAdd.chunked(BATCH)) {
            val res = rt.call { idxAddTracks(batch.toTypedArray()) }
            if (!res.isOk) {
                failed = res.status
                break
            }
            added += res.or(0L)
        }
        for ((ids, invalid) in listOf(gone to true, back to false)) {
            if (failed != null || ids.isEmpty()) continue
            val code = rt.call { idxSetTracksInvalid(ids.toLongArray(), invalid) }
            if (code != IdxStatus.OK) failed = code
        }
        val changed = added > 0 || gone.isNotEmpty() || back.isNotEmpty()
        if (changed) reload()

        val summary = buildList {
            if (added > 0) add(if (full) "прочитано песен: $added" else "новых песен: $added")
            if (gone.isNotEmpty()) add("файлов нет: ${gone.size}")
            if (back.isNotEmpty()) add("вернулось: ${back.size}")
        }.joinToString(", ")
        when {
            failed != null -> status.error("не удалось проиндексировать $where: ${describeIdx(failed)}")
            changed -> status.ok("библиотека: $summary")
            announce -> status.ok("библиотека актуальна")
        }
        return failed == null
    }

    /** song files under [root] (hidden directories skipped), by absolute path. */
    private fun listSongs(root: File): Set<String> =
        root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith('.') }
            .filter { it.isFile && it.extension.lowercase() in SONG_EXTENSIONS }
            .map { it.path }
            .toSet()

    /**
     * creates / updates playlist [name] from [ids] (in this order) with
     * [cover] — by default the one it has (under [previousName] when it is
     * being renamed). When the playlist was renamed, the old one is removed.
     */
    suspend fun savePlaylist(
        name: String,
        ids: List<Long>,
        previousName: String? = null,
        cover: String? = state.value.find(previousName ?: name)?.coverArt,
    ): Boolean {
        val renamed = previousName != null && previousName != name
        val code = rt.call {
            val saved = idxSavePlaylist(name, ids.toLongArray(), cover)
            if (saved == IdxStatus.OK && renamed) idxRemovePlaylist(previousName!!) else saved
        }
        val ok = code == IdxStatus.OK
        reload()
        if (ok) status.ok("плейлист сохранён: $name") else status.error("не удалось сохранить плейлист: ${describeIdx(code)}")
        return ok
    }

    /** removes the first occurrence of track [id] from playlist [name] (the rest keeps its order). */
    suspend fun removeFromPlaylist(name: String, id: Long): Boolean {
        val pl = state.value.find(name)?.takeIf { !it.isPool } ?: return false
        val ids = pl.tracks.mapNotNull(Track::id).toMutableList()
        if (!ids.remove(id)) return false
        return savePlaylist(name, ids)
    }

    /** the cover of playlist [name] alone (null — clear); its tracks stay as they are. */
    suspend fun setPlaylistCover(name: String, cover: String?): Boolean {
        val code = rt.call { idxSetPlaylistCover(name, cover) }
        val ok = code == IdxStatus.OK
        reload()
        if (ok) status.ok(if (cover == null) "обложка убрана" else "обложка плейлиста изменена")
        else status.error("не удалось изменить обложку: ${describeIdx(code)}")
        return ok
    }

    suspend fun removePlaylist(name: String): Boolean {
        val code = rt.call { idxRemovePlaylist(name) }
        val ok = code == IdxStatus.OK
        reload()
        if (ok) status.ok("плейлист удалён: $name") else status.error("не удалось удалить плейлист: ${describeIdx(code)}")
        return ok
    }

    /** applies metadata edits (as `apply_meta` in the TUI) and re-reads the library. */
    suspend fun applyMeta(e: MetaEdit): Boolean {
        // every field is tried; the first failure is the one reported.
        val code = rt.call {
            val codes = buildList {
                if (e.title != null || e.artists != null) {
                    add(idxSetTrackMeta(e.id, e.title, e.artists?.toTypedArray()))
                }
                e.album?.let { add(idxSetTrackAlbum(e.id, it.ifEmpty { null })) }
                e.genres?.let { add(idxSetTrackGenres(e.id, it.toTypedArray())) }
                e.coverPath?.let { add(idxSetTrackCover(e.id, it)) }
                if (e.setColor) add(idxSetTrackColor(e.id, e.color?.id))
                e.label?.let { add(idxSetTrackLabel(e.id, it.ifEmpty { null })) }
            }
            codes.firstOrNull { it != IdxStatus.OK } ?: IdxStatus.OK
        }
        val ok = code == IdxStatus.OK
        if (e.coverPath != null) CoverLoader.invalidate()
        reload()
        if (ok) status.ok("метаданные сохранены") else status.error("часть метаданных не сохранилась: ${describeIdx(code)}")
        return ok
    }

    /**
     * songs that are in no playlist (to clean up); [kept] — which of them:
     * not marked "keep" (default), only the marked ones, or all. null — the
     * core failed (reported).
     */
    suspend fun orphans(kept: OrphanView = OrphanView.Unmarked): List<Track>? {
        val filter = when (kept) {
            OrphanView.Unmarked -> OrphanFilter.NOT_IGNORED
            OrphanView.Kept -> OrphanFilter.IGNORED
            OrphanView.All -> OrphanFilter.ALL
        }
        val res = rt.call { idxGetOrphanTracks(filter) }
        if (!res.isOk) status.error("не удалось найти песни вне плейлистов: ${describeIdx(res.status)}")
        return res.takeIf { it.isOk }?.or(emptyList())?.map { it.toTrack() }
    }

    /**
     * marks a song "keep though in no playlist" (the collector stops listing
     * it) or clears the mark; the library copy is patched without a re-read.
     */
    suspend fun setKeepOrphan(track: Track, keep: Boolean): Boolean {
        val id = track.id ?: return false
        val code = rt.call { idxSetTrackIgnoreOrphan(id, keep) }
        if (code != IdxStatus.OK) {
            status.error("не удалось изменить пометку: ${describeIdx(code)}")
            return false
        }
        state.update { lib -> lib.mapTracks { if (it.id == id) it.copy(ignoreOrphan = keep) else it } }
        status.ok(if (keep) "«${track.title}» больше не показывается в сборщике" else "«${track.title}» снова показывается в сборщике")
        return true
    }

    /** saves the per-track volume; the library copy is patched without a re-read. */
    suspend fun setTrackVolume(id: Long, volume: Float): Boolean {
        val ok = rt.call { idxSetTrackVolume(id, volume) } == IdxStatus.OK
        if (ok) state.update { lib -> lib.mapTracks { if (it.id == id) it.copy(volume = volume) else it } }
        return ok
    }

    /** removes every invalid track; the number removed (-1 — error). */
    suspend fun removeInvalid(): Long {
        val res = rt.call { idxRemoveInvalidTracks() }
        val n = if (res.isOk) res.or(0L) else -1L
        reload()
        when {
            n < 0 -> status.error("не удалось удалить невалидные треки: ${describeIdx(res.status)}")
            n == 0L -> status.info("невалидных треков нет")
            else -> status.ok("удалено невалидных треков: $n")
        }
        return n
    }

    private fun Library.mapTracks(f: (Track) -> Track) = Library(
        pool = pool.copy(tracks = pool.tracks.map(f)),
        playlists = playlists.map { p -> p.copy(tracks = p.tracks.map(f)) },
    )

    private companion object {
        /** new files per core transaction. */
        const val BATCH = 200

        /** what the core recognizes as music (`Track::is_music`); others aren't sent. */
        val SONG_EXTENSIONS = setOf("mp3", "wav", "flac", "ogg", "oga", "opus", "m4a", "aac", "mid", "midi")
    }
}

/** which songs in no playlist the collector shows. */
enum class OrphanView { Unmarked, Kept, All }
