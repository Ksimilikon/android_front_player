package dev.dreamplayer.feature.editor

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.form.FormTextField
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * how open the top sheet is (0 — folded away, 1 — fully open), kept in the
 * editor state. The sheet follows the finger; let go, it stays where it is
 * unless it is near an end or flung (a weak magnet).
 */
private class SheetMotion(private val scope: CoroutineScope, private val state: PlaylistEditorState) {
    var fraction: Float
        get() = state.sheetFraction
        set(v) {
            state.sheetFraction = v.coerceIn(0f, 1f)
        }
    private var job: Job? = null

    fun stop() {
        job?.cancel()
        job = null
    }

    fun animateTo(to: Float, ms: Int = SETTLE_MS) {
        stop()
        if (fraction == to) return
        job = scope.launch { animate(fraction, to, animationSpec = tween(ms)) { v, _ -> fraction = v } }
    }

    /** the finger is lifted ([velocity] px/s, down positive). */
    fun release(velocity: Float) {
        when {
            velocity > FLING_PX_S -> animateTo(1f)
            velocity < -FLING_PX_S -> animateTo(0f)
            fraction < MAGNET -> animateTo(0f)
            fraction > 1f - MAGNET -> animateTo(1f)
        }
    }

    private companion object {
        const val SETTLE_MS = 260
        const val FLING_PX_S = 1200f
        /** this close to an end, the released sheet goes there. */
        const val MAGNET = 0.2f
    }
}

/**
 * [content] under a top sheet that takes up to half the height and folds
 * away completely. Dragged by itself; scrolling the content up folds it,
 * pulling the content down at its top unfolds it — both follow the finger;
 * what the sheet doesn't take goes on (the pull gesture of the tab).
 */
@Composable
internal fun EditorSheetLayout(
    state: PlaylistEditorState,
    sheet: @Composable (fraction: Float, toggle: () -> Unit, measure: Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = remember(state) { SheetMotion(scope, state) }
    // open asked from outside (a failed save).
    LaunchedEffect(state.sheetRequests) {
        if (state.sheetRequests > 0) {
            state.sheetRequests = 0
            motion.animateTo(1f)
        }
    }
    val toggle = { motion.animateTo(if (motion.fraction > 0.5f) 0f else 1f) }

    // the sheet's own height: it opens to it, at most half the tab.
    var natural by remember { mutableIntStateOf(0) }
    val measure = Modifier.onSizeChanged { natural = it.height }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val half = maxHeight / 2
        val unfolded = if (natural > 0) minOf(half, with(density) { natural.toDp() }) else half
        val rangePx = with(density) { unfolded.toPx() }.coerceAtLeast(1f)
        /** moves the sheet by [dy] px; what it took, px. */
        fun move(dy: Float): Float {
            val before = motion.fraction
            motion.fraction = before + dy / rangePx
            return (motion.fraction - before) * rangePx
        }
        val drag = rememberDraggableState { d -> move(d) }
        val nested = remember(motion, rangePx) {
            object : NestedScrollConnection {
                var dragged = false

                // the finger goes up: the sheet folds before the list scrolls.
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y >= 0f || motion.fraction <= 0f) return Offset.Zero
                    motion.stop()
                    dragged = true
                    return Offset(0f, move(available.y))
                }

                // the list is at its top and the finger goes down: the sheet unfolds.
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f || motion.fraction >= 1f) return Offset.Zero
                    motion.stop()
                    dragged = true
                    return Offset(0f, move(available.y))
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!dragged) return Velocity.Zero
                    dragged = false
                    // the sheet moved and is still partly open: the throw is
                    // its, the list doesn't run on under it.
                    val partly = motion.fraction > 0f && motion.fraction < 1f
                    motion.release(available.y)
                    return if (partly) available else Velocity.Zero
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (motion.fraction > 0f) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(unfolded * motion.fraction)
                        .draggable(
                            drag,
                            Orientation.Vertical,
                            onDragStarted = { motion.stop() },
                            onDragStopped = { v -> motion.release(v) },
                        ),
                ) { sheet(motion.fraction, toggle, measure) }
            }
            Box(Modifier.weight(1f).nestedScroll(nested)) { content() }
        }
    }
}

/** the sheet of the editor: the name and the cover (they fade as it folds). */
@Composable
internal fun NameCoverSheet(
    state: PlaylistEditorState,
    fraction: Float,
    onToggle: () -> Unit,
    onPickCover: () -> Unit,
    measure: Modifier = Modifier,
) {
    val first = state.assembled.firstOrNull()?.track
    // without a cover of its own a playlist shows its first song's.
    val coverPath = state.cover ?: first?.coverArt
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                .align(Alignment.BottomCenter)
                .then(measure)
                .padding(top = 8.dp, bottom = HANDLE_HEIGHT)
                .alpha(fraction),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormTextField("Название", state.name, { state.name = it })
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(
                    coverPath,
                    first?.path,
                    Modifier.size(112.dp).clickable(onClick = onPickCover),
                    shape = RoundedCornerShape(12.dp),
                )
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onPickCover) { Text("Выбрать обложку") }
                    if (state.cover != null) {
                        TextButton(onClick = { state.cover = null }) { Text("Убрать обложку", color = Dream.colors.error) }
                    } else {
                        Text("сейчас — обложка первой песни", style = MaterialTheme.typography.bodySmall, color = Dream.colors.muted)
                    }
                }
            }
        }
        // the grip: a tap folds / opens the sheet.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(HANDLE_HEIGHT)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .background(Dream.colors.muted.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

private val HANDLE_HEIGHT = 16.dp
