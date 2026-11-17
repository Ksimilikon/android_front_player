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

/**
 * dates of a track that are not part of [Track] (read only when a sorting
 * needs them), unix seconds; null — unknown.
 */
data class TrackDates(
    /** the song (its file: audio, tags, embedded cover) last changed — by the library, synced between devices. */
    val updated: Long? = null,
    /** the file appeared or changed on this device (the file system). */
    val onDevice: Long? = null,
)

/** pool sorting in the playlist editor. */
enum class SortKey(val label: String, val hint: String) {
    Title("Название", "по названию песни"),
    Artist("Исполнитель", "по имени исполнителя"),
    Album("Альбом", "по названию альбома"),
    FileName("Имя файла", "по имени файла песни, как он называется в папке"),
    Updated("Обновление песни", "когда песня последний раз менялась (файл, теги, обложка) — в том числе на других устройствах"),
    OnDevice("Появление на телефоне", "когда файл песни появился на этом телефоне или менялся на нём"),
    ;

    /** the sorting reads [TrackDates]. */
    val needsDates: Boolean get() = this == Updated || this == OnDevice

    /**
     * the order of tracks; ascending — A..Z, older first. Tracks without the
     * value (no album, unknown date) always go last.
     */
    fun comparator(descending: Boolean, dates: Map<Long, TrackDates> = emptyMap()): Comparator<Track> {
        fun date(t: Track, f: (TrackDates) -> Long?): Long? = t.id?.let(dates::get)?.let(f)
        return when (this) {
            Title -> byText(descending) { it.title }
            Artist -> byText(descending) { it.artistsLine }
            Album -> byText(descending) { it.album.orEmpty() }
            FileName -> byText(descending) { it.path?.substringAfterLast('/').orEmpty() }
            Updated -> byValue(descending) { date(it, TrackDates::updated) }
            OnDevice -> byValue(descending) { date(it, TrackDates::onDevice) }
        }
    }

    private fun byText(descending: Boolean, f: (Track) -> String): Comparator<Track> =
        byValue(descending) { t -> f(t).lowercase().takeIf { it.isNotEmpty() } }

    private fun <V : Comparable<V>> byValue(descending: Boolean, f: (Track) -> V?): Comparator<Track> =
        Comparator { a, b ->
            val x = f(a)
            val y = f(b)
            when {
                x == null && y == null -> 0
                x == null -> 1
                y == null -> -1
                descending -> y.compareTo(x)
                else -> x.compareTo(y)
            }
        }
}
