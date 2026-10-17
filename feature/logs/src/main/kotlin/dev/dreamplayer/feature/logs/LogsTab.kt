package dev.dreamplayer.feature.logs

import android.content.ClipData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.LogEntry
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.ui.ConfirmDialog
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.ListHeader
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** which lines the tab shows. */
private enum class LogFilter(val title: String, val accepts: (LogEntry) -> Boolean) {
    All("Все", { true }),
    Calls("Вызовы", { it.source == LogSource.Call }),
    Events("События", { it.source == LogSource.Event || it.source == LogSource.System }),
    Messages("Сообщения", { it.source == LogSource.Status }),
    Problems("Ошибки", { it.kind == StatusKind.Warn || it.kind == StatusKind.Error }),
}

/**
 * the LOGS tab: every call into the core with its status and time, the core's
 * own events (end of track, panics), audio route changes and the messages
 * shown to the user. Newest at the bottom; follows new lines while scrolled
 * to the end.
 */
@Composable
fun LogsTab(core: DreamCore) {
    val entries by core.log.entries.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LogFilter.All) }
    var clearing by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val view = remember(entries, filter) { entries.filter(filter.accepts) }
    // opens at the newest lines.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = view.lastIndex.coerceAtLeast(0))

    // follow the tail, unless the user scrolled up to read.
    LaunchedEffect(view.size) {
        val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val atEnd = last >= list.layoutInfo.totalItemsCount - 2
        if (view.isNotEmpty() && atEnd) list.scrollToItem(view.lastIndex)
    }

    DreamList(
        items = view,
        key = { _, e -> e.id },
        state = list,
        emptyText = "записей нет",
        header = {
            Column {
                ListHeader(
                    title = "Логи ядра",
                    subtitle = "${view.size} из ${entries.size}",
                ) {
                    IconButton(onClick = {
                        val text = view.joinToString("\n", transform = ::plain)
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("dream player log", text))) }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать")
                    }
                    IconButton(onClick = { clearing = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Очистить")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (f in LogFilter.entries) {
                        FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.title) })
                    }
                }
            }
        },
        bottomPadding = PaddingValues(bottom = 72.dp),
    ) { _, e -> LogLine(e) }

    if (clearing) {
        ConfirmDialog(
            title = "Очистить логи?",
            text = "Все записи будут удалены.",
            confirmLabel = "Очистить",
            onConfirm = { core.log.clear() },
            onDismiss = { clearing = false },
        )
    }
}

@Composable
private fun LogLine(e: LogEntry) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${time(e.time)} ${tag(e.source)}",
            color = Dream.colors.muted,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            e.text,
            color = Dream.colors.of(e.kind),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun tag(s: LogSource) = when (s) {
    LogSource.Call -> "→"
    LogSource.Event -> "←"
    LogSource.Status -> "•"
    LogSource.System -> "⚙"
}

private fun time(millis: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(millis))

/** a line for the clipboard (bug reports). */
private fun plain(e: LogEntry) = "${time(e.time)} ${e.source.name.uppercase()} ${e.kind.name.uppercase()} ${e.text}"
