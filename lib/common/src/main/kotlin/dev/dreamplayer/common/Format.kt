package dev.dreamplayer.common

import java.util.Locale

/** 83 -> "1:23", 3723 -> "1:02:03". */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

/** size in human-readable form (as `human_size` in the TUI). */
fun humanSize(bytes: Long): String {
    val mb = 1024.0 * 1024.0
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < mb -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
        else -> String.format(Locale.ROOT, "%.1f MiB", bytes / mb)
    }
}

/** 0.8 -> "80%". */
fun percent(v: Float): String = "${(v * 100).toInt()}%"

/** "a, b ,, c" -> [a, b, c]. */
fun splitList(s: String): List<String> = s.split(',').map(String::trim).filter(String::isNotEmpty)
