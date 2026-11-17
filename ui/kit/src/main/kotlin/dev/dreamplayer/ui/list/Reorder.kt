package dev.dreamplayer.ui.list

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
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
 * (a long press without a move opens its menu). The finger is then followed
 * by the list itself ([reorderContainer]), not by the row: the list may
 * dispose a row's node while it moves (then its gesture would be cut off and
 * the row left hanging somewhere off screen).
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

    /**
     * where the dragged row is drawn: its top in the list's viewport. It
     * follows only the finger — not the scroll, not the moves — so the row
     * never jumps; the shift from where the list placed it is worked out
     * when it is drawn ([drawShift]), from the layout of that very frame.
     */
    private var dragTop by mutableFloatStateOf(0f)

    /** px per frame the list scrolls by itself (the row is held near an edge). */
    private var autoScroll = 0f

    /**
     * the list scrolls by itself: the other rows don't slide into their new
     * places then (a swap every few frames would pile the slides up into a
     * shimmer), they just stand where they belong.
     */
    internal var autoScrolling by mutableStateOf(false)
        private set

    /** the finger holding the row. */
    internal var pointer: PointerId? = null
        private set

    /** what to do when the row is let go without having been moved (its menu). */
    private var onStay: () -> Unit = {}

    /** where the dragged row was last laid out (to bring it back if the list loses it). */
    private var lastIndex = -1

    /** the index the dragged row was moved to and the list hasn't placed yet. */
    private var pendingIndex: Int? = null

    /** frames spent waiting for [pendingIndex]; past [PENDING_LIMIT] it is given up. */
    private var pendingWait = 0

    internal fun start(key: Any, pointer: PointerId, onStay: () -> Unit) {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        this.pointer = pointer
        this.onStay = onStay
        lastIndex = item.index
        dragTop = item.offset.toFloat()
        draggingKey = key
        autoScroll = 0f
        pendingIndex = null
        pendingWait = 0
        moved = false
    }

    internal fun drag(dy: Float) {
        if (draggingKey == null) return
        dragTop += dy
    }

    /** the finger is lifted: the row stays where it is; not moved — its menu. */
    internal fun release() {
        val stay = !end()
        if (stay) onStay()
    }

    /** the drag is over; true — the row was moved. */
    internal fun end(): Boolean {
        val wasMoved = moved
        pointer = null
        draggingKey = null
        autoScroll = 0f
        autoScrolling = false
        pendingIndex = null
        return wasMoved
    }

    /** the drawn row's shift from its laid-out place (read while drawing). */
    internal fun drawShift(key: Any): Float {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return dragTop - item.offset
    }

    /** once a frame while a row is held: scroll near an edge, then move the row. */
    internal suspend fun frame() {
        if (draggingKey == null) return
        if (autoScroll != 0f) list.scrollBy(autoScroll)
        check()
    }

    private fun dragged(): LazyListItemInfo? = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** swaps with the row under the middle of the dragged one; updates the auto-scroll. */
    private fun check() {
        val info = list.layoutInfo
        val start = info.viewportStartOffset.toFloat()
        val end = info.viewportEndOffset.toFloat()
        // the row is drawn where the finger holds it, whatever the list does.
        val item = dragged()
        if (item == null) {
            // the list lost the row (it was laid out off screen): bring it back.
            autoScroll = 0f
            val back = pendingIndex ?: lastIndex
            if (back >= 0) list.requestScrollToItem(back, (start - dragTop).toInt().coerceAtLeast(0))
            return
        }
        lastIndex = item.index
        val top = dragTop.coerceIn(start - item.size / 2f, end - item.size / 2f)
        dragTop = top
        val bottom = top + item.size

        autoScroll = when {
            top < start + edgePx -> -SCROLL_PX * ((start + edgePx - top) / edgePx).coerceIn(0f, 1f)
            bottom > end - edgePx -> SCROLL_PX * ((bottom - end + edgePx) / edgePx).coerceIn(0f, 1f)
            else -> 0f
        }
        // can it still scroll that way at all?
        if (autoScroll < 0f && !list.canScrollBackward || autoScroll > 0f && !list.canScrollForward) autoScroll = 0f
        autoScrolling = autoScroll != 0f

        // the last move isn't laid out yet: the places are stale (the scroll goes on).
        pendingIndex?.let {
            if (item.index != it && ++pendingWait < PENDING_LIMIT) return
        }
        pendingIndex = null
        pendingWait = 0

        val middle = (top + bottom) / 2
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != item.key && middle >= it.offset && middle < it.offset + it.size
        } ?: return
        val first = list.firstVisibleItemIndex
        val firstOffset = list.firstVisibleItemScrollOffset
        // into the first visible place only at the very top: the list keeps its
        // scroll by its first visible row, and for a frame the dragged one
        // would land above the screen (it blinks). While the list can scroll
        // up, the next row comes in anyway and the swap happens one row later.
        if (target.index == first && list.canScrollBackward) return
        // a move of the first visible row would drag the scroll position along.
        onMove(item.index, target.index)
        moved = true
        if (item.index == first || target.index == first) list.requestScrollToItem(first, firstOffset)
        pendingIndex = target.index
    }

    private companion object {
        /** the fastest auto-scroll, px per frame. */
        const val SCROLL_PX = 24f

        /** frames a move may take to be laid out. */
        const val PENDING_LIMIT = 30
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
            state.frame()
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
            translationY = state.drawShift(key)
            scaleX = LIFT_SCALE
            scaleY = LIFT_SCALE
            shadowElevation = 8.dp.toPx()
        }.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        // making way: quick; while the list scrolls by itself — no slide at all.
        Modifier.animateItem(placementSpec = if (state.autoScrolling) null else spring(stiffness = Spring.StiffnessMedium))
    }

/**
 * a long press on row [key] lifts it for dragging; the drag itself is
 * followed by the list ([reorderContainer]). Let go without having changed
 * its place, it calls [onStay] (the row's menu); the release is consumed, so
 * the row's tap doesn't fire after a hold. Goes into the row's own [ListRow]
 * `gestures`, so it sees the touches before the row's click.
 */
fun Modifier.longPressDrag(state: ReorderState, key: Any, onStay: () -> Unit = {}): Modifier = pointerInput(state, key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // the system long press + [EXTRA_HOLD_MS]: a row isn't lifted by a slow scroll start.
        val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + EXTRA_HOLD_MS) {
            while (true) {
                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed || c.isConsumed) break
                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
            true
        }
        if (cancelled != null) return@awaitEachGesture
        state.start(key, down.id, onStay)
    }
}

/**
 * goes on the list (around the lazy column): follows the finger that holds a
 * row, in the list's own coordinates (they don't move with the rows), and
 * takes its moves before the list could scroll by them.
 */
fun Modifier.reorderContainer(state: ReorderState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val id = state.pointer ?: continue
            val c = event.changes.firstOrNull { it.id == id } ?: continue
            if (!c.pressed) {
                event.changes.forEach { it.consume() }
                state.release()
                continue
            }
            state.drag(c.position.y - c.previousPosition.y)
            c.consume()
        }
    }
}

private const val LIFT_SCALE = 1.02f

/** how much longer than the system long press a row must be held to be lifted. */
private const val EXTRA_HOLD_MS = 200L
