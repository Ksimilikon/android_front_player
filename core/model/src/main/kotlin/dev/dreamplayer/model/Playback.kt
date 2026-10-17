package dev.dreamplayer.model

/** the playing queue: a copy of a playlist that can be reordered temporarily. */
data class Queue(
    /** name of the playlist the queue came from. */
    val name: String,
    val isPool: Boolean,
    val tracks: List<Track>,
    /** index of the playing track in [tracks]. */
    val current: Int,
) {
    val currentTrack: Track? get() = tracks.getOrNull(current)
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
