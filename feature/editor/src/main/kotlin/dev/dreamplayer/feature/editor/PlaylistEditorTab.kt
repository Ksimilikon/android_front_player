package dev.dreamplayer.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SwapVert
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
import dev.dreamplayer.ui.PositionDialog
import dev.dreamplayer.ui.form.FormChoice
import dev.dreamplayer.ui.form.FormTextField
import dev.dreamplayer.ui.form.SearchBox
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.list.TrackRow
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.theme.Dream

/**
 * creating / editing a playlist (NEW_PLAYLIST / MOD_<name> of the TUI): the
 * name on top, then either the assembled playlist (reorder, remove) or the
 * song pool with checkboxes (sorting, "hide added", search).
 */
@Composable
fun PlaylistEditorTab(core: DreamCore, nav: Navigator, state: PlaylistEditorState) {
    val library by core.library.library.collectAsStateWithLifecycle()
    var moving by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        FormTextField("Название", state.name, { state.name = it }, Modifier.padding(top = 8.dp))
        FormChoice(
            options = EditorPane.entries,
            selected = if (state.pane == PlaylistEditorState.Pane.Playlist) EditorPane.Playlist else EditorPane.Pool,
            label = { if (it == EditorPane.Playlist) "Плейлист (${state.assembled.size})" else "Добавить песни" },
            onSelect = { state.pane = if (it == EditorPane.Playlist) PlaylistEditorState.Pane.Playlist else PlaylistEditorState.Pane.Pool },
        )

        Column(Modifier.weight(1f)) {
            when (state.pane) {
                PlaylistEditorState.Pane.Playlist -> AssembledPane(state) { moving = it }
                PlaylistEditorState.Pane.Pool -> PoolPane(state, library.pool.tracks)
            }
        }

        // start inset: the settings sheet button sits in the bottom-left corner.
        Row(
            Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = nav::closeCurrent, modifier = Modifier.weight(1f)) { Text("Отмена") }
            Button(
                onClick = {
                    val name = state.name.trim()
                    if (name.isEmpty()) {
                        core.status.warn("нужно название плейлиста")
                        return@Button
                    }
                    if (name != state.originalName && library.playlists.any { it.name == name }) {
                        core.status.warn("плейлист «$name» уже есть")
                        return@Button
                    }
                    core.launch {
                        if (core.library.savePlaylist(name, state.ids, state.originalName)) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.closeCurrent() }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Сохранить") }
        }
    }

    moving?.let { from ->
        PositionDialog(state.assembled.size, onConfirm = { state.move(from, it) }, onDismiss = { moving = null })
    }
}

private enum class EditorPane { Playlist, Pool }

@Composable
private fun AssembledPane(state: PlaylistEditorState, onMove: (Int) -> Unit) {
    val view = state.playlistView()
    Column {
        SearchBox(state.playlistQuery, { state.playlistQuery = it }, placeholder = "Поиск в плейлисте")
        DreamList(
            items = view,
            key = { _, i -> "${state.assembled[i].id}" },
            emptyText = if (state.assembled.isEmpty()) "пусто — добавьте песни во вкладке «Добавить песни»" else "ничего не найдено",
            bottomPadding = androidx.compose.foundation.layout.PaddingValues(),
        ) { _, i ->
            val t = state.assembled[i]
            TrackRow(
                track = t,
                number = i + 1,
                trailing = {
                    IconButton(onClick = { state.assembled.removeAt(i) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Убрать", tint = Dream.colors.error)
                    }
                },
                menu = listOf(
                    MenuAction("Выше", Icons.Filled.ArrowUpward, enabled = i > 0) { state.move(i, i - 1) },
                    MenuAction("Ниже", Icons.Filled.ArrowDownward, enabled = i + 1 < state.assembled.size) { state.move(i, i + 1) },
                    MenuAction("На позицию…", Icons.Filled.SwapVert) { onMove(i) },
                    MenuAction("Убрать", Icons.Filled.Close, danger = true) { state.assembled.removeAt(i) },
                ),
            )
        }
    }
}

@Composable
private fun PoolPane(state: PlaylistEditorState, pool: List<dev.dreamplayer.model.Track>) {
    val view = state.poolView(pool)
    Column {
        SearchBox(state.poolQuery, { state.poolQuery = it }, placeholder = "Поиск по всем песням")
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
            bottomPadding = androidx.compose.foundation.layout.PaddingValues(),
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
