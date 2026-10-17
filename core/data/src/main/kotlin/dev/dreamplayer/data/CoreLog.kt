package dev.dreamplayer.data

import android.util.Log
import dev.dreamplayer.model.LogEntry
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.StatusKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * the log of everything between the interface and the core: calls with their
 * statuses, core events, user messages, route changes. Keeps the last [LIMIT]
 * lines in memory and mirrors them to logcat (tag `DreamPlayer`).
 */
class CoreLog internal constructor() {
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = state.asStateFlow()

    /** [merge] of the last line, see [add]. */
    private var lastMerge: String? = null
    private var nextId = 0L

    /**
     * appends a line. A line with the same non-null [merge] key as the
     * previous one replaces it: a slider dragged through a hundred volumes
     * leaves one line with the last value, not a hundred.
     */
    fun add(source: LogSource, kind: StatusKind, text: String, merge: String? = null) {
        synchronized(this) {
            val entry = LogEntry(nextId++, System.currentTimeMillis(), source, kind, text)
            val replace = merge != null && merge == lastMerge
            lastMerge = merge
            if (!replace) Log.println(priority(kind), TAG, "[${source.name}] $text")
            state.update { if (replace) it.dropLast(1) + entry else (it + entry).takeLast(LIMIT) }
        }
    }

    fun clear() = synchronized(this) {
        lastMerge = null
        state.value = emptyList()
    }

    private fun priority(kind: StatusKind) = when (kind) {
        StatusKind.Info, StatusKind.Ok -> Log.INFO
        StatusKind.Warn -> Log.WARN
        StatusKind.Error -> Log.ERROR
    }

    private companion object {
        const val TAG = "DreamPlayer"
        const val LIMIT = 2000
    }
}
