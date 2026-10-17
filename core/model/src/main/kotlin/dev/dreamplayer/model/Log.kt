package dev.dreamplayer.model

/** where a log line comes from. */
enum class LogSource {
    /** a call into the core and its status. */
    Call,

    /** something the core reported by itself (end of track, a panic). */
    Event,

    /** a message shown to the user (the status banner). */
    Status,

    /** the system around the core (audio route changes). */
    System,
}

/** one line of the core log. [id] — unique within the process; [time] — unix millis. */
data class LogEntry(
    val id: Long,
    val time: Long,
    val source: LogSource,
    val kind: StatusKind,
    val text: String,
)
