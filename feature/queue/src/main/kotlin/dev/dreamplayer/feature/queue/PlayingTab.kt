package dev.dreamplayer.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.filterTracks
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Library
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.ConfirmDialog
import dev.dreamplayer.ui.PositionDialog
import dev.dreamplayer.ui.form.SearchBox
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.ListHeader
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.list.TrackRow
import dev.dreamplayer.ui.list.longPressDrag
import dev.dreamplayer.ui.list.rememberReorderState
import dev.dreamplayer.ui.list.reorderItem
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.theme.Dream

/**
 * the playing queue: a copy of the playlist that can be reordered / thinned
 * out for this playback only (the library isn't touched, except by "remove
 * from the playlist"). Holding a row lifts it for dragging; let go without
 * having moved it — its menu opens. Closing the tab stops playback.
 */
@Composable
fun PlayingTab(core: DreamCore, nav: Navigator) {
    val playback by core.player.playback.collectAsStateWithLifecycle()
    val queue = playback.queue
    val tracks = queue?.tracks.orEmpty()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var moving by remember { mutableStateOf<Int?>(null) }
    var removing by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val deleteFromDevice = rememberDeviceDelete(core)

    // the filtered view keeps the real indices (for playback / moves).
    val view = if (searching) filterTracks(tracks, query) else tracks.indices.toList()
    val keys = rowKeys(tracks)
    val list = rememberLazyListState()
    val reorder = rememberReorderState(list) { from, to -> core.player.move(from, to) }

    Column(Modifier.fillMaxSize()) {
        DreamList(
            items = view,
            key = { _, i -> keys[i] },
            state = list,
            header = {
                Column {
                    ListHeader(
                        title = queue?.name ?: "Ничего не играет",
                        subtitle = queue?.let { "${tracks.size} песен · очередь" },
                    ) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                        if (queue != null) {
                            IconButton(onClick = { saving = true }, enabled = tracks.isNotEmpty()) {
                                Icon(Icons.Filled.Save, contentDescription = "Сохранить очередь как плейлист")
                            }
                            IconButton(onClick = { nav.openPlaylist(queue.name) }) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Открыть плейлист")
                            }
                        }
                    }
                    if (searching) SearchBox(query, { query = it })
                }
            },
            emptyText = if (searching) "ничего не найдено" else "очередь пуста",
        ) { _, i ->
            val t = tracks[i]
            val isPool = queue?.isPool ?: false
            val menuOpen = remember { mutableStateOf(false) }
            TrackRow(
                track = t,
                number = i + 1,
                playing = i == queue?.current,
                // a search shows a part of the order: a long press opens the menu at once.
                modifier = if (searching) Modifier else reorderItem(reorder, keys[i]),
                gestures = if (searching) Modifier else Modifier.longPressDrag(reorder, keys[i]) { menuOpen.value = true },
                menuOpen = if (searching) null else menuOpen,
                menu = buildList {
                    addAll(trackInfoActions(nav, t))
                    // temporary: the queue only, not written to the db.
                    add(MenuAction("На позицию…", Icons.Filled.SwapVert) { moving = i })
                    add(MenuAction("Убрать из очереди", Icons.Filled.PlaylistRemove) { core.player.remove(i, fromDb = false) })
                    if (!isPool) add(MenuAction("Удалить из плейлиста", Icons.Filled.Delete, danger = true) { removing = i })
                    if (isPool) {
                        add(MenuAction("Удалить с устройства", Icons.Filled.DeleteForever, danger = true, enabled = t.path != null) {
                            deleteFromDevice(listOf(t))
                        })
                    }
                },
                // the playing track: pause / resume instead of starting it over.
                onClick = { if (i == queue?.current) core.player.playPause() else core.player.select(i) },
            )
        }
    }

    if (saving && queue != null) {
        val names = core.library.library.value.playlists.map { it.name }
        SaveQueueDialog(
            from = queue.name.takeIf { !queue.isPool },
            names = names,
            onSave = core.player::saveQueueAs,
            onDismiss = { saving = false },
        )
    }

    moving?.let { from ->
        PositionDialog(tracks.size, onConfirm = { to -> core.player.move(from, to) }, onDismiss = { moving = null })
    }
    removing?.let { i ->
        ConfirmDialog(
            title = "Удалить из плейлиста?",
            text = "«${tracks.getOrNull(i)?.title}» будет удалён из «${queue?.name}» в библиотеке.",
            confirmLabel = "Удалить",
            onConfirm = { core.player.remove(i, fromDb = true) },
            onDismiss = { removing = null },
        )
    }
}

/**
 * keys of the rows that stay with a track when it is moved: the track and
 * which of its copies it is (a queue may hold one song twice).
 */
private fun rowKeys(tracks: List<Track>): List<String> {
    val seen = HashMap<Any, Int>()
    return tracks.map { t ->
        val id: Any = t.id ?: t.path ?: t.title
        val n = seen.merge(id, 1, Int::plus)!!
        "$id#$n"
    }
}

/**
 * the name to save the queue under: the playlist it came from ([from], null —
 * the pool) is offered, saving over it is said plainly; another existing
 * name ([names]) is overwritten only after the same warning.
 */
@Composable
private fun SaveQueueDialog(from: String?, names: List<String>, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(from.orEmpty()) }
    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty() && trimmed != Library.POOL_NAME
    val overwrite = trimmed in names
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сохранить очередь") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название плейлиста") }, singleLine = true)
                Text(
                    when {
                        trimmed == Library.POOL_NAME -> "так называется список всех песен"
                        overwrite -> "«$trimmed» будет перезаписан порядком очереди"
                        trimmed.isNotEmpty() -> "будет создан новый плейлист; очередь продолжит играть уже из него"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overwrite || trimmed == Library.POOL_NAME) Dream.colors.error else Dream.colors.muted,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onDismiss(); onSave(trimmed) }) { Text(if (overwrite) "Перезаписать" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
