package dev.dreamplayer.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.dreamplayer.ui.theme.Dream
import kotlin.math.floor

/** a tab as the tab bar shows it. */
data class TabTitle(val title: String, val closable: Boolean)

/**
 * the tab bar at the top; transient tabs have a close cross. [position] — the
 * page the pager shows, fractional while it is being swiped: the indicator
 * slides under the titles with it instead of jumping once the swipe ends.
 * Read at drawing only, so a swipe neither recomposes nor re-lays out the bar.
 */
@Composable
fun DreamTabRow(
    tabs: List<TabTitle>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    position: () -> Float = { selected.toFloat() },
) {
    // where each title lies: the indicator is drawn exactly under it.
    val titles = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    val color = MaterialTheme.colorScheme.primary

    PrimaryScrollableTabRow(
        selectedTabIndex = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        indicator = {
            var self by remember { mutableStateOf<LayoutCoordinates?>(null) }
            Box(
                Modifier
                    .fillMaxSize()
                    .onPlaced { self = it }
                    .drawBehind {
                        val me = self?.takeIf { it.isAttached } ?: return@drawBehind
                        // left / right edges of title i in the indicator's own coordinates.
                        fun span(i: Int): Pair<Float, Float>? {
                            val c = titles[i]?.takeIf { it.isAttached } ?: return null
                            val x = me.localPositionOf(c, Offset.Zero).x
                            return x to x + c.size.width
                        }
                        val last = (tabs.size - 1).coerceAtLeast(0)
                        val at = position().coerceIn(0f, last.toFloat())
                        val i = floor(at).toInt()
                        val from = span(i) ?: return@drawBehind
                        val to = span((i + 1).coerceAtMost(last)) ?: from
                        val f = at - i
                        val left = lerp(from.first, to.first, f)
                        val right = lerp(from.second, to.second, f)
                        val h = INDICATOR_HEIGHT.toPx()
                        val r = CornerRadius(h, h)
                        val bar = Path().apply {
                            addRoundRect(RoundRect(left, size.height - h, right, size.height, r, r, CornerRadius.Zero, CornerRadius.Zero))
                        }
                        drawPath(bar, color)
                    },
            )
        },
    ) {
        tabs.forEachIndexed { i, t ->
            Tab(
                selected = i == selected,
                onClick = { onSelect(i) },
                selectedContentColor = Dream.colors.playing,
                unselectedContentColor = Dream.colors.muted,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            t.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.onGloballyPositioned { titles[i] = it },
                        )
                        if (t.closable) {
                            IconButton(onClick = { onClose(i) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                },
            )
        }
    }
}

private val INDICATOR_HEIGHT = 3.dp
