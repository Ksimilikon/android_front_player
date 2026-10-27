package dev.dreamplayer.feature.exchange

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.data.SyncMode
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import dev.dreamplayer.ui.MessageLog
import dev.dreamplayer.ui.form.FormSwitch
import dev.dreamplayer.ui.form.FormTextField
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.nav.Navigator

/**
 * IMPORT (`:import` of the TUI): a packet file into the library. The core
 * checks the packet, new songs go to Music/<folder> (MediaStore), covers into
 * the app's storage. The same import can come straight from another device
 * (the DEVICES tab, mode "receive").
 */
@Composable
fun ImportTab(core: DreamCore, nav: Navigator, state: ImportState) {
    val context = LocalContext.current
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        state.packet = uri
        state.packetName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 72.dp)) {
        SectionTitle("Пакет")
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Icon(Icons.Filled.FileOpen, contentDescription = null)
            Text("  " + (state.packetName ?: "Выбрать файл…"))
        }
        SectionTitle("Куда")
        FormTextField(
            "Папка в Music/",
            state.subdir,
            { v -> state.subdir = v.replace("..", "").trim('/') },
            supporting = "новые песни сохраняются в Music/${state.subdir.ifEmpty { "…" }}",
        )
        FormSwitch(
            "Принудительно",
            state.force,
            { state.force = it },
            description = "вернуть и плейлисты, удалённые здесь после их изменения в пакете",
        )
        Button(
            onClick = {
                val uri = state.packet
                when {
                    uri == null -> state.log.add(StatusMessage("сначала выберите файл пакета", StatusKind.Warn))
                    state.subdir.isBlank() -> state.log.add(StatusMessage("укажите папку", StatusKind.Warn))
                    else -> {
                        state.running = true
                        core.launch {
                            core.exchange.import(uri, state.force, state.subdir) { state.log.add(it) }
                            state.running = false
                        }
                    }
                }
            },
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Импортировать") }
        OutlinedButton(
            onClick = {
                core.sync.preset.value = SyncMode.Receive(state.force)
                nav.openSync()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text("Принять с устройства") }
        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        MessageLog(state.log)
    }
}
