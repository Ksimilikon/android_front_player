package dev.dreamplayer.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.roundToInt

/**
 * one vibration of [ms] milliseconds at [strength] (0..1; phones without
 * amplitude control vibrate at their only strength). Straight through the
 * vibrator, as media: the view's haptic feedback and "touch" vibrations are
 * muted by the system touch-feedback setting and are too weak on many phones.
 */
fun buzz(context: Context, ms: Int, strength: Float) {
    val v = vibratorOf(context) ?: return
    val amplitude = if (v.hasAmplitudeControl()) (strength.coerceIn(0f, 1f) * 255).roundToInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
    val effect = VibrationEffect.createOneShot(ms.toLong().coerceAtLeast(1), amplitude)
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
        }
    }
}

/** the phone can set the vibration strength (otherwise only the length changes). */
fun canSetVibrationStrength(context: Context): Boolean = vibratorOf(context)?.hasAmplitudeControl() == true

private fun vibratorOf(context: Context): Vibrator? {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    return v?.takeIf { it.hasVibrator() }
}
