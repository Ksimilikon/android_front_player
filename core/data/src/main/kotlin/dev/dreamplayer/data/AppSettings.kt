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

    private val pullDistance = MutableStateFlow(prefs.getInt(KEY_PULL_DISTANCE, DEFAULT_PULL_DP).coerceIn(PULL_DP_RANGE))

    /** how far (dp) to pull past the top of a tab for its action (refresh / close) to fire. */
    val pullDistanceDp: StateFlow<Int> = pullDistance.asStateFlow()

    fun setPullDistanceDp(value: Int) {
        val v = value.coerceIn(PULL_DP_RANGE)
        prefs.edit().putInt(KEY_PULL_DISTANCE, v).apply()
        pullDistance.value = v
    }

    private val pullVibration = MutableStateFlow(prefs.getBoolean(KEY_PULL_VIBRATION, true))

    /** a light vibration when the pull reaches [pullDistanceDp]. */
    val pullHaptics: StateFlow<Boolean> = pullVibration.asStateFlow()

    fun setPullHaptics(value: Boolean) {
        prefs.edit().putBoolean(KEY_PULL_VIBRATION, value).apply()
        pullVibration.value = value
    }

    private val vibrationLength = MutableStateFlow(prefs.getInt(KEY_VIBRATION_MS, DEFAULT_VIBRATION_MS).coerceIn(VIBRATION_MS_RANGE))

    /** how long the gesture vibration lasts (ms). */
    val pullHapticMs: StateFlow<Int> = vibrationLength.asStateFlow()

    fun setPullHapticMs(value: Int) {
        val v = value.coerceIn(VIBRATION_MS_RANGE)
        prefs.edit().putInt(KEY_VIBRATION_MS, v).apply()
        vibrationLength.value = v
    }

    private val vibrationStrength = MutableStateFlow(prefs.getInt(KEY_VIBRATION_STRENGTH, DEFAULT_VIBRATION_STRENGTH).coerceIn(VIBRATION_STRENGTH_RANGE))

    /** how strong the gesture vibration is, percent. */
    val pullHapticStrength: StateFlow<Int> = vibrationStrength.asStateFlow()

    fun setPullHapticStrength(value: Int) {
        val v = value.coerceIn(VIBRATION_STRENGTH_RANGE)
        prefs.edit().putInt(KEY_VIBRATION_STRENGTH, v).apply()
        vibrationStrength.value = v
    }

    companion object {
        private const val KEY_VIBRATION_MS = "pull_vibration_ms"
        private const val KEY_VIBRATION_STRENGTH = "pull_vibration_strength"
        const val DEFAULT_VIBRATION_MS = 40
        val VIBRATION_MS_RANGE = 10..200
        const val DEFAULT_VIBRATION_STRENGTH = 70
        val VIBRATION_STRENGTH_RANGE = 10..100
        private const val KEY_CLOSE_PLAYLIST = "auto_close_playlist"
        private const val KEY_PULL_DISTANCE = "pull_distance_dp"
        private const val KEY_PULL_VIBRATION = "pull_vibration"
        const val DEFAULT_PULL_DP = 110
        val PULL_DP_RANGE = 60..220
    }
}
