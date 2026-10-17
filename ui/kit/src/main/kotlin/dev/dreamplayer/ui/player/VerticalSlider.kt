package dev.dreamplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.theme.Dream

/**
 * a vertical slider (bottom = [range] start). [marker] draws a tick at that
 * value (e.g. 100% for the per-track volume that can boost up to 200%).
 * [onValueChangeFinished] fires when the finger is lifted.
 */
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    marker: Float? = null,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val active = if (enabled) Dream.colors.playing else Dream.colors.muted
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val span = range.endInclusive - range.start

    // the track is inset by the thumb radius so the thumb isn't clipped at the ends.
    fun valueAt(y: Float, height: Float, inset: Float): Float =
        range.start + (1f - ((y - inset) / (height - 2 * inset)).coerceIn(0f, 1f)) * span

    Canvas(
        modifier
            .width(40.dp)
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectTapGestures { change(valueAt(it.y, size.height.toFloat(), THUMB.toPx())); finished() }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { finished() },
                    onDragCancel = { finished() },
                ) { c, _ -> change(valueAt(c.position.y, size.height.toFloat(), THUMB.toPx())) }
            },
    ) {
        val w = 8.dp.toPx()
        val thumb = THUMB.toPx()
        val x = (size.width - w) / 2
        val h = size.height - 2 * thumb
        val bottom = size.height - thumb
        val frac = ((value - range.start) / span).coerceIn(0f, 1f)
        val filled = h * frac
        val r = CornerRadius(w / 2, w / 2)
        drawRoundRect(track, Offset(x, thumb), Size(w, h), r)
        drawRoundRect(active, Offset(x, bottom - filled), Size(w, filled), r)
        if (marker != null) {
            val my = bottom - h * ((marker - range.start) / span)
            drawLine(tick, Offset(x - 6.dp.toPx(), my), Offset(x + w + 6.dp.toPx(), my), strokeWidth = 2.dp.toPx())
        }
        drawCircle(active, thumb, Offset(size.width / 2, bottom - filled))
    }
}

private val THUMB = 11.dp
