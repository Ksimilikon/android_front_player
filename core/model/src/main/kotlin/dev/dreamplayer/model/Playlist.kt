package dev.dreamplayer.model

/** a playlist; the name is its identifier in the core. */
data class Playlist(
    val name: String,
    val tracks: List<Track>,
    /** the "all songs" pool: always first, can't be edited or deleted. */
    val isPool: Boolean,
    val coverArt: String? = null,
    /** unix seconds. */
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

/** the whole library: the pool + playlists from the db. */
data class Library(
    val pool: Playlist,
    val playlists: List<Playlist>,
) {
    /** the pool first, then the playlists — the PLAYLISTS tab order. */
    val all: List<Playlist> get() = listOf(pool) + playlists

    fun find(name: String): Playlist? = all.firstOrNull { it.name == name }

    fun track(id: Long): Track? = pool.tracks.firstOrNull { it.id == id }

    companion object {
        const val POOL_NAME = "Все песни"
        val EMPTY = Library(Playlist(POOL_NAME, emptyList(), isPool = true), emptyList())
    }
}
