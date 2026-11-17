package dev.dreamplayer.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.buzz
import dev.dreamplayer.ui.theme.Dream
import kotlin.math.roundToInt

/** what pulling past the top of a tab does. */
enum class PullKind { Refresh, Close }

/**
 * the "pull past the top" gesture of a tab: once its content can't scroll
 * up any more, pulling further brings a circle down from the top. Past
 * [threshold] the circle jumps a little bigger and fills with the accent of
 * the action (refresh — green, close — red) with a light vibration
 * ([haptics]); letting go there fires [onTrigger], pulling back below it
 * cancels. [busy] — the refresh is running: the circle stays with a spinner
 * (and the gesture does nothing) until it ends.
 */
@Composable
fun PullAction(
    kind: PullKind,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    threshold: Dp = 110.dp,
    haptics: Boolean = true,
    hapticMs: Int = 40,
    hapticStrength: Float = 0.7f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val limit = with(density) { threshold.toPx() }
    val context = LocalContext.current
    val trigger by rememberUpdatedState(onTrigger)
    val isBusy by rememberUpdatedState(busy)
    val vibrate by rememberUpdatedState(haptics)
    val buzzMs by rememberUpdatedState(hapticMs)
    val buzzStrength by rememberUpdatedState(hapticStrength)

    // how far the finger went past the top (px).
    var pull by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }

    fun setPull(v: Float) {
        pull = v.coerceIn(0f, limit * MAX_PULL)
        val now = pull >= limit
        if (now != armed) {
            armed = now
            if (now && vibrate) buzz(context, buzzMs, buzzStrength)
        }
    }

    /** the finger is lifted: fire if armed, the circle goes back. */
    suspend fun release() {
        if (pull <= 0f) return
        val fire = armed
        armed = false
        if (fire) trigger()
        animate(pull, 0f, animationSpec = tween(RETURN_MS)) { v, _ -> pull = v }
    }

    val connection = remember(limit) {
        object : NestedScrollConnection {
            // finger going back up while the circle is out: take it back first.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || pull <= 0f) return Offset.Zero
                val before = pull
                setPull(before + available.y)
                return Offset(0f, maxOf(available.y, -before))
            }

            // the content is at its top and the finger still goes down: pull.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || isBusy) return Offset.Zero
                setPull(pull + available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                release()
                return available
            }
        }
    }
    // content that doesn't scroll at all (an empty list) passes no scroll on:
    // a drag nobody inside took pulls directly.
    val drag = rememberDraggableState { delta ->
        if (delta > 0f && isBusy) return@rememberDraggableState
        setPull(pull + delta)
    }

    val rest = limit * TRAVEL
    // the spinner keeps the circle out after the release.
    val held by animateFloatAsState(if (busy) rest else 0f, label = "pull-hold")
    val y = maxOf(pull * TRAVEL, held)
    val progress = (pull / limit).coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        if (armed) 1.18f else 0.6f + 0.4f * progress,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pull-scale",
    )
    val accent = if (kind == PullKind.Refresh) Dream.colors.ok else Dream.colors.error
    val fill by animateColorAsState(
        if (armed || busy) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
        tween(120),
        label = "pull-fill",
    )
    val tint by animateColorAsState(if (armed || busy) Color.Black else accent, tween(120), label = "pull-tint")

    Box(
        modifier
            .fillMaxSize()
            .nestedScroll(connection)
            .draggable(drag, Orientation.Vertical, onDragStopped = { release() }),
    ) {
        content()
        if (y > 0.5f || busy) {
            val sizePx = with(density) { CIRCLE.toPx() }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (y - sizePx).roundToInt()) }
                    .graphicsLayer {
                        scaleX = if (busy && pull == 0f) 1f else scale
                        scaleY = scaleX
                        alpha = if (busy) 1f else (progress * 1.5f).coerceAtMost(1f)
                    }
                    .size(CIRCLE)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                } else {
                    Icon(
                        if (kind == PullKind.Refresh) Icons.Filled.Refresh else Icons.Filled.Close,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp).graphicsLayer {
                            rotationZ = if (kind == PullKind.Refresh) progress * 270f else progress * 90f
                        },
                    )
                }
            }
        }
    }
    // a refresh that ended while the finger is still down mustn't leave the circle armed.
    LaunchedEffect(busy) { if (!busy && pull == 0f) armed = false }
}

private val CIRCLE = 40.dp

/** the pull can go this far past the threshold. */
private const val MAX_PULL = 1.5f

/** the circle travels this part of the finger's way (resistance). */
private const val TRAVEL = 0.5f

private const val RETURN_MS = 180
