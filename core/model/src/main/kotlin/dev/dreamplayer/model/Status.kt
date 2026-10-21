package dev.dreamplayer.model

/** severity of a message: errors are red, warnings yellow (as in the TUI). */
enum class StatusKind { Info, Ok, Warn, Error }

data class StatusMessage(
    val text: String,
    val kind: StatusKind,
    /** distinguishes equal messages shown twice in a row. */
    val stamp: Long = System.nanoTime(),
)

/** a set of metadata edits (null — leave the field as is). */
data class MetaEdit(
    val id: Long,
    val title: String? = null,
    val artists: List<String>? = null,
    /** "" clears. */
    val album: String? = null,
    val genres: List<String>? = null,
    /** path of the new cover file. */
    val coverPath: String? = null,
    /** true — drop the separate cover file (the embedded one stays). */
    val clearCover: Boolean = false,
    /** true — set [color] (null clears). */
    val setColor: Boolean = false,
    val color: TrackColor? = null,
    /** "" clears. */
    val label: String? = null,
)
