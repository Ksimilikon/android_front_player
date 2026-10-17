package dev.dreamplayer.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dreamplayer.common.SortKey
import dev.dreamplayer.common.TrackMatcher
import dev.dreamplayer.common.moved
import dev.dreamplayer.model.Track

/**
 * editor state; owned by the tab host so it survives the tab leaving the
 * screen. [originalName] — null for a new playlist.
 */
class PlaylistEditorState(val originalName: String?, tracks: List<Track>) {
    var name by mutableStateOf(originalName.orEmpty())
    val assembled = mutableStateListOf<Track>().apply { addAll(tracks) }

    var pane by mutableStateOf(Pane.Playlist)
    var sortKey by mutableStateOf(SortKey.Title)
    var descending by mutableStateOf(false)
    /** hide songs already added (on by default, as in the TUI). */
    var hideAdded by mutableStateOf(true)
    var playlistQuery by mutableStateOf("")
    var poolQuery by mutableStateOf("")

    enum class Pane { Playlist, Pool }

    val ids: List<Long> get() = assembled.mapNotNull(Track::id)

    fun isAdded(t: Track): Boolean = t.id != null && assembled.any { it.id == t.id }

    fun toggle(t: Track) {
        val i = assembled.indexOfFirst { it.id == t.id }
        if (i >= 0) assembled.removeAt(i) else if (t.id != null) assembled.add(t)
    }

    fun move(from: Int, to: Int) {
        val next = assembled.toList().moved(from, to)
        assembled.clear()
        assembled.addAll(next)
    }

    /** indices of the assembled list matching the playlist search. */
    fun playlistView(): List<Int> {
        val m = TrackMatcher(playlistQuery)
        return assembled.indices.filter { m.matches(assembled[it]) }
    }

    /** the pool to pick from: search + "hide added" + sorting. */
    fun poolView(pool: List<Track>): List<Track> {
        val m = TrackMatcher(poolQuery)
        val sorted = pool.filter { m.matches(it) && !(hideAdded && isAdded(it)) }.sortedBy { sortKey.of(it) }
        return if (descending) sorted.reversed() else sorted
    }
}
