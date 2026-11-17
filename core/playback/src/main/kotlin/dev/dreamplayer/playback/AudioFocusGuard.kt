package dev.dreamplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dev.dreamplayer.data.PlayerController
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope

/**
 * the core writes to AAudio directly, so audio focus is handled here. The
 * focus is held only while music plays: taken when playback starts, given
 * back when the user pauses (a paused player gets no focus news at all).
 *
 * An interruption that came while the music played pauses it and brings it
 * back once it is over: a transient loss (a navigation prompt) — on regain; a
 * call — when the call ends, whichever way it took the focus (a messenger call
 * often takes it for good, with no regain). The music comes back only after
 * the call is over and the output has settled (a Bluetooth headset switches
 * back from the phone mode), on a fresh output stream. Music paused before
 * the interruption stays paused.
 */
internal class AudioFocusGuard(
    context: Context,
    private val controller: PlayerController,
    private val scope: CoroutineScope,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** the music is playing (the last state seen). */
    private var playing = false

    /** it was paused by an interruption: play again once that is over. */
    private var resume = false

    /** waits for the interruption to end, then resumes. */
    private var waiter: Job? = null

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
                // the user started it: whatever was waiting to resume is moot.
                resume = false
                waiter?.cancel()
                audio.requestAudioFocus(request)
            }
            // paused by the interruption itself: keep waiting for its end.
            resume -> {}
            // paused by the user (or the queue ended): nothing to come back to.
            else -> audio.abandonAudioFocusRequest(request)
        }
    }

    fun release() {
        resume = false
        waiter?.cancel()
        audio.abandonAudioFocusRequest(request)
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // a call that took the focus for good still ends some time.
                if (playing && inCall()) {
                    interrupted()
                    awaitEnd()
                } else {
                    resume = false
                    if (playing) controller.pause()
                    audio.abandonAudioFocusRequest(request)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playing) {
                interrupted()
                // a call: its end brings the music back even if no regain comes.
                if (inCall()) awaitEnd()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resume) awaitEnd()
        }
    }

    private fun interrupted() {
        resume = true
        controller.pause()
    }

    /** once no call is going on and the output has settled: play again. */
    private fun awaitEnd() {
        waiter?.cancel()
        waiter = scope.launch {
            while (inCall()) delay(CALL_POLL_MS)
            delay(SETTLE_MS)
            if (!resume) return@launch
            resume = false
            audio.requestAudioFocus(request)
            controller.resumeAfterInterruption()
        }
    }

    /** a call is ringing or going on (a phone call or a messenger one). */
    private fun inCall(): Boolean = audio.mode != AudioManager.MODE_NORMAL

    private companion object {
        const val CALL_POLL_MS = 500L

        /** for the output to come back after a call (Bluetooth: phone mode -> music). */
        const val SETTLE_MS = 1500L
    }
}
