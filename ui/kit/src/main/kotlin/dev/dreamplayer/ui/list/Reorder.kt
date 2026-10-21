package dev.dreamplayer.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * drag-and-drop reordering of a lazy list: the dragged row follows the finger,
 * the others make way as it passes their middle ([onMove] with list indices,
 * as many times as rows are passed) and the list scrolls by itself near its
 * edges. The rows are given [reorderItem]; a drag starts from a long press
 * on a row ([longPressDrag]), which also tells the row whether it was moved
 * (a long press without a move opens its menu).
 */
class ReorderState internal constructor(
    private val list: LazyListState,
    private val edgePx: Float,
) {
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    internal var haptic: HapticFeedback? = null

    /** the held row was moved during this drag. */
    private var moved = false

    /** key of the dragged row; null — no drag. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** how far the dragged row is drawn from where the list placed it. */
    internal var offset by mutableFloatStateOf(0f)
        private set

    /** px per frame the list scrolls by itself (the row is held near an edge). */
    internal var autoScroll by mutableFloatStateOf(0f)
        private set

    /** the index the dragged row was moved to and the list hasn't placed yet. */
    private var pendingIndex: Int? = null

    internal fun start(key: Any) {
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        draggingKey = key
        offset = 0f
        pendingIndex = null
        moved = false
    }

    internal fun drag(dy: Float) {
        if (draggingKey == null) return
        offset += dy
        check()
    }

    /** the drag is over; true — the row was moved. */
    internal fun end(): Boolean {
        val wasMoved = moved
        draggingKey = null
        offset = 0f
        autoScroll = 0f
        pendingIndex = null
        return wasMoved
    }

    internal suspend fun scrollStep() {
        val by = autoScroll
        if (by == 0f) return
        val done = list.scrollBy(by)
        offset += done
        check()
    }

    private fun dragged(): LazyListItemInfo? = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** swaps with the row under the middle of the dragged one; updates the auto-scroll. */
    private fun check() {
        val info = list.layoutInfo
        val item = dragged() ?: return
        // the last move isn't laid out yet: the offsets are stale.
        pendingIndex?.let { if (item.index != it) return }
        pendingIndex = null
        val top = item.offset + offset
        val bottom = top + item.size
        val middle = (top + bottom) / 2

        val target = info.visibleItemsInfo.firstOrNull {
            it.key != item.key && middle >= it.offset && middle < it.offset + it.size
        }
        if (target != null) {
            val placed = if (target.index > item.index) target.offset + target.size - item.size else target.offset
            // a move of the first visible row would drag the scroll position along.
            val first = list.firstVisibleItemIndex
            val firstOffset = list.firstVisibleItemScrollOffset
            onMove(item.index, target.index)
            moved = true
            if (item.index == first || target.index == first) list.requestScrollToItem(first, firstOffset)
            offset = top - placed
            pendingIndex = target.index
        }

        val start = info.viewportStartOffset.toFloat()
        val end = info.viewportEndOffset.toFloat()
        autoScroll = when {
            top < start + edgePx -> -SCROLL_PX * ((start + edgePx - top) / edgePx).coerceAtMost(1f)
            bottom > end - edgePx -> SCROLL_PX * ((bottom - end + edgePx) / edgePx).coerceAtMost(1f)
            else -> 0f
        }
    }

    private companion object {
        /** the fastest auto-scroll, px per frame. */
        const val SCROLL_PX = 24f
    }
}

@Composable
fun rememberReorderState(list: LazyListState, onMove: (from: Int, to: Int) -> Unit): ReorderState {
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val state = remember(list) { ReorderState(list, edge) }
    state.haptic = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    state.onMove = { from, to -> move(from, to) }
    // frames are asked for only while a row is held.
    val dragging = state.draggingKey != null
    LaunchedEffect(state, dragging) {
        while (dragging) {
            withFrameNanos { }
            state.scrollStep()
        }
    }
    return state
}

/**
 * a row of the list: while held it is lifted (a shadow, a bit larger, an
 * opaque background) over the others and follows the finger; otherwise its
 * moves are animated.
 */
@Composable
fun LazyItemScope.reorderItem(state: ReorderState, key: Any): Modifier =
    if (state.draggingKey == key) {
        Modifier.zIndex(1f).graphicsLayer {
            translationY = state.offset
            scaleX = LIFT_SCALE
            scaleY = LIFT_SCALE
            shadowElevation = 8.dp.toPx()
        }.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        Modifier.animateItem()
    }

/**
 * a long press on row [key] lifts it for dragging; released without having
 * changed its place, it calls [onStay] (the row's menu). The release is
 * consumed: the row's tap doesn't fire after a hold. Goes into the row's own
 * [ListRow] `gestures`, so it sees the touches before the row's click.
 */
fun Modifier.longPressDrag(state: ReorderState, key: Any, onStay: () -> Unit = {}): Modifier = pointerInput(state, key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        state.start(key)
        val released = drag(held.id) { change ->
            state.drag(change.positionChange().y)
            change.consume()
        }
        currentEvent.changes.forEach { it.consume() }
        val moved = state.end()
        if (released && !moved) onStay()
    }
}

private const val LIFT_SCALE = 1.02f
