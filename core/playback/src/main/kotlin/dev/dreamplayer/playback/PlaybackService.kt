package dev.dreamplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.dreamplayer.data.DreamCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        focus = AudioFocusGuard(this, controller)
        scope.launch {
            controller.playback.map { !it.isPaused && it.queue != null }.distinctUntilChanged().collect(focus::onPlaying)
        }
        session = MediaSession.Builder(this, player)
            .apply { launchIntent()?.let(::setSessionActivity) }
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

    /** tapping the notification opens the app (its launcher activity). */
    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
