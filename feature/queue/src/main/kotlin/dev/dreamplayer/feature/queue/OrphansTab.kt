package dev.dreamplayer.feature.queue

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.formatDuration
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.data.OrphanView
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.ListHeader
import dev.dreamplayer.ui.list.ListRow
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.nav.Navigator

private fun OrphanView.label() = when (this) {
    OrphanView.Unmarked -> "Без пометки"
    OrphanView.Kept -> "Оставленные"
    OrphanView.All -> "Все"
}

/**
 * the library's garbage: songs that are in no playlist (the core finds
 * them), each with its file path. Any of them, or all shown at once, can be
 * deleted from the device; a song kept on purpose is marked so the collector
 * stops listing it (it is still shown under "kept" / "all").
 */
@Composable
fun OrphansTab(core: DreamCore, nav: Navigator) {
    val library by core.library.library.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(OrphanView.Unmarked) }
    var orphans by remember { mutableStateOf<List<Track>?>(null) }
    // re-read whenever the library changes (deletions, marks, playlist edits).
    LaunchedEffect(library, view) { orphans = core.library.orphans(view) }
    val delete = rememberDeviceDelete(core)
    val list = orphans.orEmpty()

    DreamList(
        items = list,
        key = { _, t -> t.id ?: t.path ?: t.title },
        header = {
            Column {
                ListHeader(
                    title = "Песни вне плейлистов",
                    subtitle = if (orphans == null) "поиск…" else "${list.size} песен",
                ) {
                    IconButton(onClick = { delete(list) }, enabled = list.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Удалить показанные с устройства")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (v in OrphanView.entries) {
                        FilterChip(selected = v == view, onClick = { view = v }, label = { Text(v.label()) })
                    }
                }
            }
        },
        emptyText = when {
            orphans == null -> "поиск…"
            view == OrphanView.Kept -> "нет оставленных песен"
            else -> "все песни входят в плейлисты"
        },
    ) { _, t ->
        ListRow(
            title = t.title,
            subtitle = listOfNotNull(
                t.path ?: "путь неизвестен",
                formatDuration(t.durationS),
                "оставлена".takeIf { t.ignoreOrphan },
            ).joinToString(" · "),
            dimmed = t.invalid,
            menu = listOf(
                MenuAction("Подробнее", Icons.Filled.Info) { nav.openSong(t) },
                if (t.ignoreOrphan) {
                    MenuAction("Снова показывать в сборщике", Icons.Filled.Visibility, enabled = t.id != null) {
                        core.launch { core.library.setKeepOrphan(t, keep = false) }
                    }
                } else {
                    MenuAction("Не показывать в сборщике", Icons.Filled.VisibilityOff, enabled = t.id != null) {
                        core.launch { core.library.setKeepOrphan(t, keep = true) }
                    }
                },
                MenuAction("Удалить с устройства", Icons.Filled.Delete, danger = true) { delete(listOf(t)) },
            ),
            onClick = { nav.openSong(t) },
        )
    }
}
