package dev.dreamplayer.model

/** the playing queue: a copy of a playlist that can be reordered temporarily. */
data class Queue(
    /** name of the playlist the queue came from. */
    val name: String,
    val isPool: Boolean,
    val tracks: List<Track>,
    /** index of the playing track in [tracks]. */
    val current: Int,
    /**
     * the track ids of the playlist in the db when the queue last took them:
     * a different list there (a save of the playlist) replaces the queue's
     * temporary order.
     */
    val sourceIds: List<Long> = tracks.mapNotNull(Track::id),
    /**
     * not a playlist of the library (a preview in the playlist editor): the
     * library doesn't refresh it and "remove from the playlist" doesn't apply.
     */
    val temporary: Boolean = false,
    /**
     * a key of its own for every entry of [tracks] (same order): it stays with
     * the entry through moves, so two copies of one song are never mixed up
     * (the rows of the queue, drag-and-drop).
     */
    val entryKeys: List<Long> = newEntryKeys(tracks.size),
) {
    val currentTrack: Track? get() = tracks.getOrNull(current)

    companion object {
        private val next = java.util.concurrent.atomic.AtomicLong()

        /** [n] keys never given out before (in this process). */
        fun newEntryKeys(n: Int): List<Long> = List(n) { next.incrementAndGet() }
    }
}

data class PlaybackState(
    /** null — nothing chosen for playback. */
    val queue: Queue? = null,
    val isPaused: Boolean = true,
    val positionS: Long = 0,
    val durationS: Long = 0,
    /** volume of the playing track (the engine's value). */
    val trackVolume: Float = 1f,
    val masterVolume: Float = 1f,
) {
    val currentTrack: Track? get() = queue?.currentTrack

    fun isCurrent(track: Track): Boolean = currentTrack?.key == track.key
}
