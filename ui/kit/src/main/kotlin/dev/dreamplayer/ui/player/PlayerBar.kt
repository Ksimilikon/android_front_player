package dev.dreamplayer.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** how the bottom panel switches songs. */
enum class PlayerBarMode { Buttons, Gestures }

/**
 * the bottom panel, three areas: the menu button on the left (its own
 * shade), the song in the middle, the controls. [PlayerBarMode.Buttons] —
 * previous / play-pause / next grouped on the right, a tap on the song opens
 * its tab; [PlayerBarMode.Gestures] — the middle is one gesture area: slide
 * the song aside to switch (left — next, right — previous), tap to play /
 * pause.
 */
@Composable
fun PlayerBar(
    track: Track?,
    isPaused: Boolean,
    mode: PlayerBarMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = track != null
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the menu: an area of its own.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(BAR_HEIGHT)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Tune, contentDescription = "Меню") }

            when (mode) {
                PlayerBarMode.Buttons -> {
                    SongText(
                        track,
                        isPaused = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = enabled, onClick = onOpen)
                            .padding(horizontal = 12.dp),
                    )
                    Row(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrevious, enabled = enabled, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущая")
                        }
                        FilledIconButton(onClick = onPlayPause, enabled = enabled, modifier = Modifier.size(40.dp)) {
                            Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = "Пауза / воспроизведение")
                        }
                        IconButton(onClick = onNext, enabled = enabled, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Следующая")
                        }
                    }
                }
                PlayerBarMode.Gestures -> GestureArea(
                    track,
                    isPaused,
                    onPrevious,
                    onPlayPause,
                    onNext,
                    Modifier.weight(1f).fillMaxHeight().padding(6.dp),
                )
            }
        }
    }
}

/** title and artists; [isPaused] — also the play state as a small icon (null — not shown). */
@Composable
private fun SongText(track: Track?, isPaused: Boolean?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isPaused != null && track != null) {
            Icon(
                if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                tint = if (isPaused) Dream.colors.muted else Dream.colors.playing,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                track?.title ?: "Выберите песню",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (track != null) MaterialTheme.colorScheme.onSurface else Dream.colors.muted,
            )
            if (track != null && track.artists.isNotEmpty()) {
                Text(
                    track.artistsLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = Dream.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * the gesture area: the song follows the finger sideways and the arrow of
 * where it goes lights up; past a third of the width (or thrown) the song
 * flies out and the next / previous one comes in from the other side. A tap
 * plays / pauses.
 */
@Composable
private fun GestureArea(
    track: Track?,
    isPaused: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    // the song's shift (px): set directly by the finger, animated only after release.
    var offset by remember { mutableFloatStateOf(0f) }
    suspend fun slide(to: Float, ms: Int) = animate(offset, to, animationSpec = tween(ms)) { v, _ -> offset = v }
    val prev by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val toggle by rememberUpdatedState(onPlayPause)
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val threshold = width / 3
        val enabled = track != null
        val progress = (offset / threshold).coerceIn(-1f, 1f)

        // the arrows: where the song goes; full colour once it would switch.
        SideIcon(Icons.Filled.SkipPrevious, Alignment.CenterStart, (progress).coerceAtLeast(0f))
        SideIcon(Icons.Filled.SkipNext, Alignment.CenterEnd, (-progress).coerceAtLeast(0f))

        SongText(
            track,
            isPaused = isPaused,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - 0.6f * abs(progress) }
                .padding(horizontal = 12.dp),
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onTap = { toggle() })
                }
                .pointerInput(enabled, width) {
                    if (!enabled) return@pointerInput
                    val velocity = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { velocity.resetTracking() },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            velocity.addPosition(change.uptimeMillis, change.position)
                            offset += dx
                        },
                        onDragEnd = {
                            val v = velocity.calculateVelocity().x
                            val o = offset
                            val toNext = o < -threshold || (v < -FLING && o < 0)
                            val toPrev = o > threshold || (v > FLING && o > 0)
                            scope.launch {
                                if (toNext || toPrev) {
                                    val out = if (toNext) -width else width
                                    slide(out, 140)
                                    if (toNext) next() else prev()
                                    // the new song comes in from the other side.
                                    offset = -out * 0.5f
                                }
                                slide(0f, 180)
                            }
                        },
                        onDragCancel = { scope.launch { slide(0f, 180) } },
                    )
                },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SideIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    align: Alignment,
    k: Float,
) {
    if (k <= 0f) return
    Icon(
        icon,
        contentDescription = null,
        tint = if (k >= 1f) Dream.colors.playing else Dream.colors.muted,
        modifier = Modifier
            .align(align)
            .padding(horizontal = 10.dp)
            .size(24.dp)
            .graphicsLayer {
                alpha = k
                scaleX = 0.7f + 0.3f * k + if (k >= 1f) 0.15f else 0f
                scaleY = scaleX
            },
    )
}

private val BAR_HEIGHT = 52.dp

/** a throw this fast (px/s) switches even before the threshold. */
private const val FLING = 1200f
