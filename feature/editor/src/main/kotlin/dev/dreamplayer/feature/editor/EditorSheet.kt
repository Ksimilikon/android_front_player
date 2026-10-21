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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.form.FormTextField
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * how open the top sheet is (0 — folded into one row, 1 — half the tab). A
 * started move always finishes by itself: released after a small drag the
 * sheet runs to the end it was dragged towards.
 */
private class SheetMotion(private val scope: CoroutineScope, open: Boolean) {
    var fraction by mutableFloatStateOf(if (open) 1f else 0f)
    /** where the sheet is running to; null — it is still or held. */
    var target: Boolean? = null
        private set
    private var job: Job? = null
    private var dragStart = 0f

    fun settle(open: Boolean) {
        if (target == open) return
        job?.cancel()
        target = open
        job = scope.launch {
            animate(fraction, if (open) 1f else 0f, animationSpec = tween(SETTLE_MS)) { v, _ -> fraction = v }
            target = null
        }
    }

    fun startDrag() {
        job?.cancel()
        target = null
        dragStart = fraction
    }

    /** the open state the released sheet runs to. */
    fun release(velocity: Float): Boolean {
        val moved = fraction - dragStart
        return when {
            abs(velocity) > FLING_PX_S -> velocity > 0
            abs(moved) > START_FRACTION -> moved > 0
            else -> dragStart > 0.5f
        }
    }

    private companion object {
        const val SETTLE_MS = 180
        const val FLING_PX_S = 300f
        /** a drag past this part of the way counts as "started moving". */
        const val START_FRACTION = 0.03f
    }
}

/**
 * [content] under a top sheet that takes half the height when open and folds
 * into the height of a song row (so it never sits under the system shade
 * pull zone alone). Dragged by itself; scrolling the content up folds it,
 * pulling the content down at its top opens it.
 */
@Composable
internal fun EditorSheetLayout(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    sheet: @Composable (fraction: Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = remember { SheetMotion(scope, open) }
    val changeOpen by rememberUpdatedState(onOpenChange)
    val settle = { to: Boolean ->
        motion.settle(to)
        changeOpen(to)
    }
    // open / fold asked from outside (a tap, a failed save).
    LaunchedEffect(open) { motion.settle(open) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val folded = ROW_HEIGHT
        val unfolded = (maxHeight / 2).coerceAtLeast(folded)
        val rangePx = with(LocalDensity.current) { (unfolded - folded).toPx() }.coerceAtLeast(1f)
        val drag = rememberDraggableState { d -> motion.fraction = (motion.fraction + d / rangePx).coerceIn(0f, 1f) }
        val nested = remember(motion) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0 && source == NestedScrollSource.UserInput && motion.fraction > 0f && motion.target != false) {
                        settle(false)
                    }
                    // while the sheet folds the list stays where it is.
                    return if (available.y < 0 && motion.fraction > 0f) Offset(0f, available.y) else Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && source == NestedScrollSource.UserInput && motion.fraction < 1f && motion.target != true) {
                        settle(true)
                    }
                    return Offset.Zero
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(lerp(folded, unfolded, motion.fraction))
                    .draggable(
                        drag,
                        Orientation.Vertical,
                        onDragStarted = { motion.startDrag() },
                        onDragStopped = { v -> settle(motion.release(v)) },
                    ),
            ) { sheet(motion.fraction) }
            Box(Modifier.weight(1f).nestedScroll(nested)) { content() }
        }
    }
}

/** the sheet of the editor: the name and the cover; folded — one row with both. */
@Composable
internal fun NameCoverSheet(state: PlaylistEditorState, fraction: Float, onPickCover: () -> Unit) {
    val first = state.assembled.firstOrNull()?.track
    // without a cover of its own a playlist shows its first song's.
    val coverPath = state.cover ?: first?.coverArt
    Box(Modifier.fillMaxSize()) {
        if (fraction > 0f) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
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
        }
        if (fraction < 1f) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT - HANDLE_HEIGHT)
                    .alpha(1f - fraction)
                    .clickable { state.sheetOpen = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverImage(coverPath, first?.path, Modifier.size(32.dp), shape = RoundedCornerShape(6.dp))
                Text(
                    state.name.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.name.isBlank()) Dream.colors.muted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ExpandMore, contentDescription = "Развернуть", tint = Dream.colors.muted)
            }
        }
        // the grip: a tap opens / folds the sheet.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(HANDLE_HEIGHT)
                .clickable { state.sheetOpen = !state.sheetOpen },
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

/** the height of a song row ([dev.dreamplayer.ui.list.ListRow]): the folded sheet. */
private val ROW_HEIGHT = 56.dp
private val HANDLE_HEIGHT = 12.dp
