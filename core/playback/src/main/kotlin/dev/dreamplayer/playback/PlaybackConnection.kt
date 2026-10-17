package dev.dreamplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * binds the interface to [PlaybackService] (connect in onStart, close in
 * onStop): a connected controller is what lets media3 promote the service to
 * the foreground once playback starts.
 */
class PlaybackConnection(context: Context) : AutoCloseable {
    private val future: ListenableFuture<MediaController> =
        MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync()

    override fun close() = MediaController.releaseFuture(future)
}
