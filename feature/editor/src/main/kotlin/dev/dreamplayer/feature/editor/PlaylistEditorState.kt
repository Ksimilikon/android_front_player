package dev.dreamplayer.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dreamplayer.common.SortKey
import dev.dreamplayer.common.TrackMatcher
import dev.dreamplayer.model.Track

/**
 * editor state; owned by the tab host so it survives the tab leaving the
 * screen. [originalName] — null for a new playlist; [originalCover] — its
 * cover in the library.
 */
class PlaylistEditorState(val originalName: String?, tracks: List<Track>, originalCover: String? = null) {
    /**
     * a track of the assembled list with a key of its own: the list may hold
     * one song twice (from the db / another device), the rows need unique keys.
     */
    class Entry(val key: Long, val track: Track)

    private var nextKey = 0L

    var name by mutableStateOf(originalName.orEmpty())
    var cover by mutableStateOf(originalCover)
    val assembled = mutableStateListOf<Entry>().apply { addAll(tracks.map(::entry)) }

    var pane by mutableStateOf(Pane.Playlist)
    var sortKey by mutableStateOf(SortKey.Title)
    var descending by mutableStateOf(false)
    /** hide songs already added (on by default, as in the TUI). */
    var hideAdded by mutableStateOf(true)
    var searching by mutableStateOf(false)
    var playlistQuery by mutableStateOf("")
    var poolQuery by mutableStateOf("")
    /** the top sheet with the name and the cover; open when the editor opens. */
    var sheetOpen by mutableStateOf(true)

    enum class Pane { Playlist, Pool }

    val ids: List<Long> get() = assembled.mapNotNull { it.track.id }

    fun isAdded(t: Track): Boolean = t.id != null && assembled.any { it.track.id == t.id }

    fun toggle(t: Track) {
        val i = assembled.indexOfFirst { it.track.id == t.id }
        if (i >= 0) assembled.removeAt(i) else if (t.id != null) assembled.add(entry(t))
    }

    fun remove(key: Long) {
        assembled.removeAll { it.key == key }
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in assembled.indices || to !in assembled.indices) return
        assembled.add(to, assembled.removeAt(from))
    }

    /**
     * the entries matching the playlist search with their positions — a
     * snapshot: the list may change before the rows are laid out.
     */
    fun playlistView(): List<IndexedValue<Entry>> {
        val m = TrackMatcher(playlistQuery)
        return assembled.withIndex().filter { m.matches(it.value.track) }
    }

    /** the pool to pick from: search + "hide added" + sorting. */
    fun poolView(pool: List<Track>): List<Track> {
        val m = TrackMatcher(poolQuery)
        val sorted = pool.filter { m.matches(it) && !(hideAdded && isAdded(it)) }.sortedBy { sortKey.of(it) }
        return if (descending) sorted.reversed() else sorted
    }

    private fun entry(t: Track) = Entry(nextKey++, t)
}
