package dev.dreamplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dev.dreamplayer.data.PlayerController

/**
 * the core writes to AAudio directly, so audio focus is handled here: the
 * focus is requested when playback starts, a loss pauses, a transient loss
 * resumes on regain. (Unplugged headphones: `AudioRouteWatcher` in :core:data.)
 */
internal class AudioFocusGuard(
    context: Context,
    private val controller: PlayerController,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var resumeOnGain = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener(::onFocusChange)
        .build()

    /** playback state changed: hold the focus while playing. */
    fun onPlaying(playing: Boolean) {
        if (playing) {
            resumeOnGain = false
            audio.requestAudioFocus(request)
        }
    }

    fun release() {
        audio.abandonAudioFocusRequest(request)
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnGain = false
                controller.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnGain = true
                controller.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) {
                resumeOnGain = false
                controller.play()
            }
        }
    }
}
