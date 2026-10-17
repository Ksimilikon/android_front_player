package dev.dreamplayer.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.theme.Dream

/** an item of a row's context menu (opened by a long press). */
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    /** destructive actions are red. */
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * the one row every list is made of: leading slot (number / cover / checkbox),
 * title + subtitle, trailing slot. [highlighted] — the playing item (green
 * text and a bar on the left, as the green line in the TUI); [dimmed] —
 * unavailable (an invalid track). A long press opens [menu].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlighted: Boolean = false,
    selected: Boolean = false,
    dimmed: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    menu: List<MenuAction> = emptyList(),
    onClick: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = Dream.colors.playing
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.surfaceContainerHighest
                        highlighted -> accent.copy(alpha = 0.08f)
                        // transparent: the row takes the color of where it lies (tab / sheet).
                        else -> Color.Transparent
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (menu.isNotEmpty()) ({ menuOpen = true }) else null,
                )
                .alpha(if (dimmed) 0.45f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(3.dp).fillMaxHeight().heightIn(min = 56.dp)
                    .background(if (highlighted) accent else Color.Transparent),
            )
            Row(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leading?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (highlighted) accent else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Dream.colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                trailing?.invoke()
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, offset = DpOffset(48.dp, 0.dp)) {
            for (a in menu) {
                val color = if (a.danger) Dream.colors.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(a.label, color = color) },
                    leadingIcon = a.icon?.let { { Icon(it, contentDescription = null, tint = color) } },
                    enabled = a.enabled,
                    onClick = {
                        menuOpen = false
                        a.onClick()
                    },
                )
            }
        }
    }
}
