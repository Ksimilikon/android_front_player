package dev.dreamplayer.common

/**
 * a typed 1-based position -> 0-based index clamped to the list (as
 * `target_index` in the TUI). Not a number / empty list -> null.
 */
fun targetIndex(input: String, len: Int): Int? {
    if (len == 0) return null
    val n = input.trim().toIntOrNull() ?: return null
    return n.coerceIn(1, len) - 1
}

/** a copy of the list with the item [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}
