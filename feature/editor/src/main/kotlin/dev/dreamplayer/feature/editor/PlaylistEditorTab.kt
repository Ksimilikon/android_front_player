package dev.dreamplayer.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.SortKey
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.PositionDialog
import dev.dreamplayer.ui.form.CompactTextField
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.list.TrackRow
import dev.dreamplayer.ui.list.VerticalScrollbar
import dev.dreamplayer.ui.list.longPressDrag
import dev.dreamplayer.ui.list.rememberReorderState
import dev.dreamplayer.ui.list.reorderItem
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * creating / editing a playlist (NEW_PLAYLIST / MOD_<name> of the TUI): the
 * name and the cover in a top sheet that folds away, then either the
 * assembled playlist (drag to reorder, remove) or the song pool with
 * checkboxes (sorting, "hide added"); one search for both.
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

    // the close gesture asked to save.
    LaunchedEffect(state.saveRequests) {
        if (state.saveRequests > 0) {
            state.saveRequests = 0
            save(core, nav, state, core.library.library.value.playlists.map { it.name })
        }
    }

    // a date sorting: read the dates (again when the library changes).
    LaunchedEffect(state.sortKey, library) {
        val k = state.sortKey
        state.dates = if (k.needsDates) {
            core.library.trackDates(library.pool.tracks, updated = k == SortKey.Updated, onDevice = k == SortKey.OnDevice)
        } else {
            emptyMap()
        }
    }
    var sorting by remember { mutableStateOf(false) }
    // the songs of the saved playlists (the edited one as it is in the library).
    val inPlaylists = remember(library) { library.playlists.flatMap { it.tracks }.mapNotNullTo(HashSet()) { it.id } }
    val focus = LocalFocusManager.current

    EditorSheetLayout(
        state = state,
        sheet = { fraction, toggle, measure ->
            NameCoverSheet(
                state = state,
                fraction = fraction,
                onToggle = toggle,
                measure = measure,
                onPickCover = { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Toolbar(state, onSort = { sorting = true })

            // touching the list: the keyboard goes away; an empty search folds back.
            Column(
                Modifier.weight(1f).pointerInput(state) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        focus.clearFocus()
                        if (state.query.isBlank()) state.searching = false
                    }
                },
            ) {
                when (state.pane) {
                    PlaylistEditorState.Pane.Playlist -> AssembledPane(core, state, nav)
                    PlaylistEditorState.Pane.Pool -> PoolPane(core, state, nav, library.pool.tracks, inPlaylists)
                }
            }

            // small, by the right edge.
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = nav::closeCurrent,
                    contentPadding = COMPACT_BUTTON,
                    modifier = Modifier.height(34.dp),
                ) { Text("Отмена", style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = { save(core, nav, state, library.playlists.map { it.name }) },
                    contentPadding = COMPACT_BUTTON,
                    modifier = Modifier.height(34.dp),
                ) { Text("Сохранить", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    if (sorting) SortDialog(state, onDismiss = { sorting = false })
}

private val COMPACT_BUTTON = PaddingValues(horizontal = 20.dp, vertical = 0.dp)

/**
 * the pane switch with the sort and search buttons; the search field opens
 * over it (its text stays when it is closed — the search icon is then lit).
 */
@Composable
private fun Toolbar(state: PlaylistEditorState, onSort: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT)) {
        if (state.searching) {
            SearchField(state)
        } else {
            Row(Modifier.fillMaxSize().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PaneButton("Плейлист · ${state.assembled.size}", state.pane == PlaylistEditorState.Pane.Playlist) {
                    state.pane = PlaylistEditorState.Pane.Playlist
                }
                PaneButton("Добавить", state.pane == PlaylistEditorState.Pane.Pool) { state.pane = PlaylistEditorState.Pane.Pool }
                Spacer(Modifier.weight(1f))
                if (state.pane == PlaylistEditorState.Pane.Pool) {
                    IconButton(onClick = onSort) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Сортировка") }
                    IconButton(onClick = { state.descending = !state.descending }) {
                        Icon(
                            if (state.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                            contentDescription = if (state.descending) "По убыванию" else "По возрастанию",
                        )
                    }
                }
                IconButton(onClick = { state.searching = true }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Поиск",
                        tint = if (state.query.isNotBlank()) Dream.colors.playing else LocalContentColor.current,
                    )
                }
            }
        }
    }
}

/** a pane of the switch: just its text, the chosen one in the accent. */
@Composable
private fun PaneButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Dream.colors.playing else Dream.colors.muted,
            maxLines = 1,
        )
    }
}

