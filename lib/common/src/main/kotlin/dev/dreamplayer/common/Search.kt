package dev.dreamplayer.common

import dev.dreamplayer.model.Track

/**
 * track search (as `/` in the TUI): case-insensitive over every text field
 * (title, artists, album, genres, path, cover, label); every word of the query
 * must match one of the fields. An empty query matches everything.
 */
class TrackMatcher(query: String) {
    private val terms = query.split(Regex("\\s+")).filter(String::isNotEmpty).map { it.lowercase() }

    val isEmpty: Boolean get() = terms.isEmpty()

    fun matches(t: Track): Boolean {
        if (terms.isEmpty()) return true
        val hay = buildList {
            add(t.title)
            addAll(t.artists)
            t.album?.let(::add)
            addAll(t.genres)
            t.path?.let(::add)
            t.coverArt?.let(::add)
            t.label?.let(::add)
        }.joinToString("\n").lowercase()
        return terms.all(hay::contains)
    }
}

/** indices of [tracks] matching [query], in the original order. */
fun filterTracks(tracks: List<Track>, query: String): List<Int> {
    val m = TrackMatcher(query)
    return tracks.indices.filter { m.matches(tracks[it]) }
}

/** pool sorting in the playlist editor. */
enum class SortKey(val label: String) {
    Title("название"), Artist("артист"), Album("альбом");

    fun of(t: Track): String = when (this) {
        Title -> t.title
        Artist -> t.artistsLine
        Album -> t.album.orEmpty()
    }.lowercase()
}
