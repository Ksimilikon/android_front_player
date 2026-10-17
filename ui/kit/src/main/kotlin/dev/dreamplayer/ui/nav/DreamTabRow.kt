package dev.dreamplayer.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.theme.Dream

/** a tab as the tab bar shows it. */
data class TabTitle(val title: String, val closable: Boolean)

/** the tab bar at the top; transient tabs have a close cross. */
@Composable
fun DreamTabRow(
    tabs: List<TabTitle>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        tabs.forEachIndexed { i, t ->
            Tab(
                selected = i == selected,
                onClick = { onSelect(i) },
                selectedContentColor = Dream.colors.playing,
                unselectedContentColor = Dream.colors.muted,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
