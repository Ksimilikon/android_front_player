package dev.dreamplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dev.dreamplayer.data.PlayerController

/**
 * the core writes to AAudio directly, so audio focus is handled here. The
 * focus is held only while music plays: taken when playback starts, given
 * back when the user pauses (a paused player gets no focus news at all).
 * A loss pauses; a transient loss (a call, a navigation prompt) pauses and
 * resumes on regain — only if the music was playing when it came.
 * (Unplugged headphones: `AudioRouteWatcher` in :core:data.)
 */
internal class AudioFocusGuard(
    context: Context,
    private val controller: PlayerController,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** the music is playing (the last state seen). */
    private var playing = false

    /** it was paused by a transient loss: play again on regain. */
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
        this.playing = playing
        when {
            playing -> {
                // the user started it: whatever was waiting for a regain is moot.
                resumeOnGain = false
                audio.requestAudioFocus(request)
            }
            // paused by the transient loss itself: keep waiting for the regain.
            resumeOnGain -> {}
            // paused by the user (or the queue ended): nothing to come back to.
            else -> audio.abandonAudioFocusRequest(request)
        }
    }

    fun release() {
        resumeOnGain = false
        audio.abandonAudioFocusRequest(request)
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnGain = false
                if (playing) controller.pause()
                audio.abandonAudioFocusRequest(request)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playing) {
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
