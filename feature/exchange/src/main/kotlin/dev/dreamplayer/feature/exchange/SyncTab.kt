package dev.dreamplayer.feature.exchange

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import dev.dreamplayer.data.Moved
import dev.dreamplayer.data.MovedKind
import dev.dreamplayer.model.StatusMessage
import dev.dreamplayer.ui.form.FormDropdown
import dev.dreamplayer.ui.nav.Navigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.data.ExportWhat
import dev.dreamplayer.data.Peer
import dev.dreamplayer.data.SyncMode
import dev.dreamplayer.data.SyncTarget
import dev.dreamplayer.data.SyncView
import dev.dreamplayer.ui.ConfirmDialog
import dev.dreamplayer.ui.MessageLog
import dev.dreamplayer.ui.form.FormSwitch
import dev.dreamplayer.ui.form.FormTextField
import dev.dreamplayer.ui.form.InfoLine
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.list.ListRow
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.launch

/**
 * DEVICES (the SYNC tab of the TUI): two-way library sync, receiving a send
 * or sending the selection of the EXPORT tab, over the local network, a
 * Wi-Fi hotspot or USB (a PC with `adb`). The other device runs the matching
 * mode: sync — sync, send — receive. Below — the paired devices.
 */
@Composable
fun SyncTab(core: DreamCore, state: SyncTabState, nav: Navigator) {
    val view by core.sync.view.collectAsStateWithLifecycle()
    val log by core.sync.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // EXPORT / IMPORT hand a mode over.
    LaunchedEffect(Unit) {
        core.sync.preset.collect { p ->
            when (p) {
                null -> return@collect
                is SyncMode.Send -> state.apply { send = p; mode = SyncModeChoice.Send }
                is SyncMode.Receive -> state.apply { force = p.force; mode = SyncModeChoice.Receive }
                SyncMode.Sync -> state.mode = SyncModeChoice.Sync
            }
            core.sync.preset.value = null
        }
    }
    // the transfer must not stop because the screen went off.
    val androidView = LocalView.current
    DisposableEffect(view.running) {
        androidView.keepScreenOn = view.running
        onDispose { androidView.keepScreenOn = false }
    }

    val start = {
        val error = startSync(core, state)
        if (error != null) core.status.warn(error)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) start() else core.status.warn("без разрешения на Wi-Fi точка доступа недоступна")
    }

    var own by remember { mutableStateOf<String?>(null) }
    var peers by remember { mutableStateOf<List<Peer>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    // a session may pair a new device: re-read after it.
    LaunchedEffect(view.running, reload) {
        if (own == null) own = core.sync.ownFingerprint()
        peers = core.sync.paired()
    }
    var renaming by remember { mutableStateOf<Peer?>(null) }
    var forgetting by remember { mutableStateOf<Peer?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        InfoLine("Это устройство", own ?: "ключи недоступны")

        SectionTitle("Режим")
        FormDropdown("Режим", SyncModeChoice.entries, state.mode, { it.title }, { state.mode = it }, enabled = !view.running)
        when (state.mode) {
            SyncModeChoice.Sync -> Hint("обмен библиотеками в обе стороны; на другом устройстве — тоже «синхронизация»")
            SyncModeChoice.Receive -> {
                Hint("принять то, что отправит другое устройство (режим «отправка»)")
                FormSwitch(
                    "Принудительно",
                    state.force,
                    { state.force = it },
                    description = "вернуть и плейлисты, удалённые здесь после их изменения на другом устройстве",
                    enabled = !view.running,
                )
            }
            SyncModeChoice.Send -> Hint(
                state.send?.let { "отправить: ${describe(it)}; на другом устройстве — «приём»" }
                    ?: "отметьте что отправить во вкладке «Экспорт» и нажмите «Отправить на устройство»",
            )
        }
        if (state.mode != SyncModeChoice.Send) {
            FormTextField(
                "Папка в Music/",
                state.subdir,
                { v -> state.subdir = v.replace("..", "").trim('/') },
                supporting = "куда сохраняются полученные песни",
                enabled = !view.running,
            )
        }

        SectionTitle("Подключение")
        FormDropdown(
            "Подключение",
            LinkChoice.entries,
            state.link,
            { it.label },
            { state.link = it },
            hint = { it.hint },
            enabled = !view.running,
        )
        Hint(state.link.hint)
        when (state.link) {
            LinkChoice.Listen, LinkChoice.HotspotHost, LinkChoice.Usb -> {}
            LinkChoice.Connect -> FormTextField(
                "Адрес",
                state.address,
                { state.address = it.trim() },
                placeholder = "192.168.1.5 или 192.168.1.5:47800",
                enabled = !view.running,
            )
            LinkChoice.HotspotJoin -> {
                FormTextField("Сеть (SSID)", state.ssid, { state.ssid = it }, enabled = !view.running)
                FormTextField("Пароль", state.password, { state.password = it }, enabled = !view.running)
            }
        }
        if (state.link != LinkChoice.Connect && state.link != LinkChoice.Usb) {
            FormTextField("Порт", state.port, { v -> state.port = v.filter(Char::isDigit).take(5) }, enabled = !view.running)
        }

        if (view.running) {
            OutlinedButton(onClick = core.sync::cancel, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Отменить") }
        } else {
            Button(
                onClick = {
                    val needed = hotspotPermissions().filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    val hotspot = state.link == LinkChoice.HotspotHost || state.link == LinkChoice.HotspotJoin
                    if (hotspot && needed.isNotEmpty()) permissions.launch(needed.toTypedArray()) else start()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(if (state.mode == SyncModeChoice.Sync) "Синхронизировать" else "Начать") }
        }
        StatusCard(view)
        TransferLines(view, log, open = { m -> openMoved(core, nav, m) })

        SectionTitle("Сопряжённые устройства")
        if (peers.isEmpty()) Hint("пока нет: устройство сопрягается при первом обмене")
        for (p in peers) {
            ListRow(
                title = p.name,
                subtitle = "#${p.id} · ${p.fingerprint}",
                leading = { Icon(Icons.Filled.Devices, contentDescription = null, tint = Dream.colors.muted) },
                menu = listOf(
                    MenuAction("Переименовать", Icons.Filled.Edit) { renaming = p },
                    MenuAction("Забыть", Icons.Filled.Delete, danger = true) { forgetting = p },
                ),
            )
        }
    }

    renaming?.let { p ->
        RenameDialog(p, onDismiss = { renaming = null }) { name ->
            scope.launch { if (core.sync.rename(p, name)) reload++ }
        }
    }
    forgetting?.let { p ->
        ConfirmDialog(
            title = "Забыть ${p.name}?",
            text = "Следующее подключение к нему будет сопряжением заново.",
            confirmLabel = "Забыть",
            onConfirm = { scope.launch { if (core.sync.forget(p)) reload++ } },
            onDismiss = { forgetting = null },
        )
    }
}

/** builds the target and mode from the form and starts; an error to show, null — started. */
private fun startSync(core: DreamCore, state: SyncTabState): String? {
    val port = state.port.toIntOrNull()?.takeIf { it in 1..65535 }
    val needsPort = state.link != LinkChoice.Connect && state.link != LinkChoice.Usb
    if (needsPort && port == null) return "неверный порт"
    val target = when (state.link) {
        LinkChoice.Listen -> SyncTarget.Listen(port!!)
        LinkChoice.Connect -> state.address.takeIf { it.isNotBlank() }?.let(SyncTarget::Connect) ?: return "укажите адрес"
        LinkChoice.HotspotHost -> SyncTarget.HotspotHost(port!!)
        LinkChoice.HotspotJoin -> when {
            state.ssid.isBlank() -> return "укажите имя сети"
            state.password.length < 8 -> return "пароль сети — не меньше 8 символов"
            else -> SyncTarget.HotspotJoin(state.ssid, state.password, port!!)
        }
        LinkChoice.Usb -> SyncTarget.UsbCable
    }
    val mode = when (state.mode) {
        SyncModeChoice.Sync -> SyncMode.Sync
        SyncModeChoice.Receive -> SyncMode.Receive(state.force)
        SyncModeChoice.Send -> state.send ?: return "нечего отправлять: выберите данные во вкладке «Экспорт»"
    }
    if (state.mode != SyncModeChoice.Send && state.subdir.isBlank()) return "укажите папку для песен"
    return if (core.sync.start(target, mode, state.subdir)) null else "обмен уже идёт"
}

/** what the hotspot needs: nearby Wi-Fi devices (13+) or the location (older). */
private fun hotspotPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

/** the state of the session (always shown): what is happening, what to tell the other device. */
@Composable
private fun StatusCard(v: SyncView) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SelectionContainer {
            Column(Modifier.padding(vertical = 8.dp)) {
                InfoLine("Состояние", v.stage)
                if (v.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                v.hotspot?.let {
                    InfoLine("Сеть", it.ssid)
                    InfoLine("Пароль", it.password)
                }
                if (v.addresses.isNotEmpty()) InfoLine("Адрес", v.addresses.joinToString("\n") { a -> "$a :${v.port}" })
                v.link?.let { InfoLine("Подключение", it.describe()) }
            }
        }
    }
}

/**
 * one line per counter (sent / received songs and playlists) and the log;
 * a line unfolds into what moved and when. The entries lead to their tabs
 * once the session is over (until then the library is still changing).
 */
@Composable
private fun TransferLines(v: SyncView, log: List<StatusMessage>, open: (Moved) -> Boolean) {
    val unfolded = remember { mutableStateMapOf<String, Boolean>() }
    val lines = listOf(
        Triple("Отправлено песен", v.sent.songs, v.moved.sentSongs),
        Triple("Отправлено плейлистов", v.sent.playlists, v.moved.sentPlaylists),
        Triple("Получено песен", v.received.songs, v.moved.receivedSongs),
        Triple("Получено плейлистов", v.received.playlists, v.moved.receivedPlaylists),
    )
    for ((label, count, items) in lines) {
        if (count == 0 && items.isEmpty()) continue
        val isOpen = unfolded[label] == true
        FoldLine(label, count.toString(), isOpen) { unfolded[label] = !isOpen }
        if (isOpen) {
            for (m in items) MovedRow(m, clickable = !v.running && m.kind.leadsSomewhere(), open = open)
        }
    }
    if (log.isNotEmpty()) {
        val isOpen = unfolded[LOG_LINE] == true
        FoldLine("Журнал", log.size.toString(), isOpen) { unfolded[LOG_LINE] = !isOpen }
        if (isOpen) MessageLog(log)
    }
}

private const val LOG_LINE = "log"

private fun MovedKind.leadsSomewhere() = this == MovedKind.Song || this == MovedKind.Playlist

@Composable
private fun FoldLine(label: String, value: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Dream.colors.playing, maxLines = 1)
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (open) "Свернуть" else "Развернуть",
            tint = Dream.colors.muted,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun MovedRow(m: Moved, clickable: Boolean, open: (Moved) -> Boolean) {
    val title = when (m.kind) {
        MovedKind.DeletedPlaylist -> "удалён: ${m.title}"
        else -> m.title
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable) { open(m) }
            .padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(clock(m.at), style = MaterialTheme.typography.bodySmall, color = Dream.colors.muted, fontFamily = FontFamily.Monospace)
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = if (clickable) MaterialTheme.colorScheme.onSurface else Dream.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun clock(at: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))

/** the tab of a moved song / playlist; false — it is not in the library (any more). */
private fun openMoved(core: DreamCore, nav: Navigator, m: Moved): Boolean {
    val lib = core.library.library.value
    return when (m.kind) {
        MovedKind.Song -> lib.pool.tracks.firstOrNull { it.id == m.trackId }?.let { nav.openSong(it); true }
        MovedKind.Playlist -> lib.playlists.firstOrNull { it.name == m.title }?.let { nav.openPlaylist(it.name); true }
        else -> null
    } ?: run {
        core.status.warn("«${m.title}» больше нет в библиотеке")
        false
    }
}

private fun describe(send: SyncMode.Send): String {
    val what = when (val w = send.what) {
        ExportWhat.Library -> "вся библиотека"
        is ExportWhat.Playlists -> "плейлистов: ${w.names.size}"
        is ExportWhat.Tracks -> "треков: ${w.ids.size}"
    }
    val f = send.flags
    fun yes(b: Boolean) = if (b) "да" else "нет"
    return "$what (аудио: ${yes(f.audio)}, имена: ${yes(f.names)}, обложки: ${yes(f.covers)})"
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Dream.colors.muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun RenameDialog(peer: Peer, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // selected: typing replaces the old name.
    var name by remember { mutableStateOf(TextFieldValue(peer.name, TextRange(0, peer.name.length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Имя устройства") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                Text(
                    peer.fingerprint,
                    fontFamily = FontFamily.Monospace,
                    color = Dream.colors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.text.isNotBlank(), onClick = { onDismiss(); onConfirm(name.text.trim()) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
