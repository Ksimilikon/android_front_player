package dev.dreamplayer.feature.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.filterTracks
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.ConfirmDialog
import dev.dreamplayer.ui.PositionDialog
import dev.dreamplayer.ui.form.SearchBox
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.ListHeader
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.list.TrackRow
import dev.dreamplayer.ui.nav.Navigator

/**
 * the tab of an opened playlist. While this playlist is the playing queue, the
 * queue is shown (with its temporary reorders / removals) and the playing
 * track is highlighted; otherwise — the playlist from the library. Opening
 * doesn't start playback: a tap on a track does.
 */
@Composable
fun QueueTab(core: DreamCore, nav: Navigator, playlistName: String) {
    val library by core.library.library.collectAsStateWithLifecycle()
    val playback by core.player.playback.collectAsStateWithLifecycle()
    val playlist = library.find(playlistName)
    val queue = playback.queue?.takeIf { it.name == playlistName }
    val tracks = queue?.tracks ?: playlist?.tracks.orEmpty()
    val isPool = playlist?.isPool ?: queue?.isPool ?: false

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var moving by remember { mutableStateOf<Int?>(null) }
    var removing by remember { mutableStateOf<Int?>(null) }

    // the filtered view keeps the real indices (for playback / moves).
    val view = if (searching) filterTracks(tracks, query) else tracks.indices.toList()

    Column(Modifier.fillMaxSize()) {
        DreamList(
            items = view,
            key = { _, i -> "$i:${tracks[i].id ?: tracks[i].path}" },
            header = {
                Column {
                    ListHeader(
                        title = playlistName,
                        subtitle = buildString {
                            append("${tracks.size} песен")
                            if (queue != null) append(" · играет")
                            if (playlist == null && queue == null) append(" · плейлист не найден")
                        },
                    ) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                        if (!isPool && playlist != null) {
                            IconButton(onClick = { nav.openEditPlaylist(playlistName) }) {
                                Icon(Icons.Filled.Tune, contentDescription = "Изменить плейлист")
                            }
                        }
                        IconButton(onClick = { core.player.playPlaylist(playlistName, 0) }, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Воспроизвести с начала")
                        }
                    }
                    if (searching) SearchBox(query, { query = it })
                }
            },
            emptyText = if (searching) "ничего не найдено" else "в плейлисте нет песен",
        ) { _, i ->
            val t = tracks[i]
            TrackRow(
                track = t,
                number = i + 1,
                playing = queue != null && i == queue.current,
                menu = menu(core, nav, t, i, tracks.size, queue != null, isPool, { moving = it }, { removing = it }),
                onClick = {
                    if (queue != null) core.player.select(i) else core.player.playPlaylist(playlistName, i)
                },
            )
        }
    }

    moving?.let { from ->
        PositionDialog(tracks.size, onConfirm = { to -> core.player.move(from, to) }, onDismiss = { moving = null })
    }
    removing?.let { i ->
        ConfirmDialog(
            title = "Удалить из плейлиста?",
            text = "«${tracks.getOrNull(i)?.title}» будет удалён из «$playlistName» в библиотеке.",
            confirmLabel = "Удалить",
            onConfirm = {
                if (queue != null) {
                    core.player.remove(i, fromDb = true)
                } else {
                    val ids = tracks.filterIndexed { j, _ -> j != i }.mapNotNull(Track::id)
                    core.launch { core.library.savePlaylist(playlistName, ids) }
                }
            },
            onDismiss = { removing = null },
        )
    }
}

/** the long-press menu of a track (the TUI SONG tab commands). */
private fun menu(
    core: DreamCore,
    nav: Navigator,
    t: Track,
    i: Int,
    size: Int,
    isQueue: Boolean,
    isPool: Boolean,
    onMove: (Int) -> Unit,
    onRemove: (Int) -> Unit,
): List<MenuAction> = buildList {
    add(MenuAction("Подробнее", Icons.Filled.Info) { nav.openSong(t) })
    add(MenuAction("Изменить метаданные", Icons.Filled.Edit, enabled = t.id != null) { nav.openEditMeta(t) })
    if (isQueue) {
        // temporary: the queue only, not written to the db.
        add(MenuAction("Выше", Icons.Filled.ArrowUpward, enabled = i > 0) { core.player.move(i, i - 1) })
        add(MenuAction("Ниже", Icons.Filled.ArrowDownward, enabled = i + 1 < size) { core.player.move(i, i + 1) })
        add(MenuAction("На позицию…", Icons.Filled.SwapVert) { onMove(i) })
        add(MenuAction("Убрать из очереди", Icons.Filled.PlaylistRemove) { core.player.remove(i, fromDb = false) })
    }
    if (!isPool) add(MenuAction("Удалить из плейлиста", Icons.Filled.Delete, danger = true) { onRemove(i) })
}
