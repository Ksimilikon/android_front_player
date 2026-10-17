package dev.dreamplayer.feature.exchange

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
