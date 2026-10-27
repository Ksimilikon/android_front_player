package dev.dreamplayer.feature.meta

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dreamplayer.common.formatDuration
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.data.MetaResult
import dev.dreamplayer.model.MetaEdit
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.form.FormSwitch
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
                if (path != null) state.pickCover(path) else core.status.error("не удалось скопировать обложку")
            }
        }
    }
    // what waits for the system to allow writing the song file: tags not
    // written yet (null — none) and / or the picture to embed.
    var pending by remember { mutableStateOf<Pair<MetaEdit?, String?>?>(null) }
    val writeConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val (tags, picture) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (res.resultCode == Activity.RESULT_OK) {
            core.launch {
                val ok = tags == null || core.library.applyMeta(tags.tagsOnly()) == MetaResult.OK
                if (ok && picture != null) core.files.embedCover(t, picture, asked = true)
            }
        } else {
            core.status.info(if (tags != null) "теги не записаны: запись файла не разрешена" else "обложка не встроена: запись файла не разрешена")
        }
        nav.closeCurrent()
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // without the separate file: what is embedded in the audio file.
                CoverImage(state.cover, t.path, Modifier.size(96.dp), shape = RoundedCornerShape(12.dp))
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Выбрать обложку") }
                    if (state.cover != null) {
                        OutlinedButton(onClick = state::dropCover) { Text("Убрать обложку") }
                    }
                    when {
                        state.newCover != null ->
                            Text("новая обложка будет сохранена", style = MaterialTheme.typography.bodySmall, color = Dream.colors.playing)
                        state.removeCover && t.coverArt != null ->
                            Text("обложка будет убрана; встроенная в файл останется", style = MaterialTheme.typography.bodySmall, color = Dream.colors.playing)
                    }
                }
            }
            FormSwitch(
                "Встроить обложку в файл",
                state.embedCover,
                { state.embedCover = it },
                description = if (state.cover != null) "PNG или JPEG: картинка запишется в теги аудиофайла" else "сначала выберите обложку",
                enabled = state.cover != null,
            )

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
                    val picture = state.cover?.takeIf { state.embedCover }
                    val edit = state.toEdit()
                    core.launch {
                        // the tags and the embedded picture go into the same file: one consent covers both.
                        val wait: Pair<MetaEdit?, String?>
                        val consent = when (core.library.applyMeta(edit, canAsk = true)) {
                            MetaResult.FAILED -> return@launch
                            MetaResult.WRITE_DENIED -> {
                                wait = edit to picture
                                core.files.writeRequest(t) ?: run {
                                    // nothing to ask: the retry reports the failure.
                                    core.library.applyMeta(edit.tagsOnly())
                                    return@launch
                                }
                            }
                            MetaResult.OK -> {
                                wait = null to picture
                                picture?.let { core.files.embedCover(t, it) }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (consent != null) {
                                pending = wait
                                writeConsent.launch(IntentSenderRequest.Builder(consent).build())
                            } else {
                                nav.closeCurrent()
                            }
                        }
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
