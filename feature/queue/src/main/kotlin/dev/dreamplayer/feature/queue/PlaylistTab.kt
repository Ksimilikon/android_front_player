package dev.dreamplayer.feature.queue

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.filterTracks
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.ConfirmDialog
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.form.SearchBox
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.ListHeader
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.list.TrackRow
import dev.dreamplayer.ui.nav.Navigator

/**
 * a playlist as it is in the library (its saved order). Opening doesn't start
 * playback: a tap on a track plays the playlist from it and goes to the
 * queue. A tap on the cover picks a new one.
 */
@Composable
fun PlaylistTab(core: DreamCore, nav: Navigator, playlistName: String) {
    val library by core.library.library.collectAsStateWithLifecycle()
    val playback by core.player.playback.collectAsStateWithLifecycle()
    val playlist = library.find(playlistName)
    val tracks = playlist?.tracks.orEmpty()
    val isPool = playlist?.isPool ?: false
    val playingHere = playback.queue?.name == playlistName

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<Int?>(null) }
    val deleteFromDevice = rememberDeviceDelete(core)
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            core.launch {
                val path = core.covers.importCover(uri)
                if (path != null) core.library.setPlaylistCover(playlistName, path) else core.status.error("не удалось скопировать обложку")
            }
        }
    }

    val view = if (searching) filterTracks(tracks, query) else tracks.indices.toList()

    Column(Modifier.fillMaxSize()) {
        DreamList(
            items = view,
            key = { _, i -> "$i:${tracks[i].id ?: tracks[i].path}" },
            header = {
                Column {
                    ListHeader(
                        title = playlistName,
                        subtitle = buildString {
                            append("${tracks.size} песен")
                            if (playingHere) append(" · играет")
                            if (playlist == null) append(" · плейлист не найден")
                        },
                        leading = if (playlist != null && !isPool) {
                            {
                                CoverImage(
                                    playlist.coverArt ?: tracks.firstOrNull()?.coverArt,
                                    tracks.firstOrNull()?.path,
                                    Modifier.size(40.dp).clickable {
                                        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                )
                            }
                        } else {
                            null
                        },
                    ) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                        if (!isPool && playlist != null) {
                            IconButton(onClick = { nav.openEditPlaylist(playlistName) }) {
                                Icon(Icons.Filled.Tune, contentDescription = "Изменить плейлист")
                            }
                        }
                        IconButton(onClick = { core.player.playPlaylist(playlistName, 0); nav.openPlaying() }, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Воспроизвести с начала")
                        }
                    }
                    if (searching) SearchBox(query, { query = it })
                }
            },
            emptyText = if (searching) "ничего не найдено" else "в плейлисте нет песен",
        ) { _, i ->
            val t = tracks[i]
            TrackRow(
                track = t,
                number = i + 1,
                playing = playingHere && playback.isCurrent(t),
                menu = buildList {
                    addAll(trackInfoActions(nav, t))
                    if (!isPool) add(MenuAction("Удалить из плейлиста", Icons.Filled.Delete, danger = true) { removing = i })
                    // the pool is the whole library: here a song can go from the device itself.
                    if (isPool) {
                        add(MenuAction("Удалить с устройства", Icons.Filled.DeleteForever, danger = true, enabled = t.path != null) {
                            deleteFromDevice(listOf(t))
                        })
                    }
                },
                onClick = {
                    // the playing track: pause / resume instead of starting it over.
                    if (playingHere && playback.isCurrent(t)) {
                        core.player.playPause()
                    } else {
                        core.player.playPlaylist(playlistName, i)
                        nav.openPlaying()
                    }
                },
            )
        }
    }

    removing?.let { i ->
        ConfirmDialog(
            title = "Удалить из плейлиста?",
            text = "«${tracks.getOrNull(i)?.title}» будет удалён из «$playlistName» в библиотеке.",
            confirmLabel = "Удалить",
            onConfirm = {
                val ids = tracks.filterIndexed { j, _ -> j != i }.mapNotNull(Track::id)
                core.launch { core.library.savePlaylist(playlistName, ids) }
            },
            onDismiss = { removing = null },
        )
    }
}

/** the menu items every track list has: details and the metadata editor. */
internal fun trackInfoActions(nav: Navigator, t: Track): List<MenuAction> = listOf(
    MenuAction("Подробнее", Icons.Filled.Info) { nav.openSong(t) },
    MenuAction("Изменить метаданные", Icons.Filled.Edit, enabled = t.id != null) { nav.openEditMeta(t) },
)
