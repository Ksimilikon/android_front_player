package dev.dreamplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dreamplayer.common.formatDuration
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.theme.Dream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * the strip at the very top: cover, title and artists of the playing track
 * over three translucent green waves that drift while it plays and freeze on
 * pause; the progress runs along the bottom edge with the time beside it.
 * A tap opens the song tab; a horizontal drag seeks (only once the finger
 * clearly goes sideways — a tap or a vertical move doesn't). Without a track
 * it shows a message and does nothing.
 */
@Composable
fun NowPlayingStrip(
    track: Track?,
    isPaused: Boolean,
    positionS: Long,
    durationS: Long,
    onClick: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(STRIP_HEIGHT)) {
            if (track == null) {
                Text(
                    "Ничего не играет",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dream.colors.muted,
                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
                )
                return@BoxWithConstraints
            }
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            // where a drag would seek to (s); null — not seeking.
            var preview by remember(track.key) { mutableStateOf<Long?>(null) }
            val open by rememberUpdatedState(onClick)
            val seek by rememberUpdatedState(onSeek)
            val duration by rememberUpdatedState(durationS)
            val position by rememberUpdatedState(positionS)

            Waves(track, playing = !isPaused, modifier = Modifier.fillMaxSize())

            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(track.key, widthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // a seek starts only after a clear sideways move:
                            // twice the touch slop and more sideways than down.
                            val slop = viewConfiguration.touchSlop * 2
                            var total = Offset.Zero
                            var seeking = false
                            while (!seeking) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if (!c.pressed) {
                                    // a plain tap (no seek): open the song.
                                    if (abs(total.x) < slop && abs(total.y) < slop) open()
                                    return@awaitEachGesture
                                }
                                total += c.positionChange()
                                if (abs(total.y) > slop) return@awaitEachGesture
                                if (abs(total.x) > slop && abs(total.x) > abs(total.y) * 2) {
                                    seeking = true
                                    c.consume()
                                }
                            }
                            val d = duration.coerceAtLeast(1)
                            val start = position
                            // the whole width is the whole song: relative, so the touch itself doesn't jump.
                            var dx = 0f
                            preview = start
                            val done = horizontalDrag(down.id) { c ->
                                dx += c.positionChange().x
                                preview = (start + dx / widthPx * d).toLong().coerceIn(0, d)
                                c.consume()
                            }
                            val to = preview
                            preview = null
                            if (done && to != null) seek(to)
                        }
                    }
                    .padding(start = 12.dp, end = 12.dp, bottom = PROGRESS_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CoverImage(track.coverArt, track.path, Modifier.size(32.dp), shape = RoundedCornerShape(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track.artists.isNotEmpty()) {
                        Text(
                            track.artistsLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = Dream.colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // the time in the free place on the right, by the bottom edge.
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.BottomEnd) {
                    val shown = preview ?: positionS
                    Text(
                        "${formatDuration(shown)} / ${formatDuration(durationS)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (preview != null) Dream.colors.playing else Dream.colors.muted,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }

            // the progress along the bottom edge.
            val shown = preview ?: positionS
            val fraction = if (durationS > 0) (shown.toFloat() / durationS).coerceIn(0f, 1f) else 0f
            val accent = Dream.colors.playing
            val track0 = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(PROGRESS_HEIGHT)) {
                drawRect(track0)
                drawRect(accent, size = size.copy(width = size.width * fraction))
            }
        }
    }
}

private val STRIP_HEIGHT = 48.dp
private val PROGRESS_HEIGHT = 3.dp

// ───────────────────────────── the waves ─────────────────────────────

/** one value that drifts between [lo] and [hi] and back over [periodS] seconds. */
private class Drift(val lo: Float, val hi: Float, val periodS: Float, val shift: Float) {
    fun at(t: Float): Float {
        val k = 0.5f - 0.5f * cos(2f * PI.toFloat() * (t / periodS + shift))
        return lo + (hi - lo) * k
    }
}

/**
 * a wave's parameters, each drifting in its own small range: height (part of
 * the strip), length (part of the width), level of its middle line, and the
 * speed it travels sideways (widths per second; the sign is the direction).
 */
private class WaveSpec(
    val amplitude: Drift,
    val length: Drift,
    val level: Drift,
    val speed: Drift,
    val alpha: Float,
)

/** three waves picked at random for a song (the same song — the same waves). */
private fun wavesFor(track: Track): List<WaveSpec> {
    val r = Random(track.key.hashCode())
    fun range(min: Float, max: Float, spread: Float): Pair<Float, Float> {
        val a = min + r.nextFloat() * (max - min)
        return a to (a + spread * (0.5f + r.nextFloat() * 0.5f)).coerceAtMost(max + spread)
    }
    fun drift(min: Float, max: Float, spread: Float): Drift {
        val (lo, hi) = range(min, max, spread)
        return Drift(lo, hi, periodS = 6f + r.nextFloat() * 10f, shift = r.nextFloat())
    }
    return List(3) { i ->
        val dir = if (r.nextBoolean()) 1f else -1f
        val speed = drift(0.03f, 0.09f, 0.03f)
        WaveSpec(
            amplitude = drift(0.10f, 0.22f, 0.06f),
            length = drift(0.45f, 1.1f, 0.25f),
            level = drift(0.40f, 0.62f, 0.08f),
            speed = Drift(speed.lo * dir, speed.hi * dir, speed.periodS, speed.shift),
            alpha = 0.16f - i * 0.03f,
        )
    }
}

/**
 * the waves of [track], each filled down to the bottom; layered, their
 * transparency adds up. The clock runs only while [playing]: on pause they
 * stop where they are.
 */
@Composable
private fun Waves(track: Track, playing: Boolean, modifier: Modifier) {
    val specs = remember(track.key) { wavesFor(track) }
    // seconds of playback of this song (the waves' clock) and how far each wave travelled.
    var clock by remember(track.key) { mutableFloatStateOf(0f) }
    val travelled = remember(track.key) { FloatArray(specs.size) }
    LaunchedEffect(track.key, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            for (i in specs.indices) travelled[i] += specs[i].speed.at(clock) * dt
            clock += dt
        }
    }
    val green = Dream.colors.playing
    Canvas(modifier) {
        val t = clock
        val w = size.width
        val h = size.height
        val step = 6f
        for ((i, s) in specs.withIndex()) {
            val amp = s.amplitude.at(t) * h
            val len = (s.length.at(t) * w).coerceAtLeast(1f)
            val mid = s.level.at(t) * h
            val phase = travelled[i] * w
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w + step) {
                    lineTo(x, mid + amp * sin(2f * PI.toFloat() * (x + phase) / len))
                    x += step
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, Color(green.red, green.green, green.blue, s.alpha))
        }
    }
}
