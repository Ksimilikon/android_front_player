package dev.dreamplayer.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.theme.Dream

/**
 * the unified list of every tab: an optional header (title, counter,
 * actions), the rows and a placeholder for an empty list.
 */
@Composable
fun <T> DreamList(
    items: List<T>,
    key: (index: Int, item: T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    emptyText: String = "пусто",
    bottomPadding: PaddingValues = PaddingValues(bottom = 72.dp),
    row: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        header?.invoke()
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(emptyText, color = Dream.colors.muted, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = bottomPadding) {
            itemsIndexed(items, key = key) { i, item -> row(i, item) }
        }
    }
}

/** header of a list: a title with a counter and actions on the right. */
@Composable
fun ListHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Dream.colors.muted, maxLines = 1)
                }
            }
            actions()
        }
        HorizontalDivider()
    }
}