/** the search over both panes; ✕ closes it and keeps the text, the eraser clears it. */
@Composable
private fun BoxScope.SearchField(state: PlaylistEditorState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    CompactTextField(
        value = state.query,
        onValueChange = { state.query = it },
        placeholder = "Поиск: название, исполнитель, альбом…",
        leading = { Icon(Icons.Filled.Search, contentDescription = null, tint = Dream.colors.playing, modifier = Modifier.size(20.dp)) },
        trailing = {
            if (state.query.isNotEmpty()) {
                SmallIconButton(Icons.Filled.Backspace, "Стереть") { state.query = "" }
            }
            SmallIconButton(Icons.Filled.Close, "Скрыть поиск") { state.searching = false }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .align(Alignment.Center)
            .focusRequester(focus),
    )
}

/** an icon button of 36dp (the field is lower than the standard 48dp touch target). */
@Composable
private fun SmallIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** how the songs to add are ordered and which are listed. */
@Composable
private fun SortDialog(state: PlaylistEditorState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сортировка") },
        text = {
            val scroll = rememberScrollState()
            Box {
                // room on the right for the scroll thumb.
                Column(Modifier.verticalScroll(scroll).padding(end = 14.dp)) {
                    for (k in SortKey.entries) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { state.sortKey = k }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.sortKey == k, onClick = null, modifier = Modifier.padding(end = 12.dp))
                            Column {
                                Text(k.label, style = MaterialTheme.typography.bodyLarge)
                                Text(k.hint, style = MaterialTheme.typography.bodySmall, color = Dream.colors.muted)
                            }
                        }
                    }
                    Text(
                        "Порядок",
                        style = MaterialTheme.typography.labelLarge,
                        color = Dream.colors.playing,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.descending,
                            onClick = { state.descending = false },
                            label = { Text(if (state.sortKey.needsDates) "Сначала старые" else "А → Я") },
                        )
                        FilterChip(
                            selected = state.descending,
                            onClick = { state.descending = true },
                            label = { Text(if (state.sortKey.needsDates) "Сначала новые" else "Я → А") },
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { state.hideAdded = !state.hideAdded }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Скрывать уже добавленные", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "песни, которые уже есть в этом плейлисте, не показываются в списке «Добавить»",
                                style = MaterialTheme.typography.bodySmall,
                                color = Dream.colors.muted,
                            )
                        }
                        Switch(checked = state.hideAdded, onCheckedChange = { state.hideAdded = it })
                    }
                    Text(
                        "Песни вне плейлистов",
                        style = MaterialTheme.typography.labelLarge,
                        color = Dream.colors.playing,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                    Text(
                        "песни, которых нет ни в одном сохранённом плейлисте",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dream.colors.muted,
                    )
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (l in LooseSongs.entries) {
                            FilterChip(selected = state.loose == l, onClick = { state.loose = l }, label = { Text(l.label, maxLines = 1, softWrap = false) })
                        }
                    }
                }
                // the thumb shows that the settings go on below.
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) { VerticalScrollbar(scroll) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

private val TOOLBAR_HEIGHT = 48.dp

/** the name of the temporary queue the editor listens through. */
private const val PREVIEW = "Прослушивание"

private fun save(core: DreamCore, nav: Navigator, state: PlaylistEditorState, names: List<String>) {
    val name = state.name.trim()
    val old = state.originalName
    if (name.isEmpty()) {
        core.status.warn("нужно название плейлиста")
        state.openSheet()
        return
    }
    if (name != old && name in names) {
        core.status.warn("плейлист «$name» уже есть")
        state.openSheet()
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
private fun AssembledPane(core: DreamCore, state: PlaylistEditorState, nav: Navigator) {
    val playback by core.player.playback.collectAsStateWithLifecycle()
    val previewing = playback.queue?.takeIf { it.temporary && it.name == PREVIEW }
    val view = state.playlistView()
    val list = rememberLazyListState()
    val canDrag = state.query.isBlank()
    val reorder = rememberReorderState(list) { from, to -> state.move(from, to) }
    var moving by remember { mutableStateOf<Long?>(null) }
    Column {
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
                // a tap listens to the draft from this song (the playlist isn't saved for it).
                playing = previewing?.current == i && previewing.currentTrack?.key == e.track.key,
                onClick = {
                    if (previewing?.current == i && previewing.currentTrack?.key == e.track.key) {
                        core.player.playPause()
                    } else {
                        core.player.preview(state.assembled.map { it.track }, i, PREVIEW)
                    }
                },
                menu = listOf(
                    MenuAction("Играть следом", Icons.Filled.QueuePlayNext) { core.player.playNext(listOf(e.track), e.track.title) },
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
private fun PoolPane(core: DreamCore, state: PlaylistEditorState, nav: Navigator, pool: List<Track>, inPlaylists: Set<Long>) {
    val playback by core.player.playback.collectAsStateWithLifecycle()
    val previewing = playback.queue?.takeIf { it.temporary && it.name == PREVIEW }
    val view = state.poolView(pool, inPlaylists)
    Column {
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
                playing = previewing?.currentTrack?.key == t.key,
                onClick = { state.toggle(t) },
                // a hold: listen to it without adding it.
                menu = listOf(
                    MenuAction("Прослушать", Icons.Filled.Headphones) { core.player.preview(listOf(t), 0, PREVIEW) },
                    MenuAction("Играть следом", Icons.Filled.QueuePlayNext) { core.player.playNext(listOf(t), t.title) },
                    MenuAction("Подробнее", Icons.Filled.Info) { nav.openSong(t) },
                ),
            )
        }
    }
}
