package dev.dreamplayer.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.SortKey
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.PositionDialog
import dev.dreamplayer.ui.form.FormChoice
import dev.dreamplayer.ui.form.SearchBox
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.TrackRow
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.list.longPressDrag
import dev.dreamplayer.ui.list.rememberReorderState
import dev.dreamplayer.ui.list.reorderItem
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * creating / editing a playlist (NEW_PLAYLIST / MOD_<name> of the TUI): the
 * name and the cover in a top sheet that folds into one row, then either the
 * assembled playlist (drag to reorder, remove) or the song pool with
 * checkboxes (sorting, "hide added", search).
 */
@Composable
fun PlaylistEditorTab(core: DreamCore, nav: Navigator, state: PlaylistEditorState) {
    val library by core.library.library.collectAsStateWithLifecycle()
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            core.launch {
                val path = core.covers.importCover(uri)
                if (path != null) state.cover = path else core.status.error("не удалось скопировать обложку")
            }
        }
    }

    EditorSheetLayout(
        open = state.sheetOpen,
        onOpenChange = { state.sheetOpen = it },
        sheet = { fraction ->
            NameCoverSheet(
                state = state,
                fraction = fraction,
                onPickCover = { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormChoice(
                    options = PlaylistEditorState.Pane.entries,
                    selected = state.pane,
                    label = { if (it == PlaylistEditorState.Pane.Playlist) "Плейлист (${state.assembled.size})" else "Добавить песни" },
                    onSelect = { state.pane = it },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    state.searching = !state.searching
                    if (!state.searching) {
                        state.playlistQuery = ""
                        state.poolQuery = ""
                    }
                }) {
                    Icon(if (state.searching) Icons.Filled.SearchOff else Icons.Filled.Search, contentDescription = "Поиск")
                }
            }

            Column(Modifier.weight(1f)) {
                when (state.pane) {
                    PlaylistEditorState.Pane.Playlist -> AssembledPane(state, nav)
                    PlaylistEditorState.Pane.Pool -> PoolPane(state, library.pool.tracks)
                }
            }

            // start inset: the settings sheet button sits in the bottom-left corner.
            Row(
                Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = nav::closeCurrent, modifier = Modifier.weight(1f)) { Text("Отмена") }
                Button(onClick = { save(core, nav, state, library.playlists.map { it.name }) }, modifier = Modifier.weight(1f)) {
                    Text("Сохранить")
                }
            }
        }
    }
}

private fun save(core: DreamCore, nav: Navigator, state: PlaylistEditorState, names: List<String>) {
    val name = state.name.trim()
    val old = state.originalName
    if (name.isEmpty()) {
        core.status.warn("нужно название плейлиста")
        state.sheetOpen = true
        return
    }
    if (name != old && name in names) {
        core.status.warn("плейлист «$name» уже есть")
        state.sheetOpen = true
        return
    }
    val renamed = old != null && old != name
    core.launch {
        // the queue follows the name first, so it sees the saved order under the new one.
        if (renamed) core.player.renamePlaylist(old!!, name)
        val ok = core.library.savePlaylist(name, state.ids, old, state.cover)
        if (!ok && renamed) core.player.renamePlaylist(name, old!!)
        if (ok) {
            withContext(Dispatchers.Main) {
                if (renamed) nav.playlistRenamed(old!!, name)
                nav.closeCurrent()
            }
        }
    }
}

/**
 * the assembled playlist: holding a row lifts it for dragging; let go without
 * having moved it — its menu opens (as in the PLAYING tab). With a search the
 * rows show a part of the order, so the long press only opens the menu.
 */
@Composable
private fun AssembledPane(state: PlaylistEditorState, nav: Navigator) {
    val view = state.playlistView()
    val list = rememberLazyListState()
    val canDrag = state.playlistQuery.isBlank()
    val reorder = rememberReorderState(list) { from, to -> state.move(from, to) }
    var moving by remember { mutableStateOf<Long?>(null) }
    Column {
        if (state.searching) SearchBox(state.playlistQuery, { state.playlistQuery = it }, placeholder = "Поиск в плейлисте")
        DreamList(
            items = view,
            key = { _, e -> e.value.key },
            state = list,
            emptyText = if (state.assembled.isEmpty()) "пусто — добавьте песни во вкладке «Добавить песни»" else "ничего не найдено",
            bottomPadding = PaddingValues(),
        ) { _, (i, e) ->
            val menuOpen = remember { mutableStateOf(false) }
            TrackRow(
                track = e.track,
                number = i + 1,
                modifier = if (canDrag) reorderItem(reorder, e.key) else Modifier,
                gestures = if (canDrag) Modifier.longPressDrag(reorder, e.key) { menuOpen.value = true } else Modifier,
                menuOpen = if (canDrag) menuOpen else null,
                menu = listOf(
                    MenuAction("Подробнее", Icons.Filled.Info) { nav.openSong(e.track) },
                    MenuAction("Изменить метаданные", Icons.Filled.Edit, enabled = e.track.id != null) { nav.openEditMeta(e.track) },
                    MenuAction("На позицию…", Icons.Filled.SwapVert) { moving = e.key },
                    MenuAction("Убрать", Icons.Filled.Close, danger = true) { state.remove(e.key) },
                ),
                trailing = {
                    IconButton(onClick = { state.remove(e.key) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Убрать", tint = Dream.colors.error)
                    }
                },
            )
        }
    }

    moving?.let { key ->
        PositionDialog(
            state.assembled.size,
            onConfirm = { to -> state.move(state.assembled.indexOfFirst { it.key == key }, to) },
            onDismiss = { moving = null },
        )
    }
}

@Composable
private fun PoolPane(state: PlaylistEditorState, pool: List<Track>) {
    val view = state.poolView(pool)
    Column {
        if (state.searching) SearchBox(state.poolQuery, { state.poolQuery = it }, placeholder = "Поиск по всем песням")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (k in SortKey.entries) {
                FilterChip(selected = state.sortKey == k, onClick = { state.sortKey = k }, label = { Text(k.label) })
            }
            IconButton(onClick = { state.descending = !state.descending }) {
                Icon(if (state.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, contentDescription = "Направление")
            }
            FilterChip(selected = state.hideAdded, onClick = { state.hideAdded = !state.hideAdded }, label = { Text("скрыть") })
        }
        DreamList(
            items = view,
            key = { _, t -> "${t.id}" },
            emptyText = "ничего не найдено",
            bottomPadding = PaddingValues(),
        ) { _, t ->
            val added = state.isAdded(t)
            TrackRow(
                track = t,
                selected = added,
                leadingExtra = { Checkbox(checked = added, onCheckedChange = { state.toggle(t) }, modifier = Modifier.size(32.dp)) },
                onClick = { state.toggle(t) },
            )
        }
    }
}
