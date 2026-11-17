package dev.dreamplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import dev.dreamplayer.data.DreamCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** keeps the process alive while music plays and publishes the media session. */
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var session: MediaSession? = null
    private lateinit var focus: AudioFocusGuard

    override fun onCreate() {
        super.onCreate()
        val controller = DreamCore.get.player
        val player = SessionPlayer(mainLooper, controller, scope)
        focus = AudioFocusGuard(this, controller, scope)
        scope.launch {
            controller.playback.map { !it.isPaused && it.queue != null }.distinctUntilChanged().collect(focus::onPlaying)
        }
        val settings = DreamCore.get.settings
        session = MediaSession.Builder(this, player)
            .apply { launchIntent()?.let(::setSessionActivity) }
            .setCallback(ExternalButtons(settings::ignoreMediaButtons))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** swiped out of recents while paused — nothing to keep running for. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        focus.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * the buttons of headphones and Bluetooth devices: dropped when the user
     * asked to ignore them ([ignore]). They come as media button events, and
     * Bluetooth also as commands from the system Bluetooth app; the
     * notification and the app itself are not affected.
     */
    private class ExternalButtons(private val ignore: () -> StateFlow<Boolean>) : MediaSession.Callback {
        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean =
            if (ignore().value) true else super.onMediaButtonEvent(session, controllerInfo, intent)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int =
            if (ignore().value && controller.packageName == BLUETOOTH) SessionResult.RESULT_ERROR_PERMISSION_DENIED
            else super.onPlayerCommandRequest(session, controller, playerCommand)
    }

    private companion object {
        /** the system app that relays Bluetooth headset / car buttons (AVRCP). */
        const val BLUETOOTH = "com.android.bluetooth"
    }

    /** tapping the notification opens the app (its launcher activity). */
    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
