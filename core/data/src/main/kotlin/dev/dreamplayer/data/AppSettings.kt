package dev.dreamplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** settings of the interface itself (the core keeps its own config). */
class AppSettings internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val closePlaylist = MutableStateFlow(prefs.getBoolean(KEY_CLOSE_PLAYLIST, true))

    /** the tab of an opened playlist closes once another tab is chosen. */
    val autoClosePlaylist: StateFlow<Boolean> = closePlaylist.asStateFlow()

    fun setAutoClosePlaylist(value: Boolean) {
        prefs.edit().putBoolean(KEY_CLOSE_PLAYLIST, value).apply()
        closePlaylist.value = value
    }

    private companion object {
        const val KEY_CLOSE_PLAYLIST = "auto_close_playlist"
    }
}
