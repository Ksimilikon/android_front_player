package dev.dreamplayer.feature.meta

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dreamplayer.common.formatDuration
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.form.FormTextField
import dev.dreamplayer.ui.form.InfoLine
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.theme.Dream
import dev.dreamplayer.ui.theme.display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** editing the metadata of one song (MOD_SONG of the TUI). */
@Composable
fun MetaEditorTab(core: DreamCore, nav: Navigator, state: MetaEditorState) {
    val t = state.track
    val scope = rememberCoroutineScope()
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = core.covers.importCover(uri)
                if (path != null) state.newCover = path else core.status.error("не удалось скопировать обложку")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(state.newCover ?: t.coverArt, t.path, Modifier.size(96.dp), shape = RoundedCornerShape(12.dp))
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Выбрать обложку") }
                    if (state.newCover != null) {
                        Text("новая обложка будет сохранена", style = MaterialTheme.typography.bodySmall, color = Dream.colors.playing)
                    }
                }
            }

            SectionTitle("Теги")
            FormTextField("Название", state.title, { state.title = it }, supporting = "пусто — не менять")
            FormTextField("Артисты", state.artists, { state.artists = it }, supporting = "через запятую; пусто — не менять")
            FormTextField("Альбом", state.album, { state.album = it }, supporting = "пусто — очистить")
            FormTextField("Жанры", state.genres, { state.genres = it }, supporting = "через запятую")

            SectionTitle("Метки")
            FormTextField("Метка", state.label, { state.label = it })
            ColorPicker(state)

            SectionTitle("Только чтение")
            InfoLine("Путь", t.path ?: "—")
            InfoLine("Битрейт", "${t.bitrate} kbps")
            InfoLine("Частота", "${t.sampleRate} Hz")
            InfoLine("Длительность", formatDuration(t.durationS))
            InfoLine("Прослушиваний", t.listenCount.toString())
        }

        // start inset: the settings sheet button sits in the bottom-left corner.
        Row(
            Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = nav::closeCurrent, modifier = Modifier.weight(1f)) { Text("Отмена") }
            Button(
                onClick = {
                    if (t.id == null) {
                        core.status.warn("трек не из библиотеки")
                        return@Button
                    }
                    core.launch {
                        if (core.library.applyMeta(state.toEdit())) withContext(Dispatchers.Main) { nav.closeCurrent() }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Сохранить") }
        }
    }
}

/** the color mark: none or one of the colors the core knows. */
@Composable
private fun ColorPicker(state: MetaEditorState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (c in MetaEditorState.COLORS) {
            val selected = state.color == c
            Box(
                Modifier
                    .size(36.dp)
                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else Dream.colors.muted, CircleShape)
                    .padding(5.dp)
                    .background(c?.display() ?: MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .clickable { state.color = c },
                contentAlignment = Alignment.Center,
            ) {
                if (c == null) Icon(Icons.Filled.Block, contentDescription = "Без цвета", tint = Dream.colors.muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
