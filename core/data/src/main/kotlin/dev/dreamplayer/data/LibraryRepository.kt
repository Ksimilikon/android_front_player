package dev.dreamplayer.data

import dev.dreamplayer.core.IdxStatus
import dev.dreamplayer.covers.CoverLoader
import dev.dreamplayer.model.Library
import dev.dreamplayer.model.MetaEdit
import dev.dreamplayer.model.Playlist
import dev.dreamplayer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** the library index: the track pool and the playlists of the db. */
class LibraryRepository internal constructor(
    private val rt: CoreRuntime,
    private val status: StatusBus,
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
        if (lib != null) state.value = lib else status.error("не удалось прочитать библиотеку: ${describeIdx(code)}")
    }

    /**
     * indexes [dir] (null — the music directory) and re-reads the library;
     * [announce] — report success too (the silent start-up scan doesn't).
     */
    suspend fun scan(dir: String? = null, announce: Boolean = true): Boolean {
        val code = rt.call { idxIndexDir(dir) }
        val ok = code == IdxStatus.OK
        reload()
        when {
            !ok -> status.error("не удалось проиндексировать ${dir ?: "папку музыки"}: ${describeIdx(code)}")
            announce -> status.ok("библиотека проиндексирована")
        }
        return ok
    }

    /**
     * creates / updates playlist [name] from [ids] (in this order). When the
     * playlist was renamed ([previousName] differs), the old one is removed
     * and its cover carried over.
     */
    suspend fun savePlaylist(name: String, ids: List<Long>, previousName: String? = null): Boolean {
        val renamed = previousName != null && previousName != name
        val cover = previousName?.let { state.value.find(it)?.coverArt }
        val code = rt.call {
            val saved = idxSavePlaylist(name, ids.toLongArray(), cover)
            if (saved == IdxStatus.OK && renamed) idxRemovePlaylist(previousName!!) else saved
        }
        val ok = code == IdxStatus.OK
        reload()
        if (ok) status.ok("плейлист сохранён: $name") else status.error("не удалось сохранить плейлист: ${describeIdx(code)}")
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
}
