package dev.dreamplayer.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dreamplayer.common.SortKey
import dev.dreamplayer.common.TrackDates
import dev.dreamplayer.common.TrackMatcher
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.nav.Editing

/**
 * editor state; owned by the tab host so it survives the tab leaving the
 * screen. [originalName] — null for a new playlist; [originalCover] — its
 * cover in the library.
 */
class PlaylistEditorState(
    val originalName: String?,
    tracks: List<Track>,
    private val originalCover: String? = null,
) : Editing {
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
    /** songs already in the playlist are not listed in the pool (on by default, as in the TUI). */
    var hideAdded by mutableStateOf(true)
    /** songs that are in no playlist of the library: listed with the rest, hidden or the only ones listed. */
    var loose by mutableStateOf(LooseSongs.Show)
    /** the dates a [SortKey.needsDates] sorting uses (read when such a sorting is chosen). */
    var dates by mutableStateOf<Map<Long, TrackDates>>(emptyMap())
    /** the search field is shown (over the pane switch). */
    var searching by mutableStateOf(false)
    /** one search for both panes; kept when the field is closed. */
    var query by mutableStateOf("")
    /** how open the top sheet with the name and the cover is (0..1); open when the editor opens. */
    var sheetFraction by mutableFloatStateOf(1f)
    /** bumped to open the sheet (a failed save points at the name). */
    var sheetRequests by mutableIntStateOf(0)

    fun openSheet() {
        sheetRequests++
    }

    enum class Pane { Playlist, Pool }

    val ids: List<Long> get() = assembled.mapNotNull { it.track.id }

    private val originalIds = tracks.mapNotNull { it.id }

    override val hasChanges: Boolean
        get() = name.trim() != originalName.orEmpty() || cover != originalCover || ids != originalIds

    override var saveRequests by mutableIntStateOf(0)

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
        val m = TrackMatcher(query)
        return assembled.withIndex().filter { m.matches(it.value.track) }
    }

    /** the pool to pick from: search + "hide added" + sorting. */
    /** [inPlaylists] — ids of the songs in some saved playlist. */
    fun poolView(pool: List<Track>, inPlaylists: Set<Long>): List<Track> {
        val m = TrackMatcher(query)
        val added = ids.toHashSet()
        return pool.filter { t ->
            val isLoose = t.id !in inPlaylists
            val shown = when (loose) {
                LooseSongs.Show -> true
                LooseSongs.Hide -> !isLoose
                LooseSongs.Only -> isLoose
            }
            shown && m.matches(t) && !(hideAdded && t.id in added)
        }.sortedWith(sortKey.comparator(descending, dates))
    }

    private fun entry(t: Track) = Entry(nextKey++, t)
}

/** what the pool does with the songs that are in no playlist. */
enum class LooseSongs(val label: String) { Show("Все"), Hide("Скрыть"), Only("Только они") }
