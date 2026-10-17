package dev.dreamplayer.playback

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.dreamplayer.data.PlayerController
import dev.dreamplayer.model.PlaybackState
import dev.dreamplayer.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * media3 Player over [PlayerController]: the session sees a three-item window
 * (previous, current, next) so the system shows prev / next buttons; seeking to
 * a neighbour is translated into `previous()` / `next()`. The real queue lives
 * in the controller — the window is rebuilt on every state change.
 */
@OptIn(UnstableApi::class)
internal class SessionPlayer(
    looper: Looper,
    private val controller: PlayerController,
    private val scope: CoroutineScope,
) : SimpleBasePlayer(looper) {
    private var playback: PlaybackState = controller.playback.value

    /** embedded cover of the current track for the notification (null — none). */
    private var artwork: Pair<Track, ByteArray?>? = null

    init {
        scope.launch(Dispatchers.Main) {
            controller.playback.collect {
                playback = it
                it.currentTrack?.let(::loadArtwork)
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val builder = State.Builder().setAvailableCommands(COMMANDS)
        val q = playback.queue
        if (q == null || q.tracks.isEmpty()) {
            return builder
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }
        val n = q.tracks.size
        val window = if (n == 1) listOf(q.current) else listOf((q.current - 1 + n) % n, q.current, (q.current + 1) % n)
        val items = window.mapIndexed { slot, i -> item("$slot:${q.current}", q.tracks[i], slot == window.size / 2 || n == 1) }
        return builder
            .setPlaylist(items)
            .setCurrentMediaItemIndex(if (n == 1) 0 else 1)
            .setContentPositionMs(playback.positionS * 1000)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(!playback.isPaused, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    private fun item(uid: String, t: Track, current: Boolean): MediaItemData {
        val meta = MediaMetadata.Builder()
            .setTitle(t.title)
            .setArtist(t.artistsLine)
            .setAlbumTitle(t.album)
        t.coverArt?.let { meta.setArtworkUri(Uri.fromFile(File(it))) }
        if (current && t.coverArt == null) {
            artwork?.takeIf { it.first.key == t.key }?.second?.let {
                meta.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(t.id?.toString() ?: t.path ?: t.title)
            .setMediaMetadata(meta.build())
            .build()
        val duration = if (current && playback.durationS > 0) playback.durationS else t.durationS
        return MediaItemData.Builder(uid)
            .setMediaItem(mediaItem)
            .setDurationUs(duration * 1_000_000)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) controller.play() else controller.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val currentSlot = if ((playback.queue?.tracks?.size ?: 0) == 1) 0 else 1
        when {
            mediaItemIndex > currentSlot -> controller.next()
            mediaItemIndex in 0 until currentSlot -> controller.previous()
            else -> controller.seek((positionMs / 1000).coerceAtLeast(0))
        }
        return Futures.immediateVoidFuture()
    }

    /** the session asked to stop: pause, but keep the queue (the interface owns it). */
    override fun handleStop(): ListenableFuture<*> {
        controller.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private fun loadArtwork(t: Track) {
        if (t.coverArt != null || artwork?.first?.key == t.key) return
        val path = t.path ?: return
        artwork = t to null
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(path)
                    r.embeddedPicture
                } catch (_: RuntimeException) {
                    null
                } finally {
                    r.release()
                }
            }
            if (artwork?.first?.key == t.key) {
                artwork = t to bytes
                invalidateState()
            }
        }
    }

    private companion object {
        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_PREPARE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
            )
            .build()
    }
}
