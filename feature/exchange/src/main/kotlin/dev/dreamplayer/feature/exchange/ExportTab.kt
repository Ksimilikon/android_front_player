package dev.dreamplayer.feature.exchange

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.data.ExportFlags
import dev.dreamplayer.data.ExportWhat
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import dev.dreamplayer.ui.MessageLog
import dev.dreamplayer.ui.form.FormChoice
import dev.dreamplayer.ui.form.FormSwitch
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.list.ListRow

/**
 * EXPORT (`:export` of the TUI): the whole library, chosen playlists or chosen
 * tracks into a packet file created through the system picker.
 */
@Composable
fun ExportTab(core: DreamCore, state: ExportState) {
    val library by core.library.library.collectAsStateWithLifecycle()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val what = when (state.mode) {
            ExportMode.Library -> ExportWhat.Library
            // marked items, in list order.
            ExportMode.Playlists -> ExportWhat.Playlists(library.playlists.map { it.name }.filter(state.checkedPlaylists::contains))
            ExportMode.Tracks -> ExportWhat.Tracks(library.pool.tracks.mapNotNull { it.id }.filter(state.checkedTracks::contains))
        }
        val flags = ExportFlags(state.audio, state.names, state.covers && state.mode != ExportMode.Tracks)
        state.running = true
        core.launch {
            core.exchange.export(uri, what, flags) { state.log.add(it) }
            state.running = false
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionTitle("Что экспортировать")
            FormChoice(ExportMode.entries, state.mode, { it.label }, { state.mode = it })
            FormSwitch("Аудио", state.audio, { state.audio = it }, description = "сами файлы песен (иначе только данные)")
            FormSwitch("Имена файлов", state.names, { state.names = it })
            FormSwitch(
                "Обложки",
                state.covers,
                { state.covers = it },
                enabled = state.mode != ExportMode.Tracks,
                description = if (state.mode == ExportMode.Tracks) "недоступно без плейлистов" else null,
            )
            Button(
                onClick = {
                    val nothing = when (state.mode) {
                        ExportMode.Library -> false
                        ExportMode.Playlists -> state.checkedPlaylists.isEmpty()
                        ExportMode.Tracks -> state.checkedTracks.isEmpty()
                    }
                    if (nothing) {
                        state.log.add(StatusMessage("ничего не выбрано: отметьте элементы в списке", StatusKind.Warn))
                    } else {
                        create.launch("dream_player_${System.currentTimeMillis() / 1000}.dplr")
                    }
                },
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Экспортировать") }
            if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            MessageLog(state.log)
        }

        when (state.mode) {
            ExportMode.Library -> {}
            ExportMode.Playlists -> {
                item {
                    SelectionHeader("Плейлисты", state.checkedPlaylists.size) {
                        val all = library.playlists.map { it.name }
                        if (state.checkedPlaylists.containsAll(all)) state.checkedPlaylists.clear()
                        else state.checkedPlaylists.apply { clear(); addAll(all) }
                    }
                }
                items(library.playlists, key = { "pl:" + it.name }) { p ->
                    val checked = p.name in state.checkedPlaylists
                    ListRow(
                        title = p.name,
                        subtitle = "${p.tracks.size} песен",
                        selected = checked,
                        leading = { Checkbox(checked, onCheckedChange = null) },
                        onClick = { if (checked) state.checkedPlaylists.remove(p.name) else state.checkedPlaylists.add(p.name) },
                    )
                }
            }
            ExportMode.Tracks -> {
                item {
                    SelectionHeader("Треки", state.checkedTracks.size) {
                        val all = library.pool.tracks.mapNotNull { it.id }
                        if (state.checkedTracks.containsAll(all)) state.checkedTracks.clear()
                        else state.checkedTracks.apply { clear(); addAll(all) }
                    }
                }
                items(library.pool.tracks.filter { it.id != null }, key = { "t:" + it.id }) { t ->
                    val id = t.id!!
                    val checked = id in state.checkedTracks
                    ListRow(
                        title = t.title,
                        subtitle = t.artistsLine,
                        selected = checked,
                        leading = { Checkbox(checked, onCheckedChange = null) },
                        onClick = { if (checked) state.checkedTracks.remove(id) else state.checkedTracks.add(id) },
                    )
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 72.dp)) }
    }
}

@Composable
private fun SelectionHeader(title: String, count: Int, onToggleAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        SectionTitle("$title · выбрано $count")
        TextButton(onClick = onToggleAll) { Text("все / ничего") }
    }
}
