package dev.dreamplayer.model

/** a library track as the interface sees it. */
data class Track(
    /** index row id (null — the track is not from the library). */
    val id: Long?,
    val path: String?,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val genres: List<String>,
    val durationS: Long,
    val sampleRate: Int,
    val bitrate: Int,
    val listenCount: Long,
    /** path to a separate cover file (null — the embedded one, if any). */
    val coverArt: String?,
    /** saved per-track volume, [VOLUME_MIN]..[VOLUME_MAX]. */
    val volume: Float,
    /** the file is known-bad (playback is skipped by the core). */
    val invalid: Boolean,
    val color: TrackColor?,
    val label: String?,
) {
    val artistsLine: String get() = artists.joinToString(", ")

    /** stable identity (survives moves in the queue). */
    val key: TrackKey get() = TrackKey(id, path, title)

    companion object {
        const val VOLUME_MIN = 0f
        /** the per-track volume may boost up to 2x (as in the TUI). */
        const val VOLUME_MAX = 2f
    }
}

data class TrackKey(val id: Long?, val path: String?, val title: String)

/** the color marks the core accepts. */
enum class TrackColor(val id: String) {
    Red("red"), Pink("pink"), Orange("orange"), Green("green"), Blue("blue"), Cyan("cyan"), Purple("purple");

    companion object {
        fun of(id: String?): TrackColor? = entries.firstOrNull { it.id == id }
    }
}
