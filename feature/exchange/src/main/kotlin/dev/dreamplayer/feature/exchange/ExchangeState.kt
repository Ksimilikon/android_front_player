package dev.dreamplayer.feature.exchange

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dreamplayer.data.DEFAULT_SYNC_PORT
import dev.dreamplayer.data.SyncMode
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage

/** what the EXPORT tab packs. */
enum class ExportMode(val label: String) { Library("библиотека"), Playlists("плейлисты"), Tracks("треки") }

/** an EXPORT tab; owned by the tab host (survives leaving the screen). */
class ExportState {
    var mode by mutableStateOf(ExportMode.Library)
    var audio by mutableStateOf(true)
    var names by mutableStateOf(true)
    var covers by mutableStateOf(true)
    /** marked playlists (by name) or tracks (by id) — depending on [mode]. */
    val checkedPlaylists = mutableStateListOf<String>()
    val checkedTracks = mutableStateListOf<Long>()
    var running by mutableStateOf(false)
    val log = mutableStateListOf(StatusMessage("выберите что экспортировать и нажмите «Экспортировать»", StatusKind.Info))
}

/** an IMPORT tab. */
class ImportState {
    var packet by mutableStateOf<Uri?>(null)
    var packetName by mutableStateOf<String?>(null)
    var force by mutableStateOf(false)
    /** the folder under Music/ the songs are saved into. */
    var subdir by mutableStateOf("DreamPlayer")
    var running by mutableStateOf(false)
    val log = mutableStateListOf(StatusMessage("выберите файл пакета (.dplr) и нажмите «Импортировать»", StatusKind.Info))
}

/** what the DEVICES tab runs. */
enum class SyncModeChoice(val label: String) { Sync("синхронизация"), Receive("приём"), Send("отправка") }

/** how the DEVICES tab reaches the other device. */
enum class LinkChoice(val label: String, val hint: String) {
    Listen("Ждать подключения", "другое устройство подключится к этому по сети"),
    Connect("Подключиться по адресу", "к устройству, которое ждёт подключения"),
    HotspotHost("Создать точку доступа", "телефон раздаст свою Wi-Fi сеть (без интернета)"),
    HotspotJoin("Подключиться к точке доступа", "сеть и пароль показывает другое устройство"),
    Usb("USB-кабель", "обмен с компьютером по кабелю, без режима отладки"),
}

/** the DEVICES tab (library sync / transfer with another device). */
class SyncTabState {
    var mode by mutableStateOf(SyncModeChoice.Sync)
    /** what to send — handed over by the EXPORT tab. */
    var send by mutableStateOf<SyncMode.Send?>(null)
    var force by mutableStateOf(false)
    var link by mutableStateOf(LinkChoice.Listen)
    var address by mutableStateOf("")
    var ssid by mutableStateOf("")
    var password by mutableStateOf("")
    var port by mutableStateOf(DEFAULT_SYNC_PORT.toString())
    /** the folder under Music/ received songs are saved into. */
    var subdir by mutableStateOf("DreamPlayer")
}
