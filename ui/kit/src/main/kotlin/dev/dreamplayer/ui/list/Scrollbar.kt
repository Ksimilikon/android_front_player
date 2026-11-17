package dev.dreamplayer.ui.list

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * a scroll thumb for content scrolled by [state] (`verticalScroll`): shown
 * all the time while there is something to scroll, so it is seen that the
 * content goes on; the thumb can be dragged. Place it over the right edge of
 * the scrolled area (it fills the height given to it).
 */
@Composable
fun VerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxHeight().width(TOUCH_WIDTH)) {
        val trackPx = with(LocalDensity.current) { maxHeight.toPx() }
        val viewport = state.viewportSize.toFloat().coerceAtLeast(1f)
        val content = viewport + state.maxValue
        val minThumb = with(LocalDensity.current) { MIN_THUMB.toPx() }
        val thumbPx = (trackPx * viewport / content).coerceIn(minThumb, trackPx)
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)
        val top = travel * state.value / state.maxValue
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(state, travel) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        scope.launch { state.scrollBy(dy * state.maxValue / travel) }
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, top.roundToInt()) }
                    .width(THUMB_WIDTH)
                    .height(with(LocalDensity.current) { thumbPx.toDp() })
                    .background(Dream.colors.muted.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

private val THUMB_WIDTH = 4.dp
/** wider than the thumb: easier to catch with a finger. */
private val TOUCH_WIDTH = 16.dp
private val MIN_THUMB = 32.dp
