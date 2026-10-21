package dev.dreamplayer.feature.playlists

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Playlist
import dev.dreamplayer.ui.ConfirmDialog
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.list.DreamList
import dev.dreamplayer.ui.list.ListHeader
import dev.dreamplayer.ui.list.ListRow
import dev.dreamplayer.ui.list.MenuAction
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** the start tab: every playlist; the one playing is highlighted. */
@Composable
fun PlaylistsTab(core: DreamCore, nav: Navigator) {
    val library by core.library.library.collectAsStateWithLifecycle()
    val playback by core.player.playback.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    val playlists = library.all

    DreamList(
        items = playlists,
        key = { _, p -> (if (p.isPool) "pool:" else "pl:") + p.name },
        header = {
            ListHeader(
                title = "Плейлисты",
                subtitle = "${library.playlists.size} плейлистов · ${library.pool.tracks.size} песен",
            ) {
                IconButton(onClick = nav::openNewPlaylist) { Icon(Icons.Filled.Add, contentDescription = "Новый плейлист") }
            }
        },
        emptyText = "библиотека пуста",
    ) { _, p ->
        val playing = playback.queue?.name == p.name
        ListRow(
            title = p.name,
            subtitle = subtitle(p),
            highlighted = playing,
            leading = {
                val first = p.tracks.firstOrNull()
                if (p.isPool) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Dream.colors.playing, modifier = Modifier.size(40.dp))
                } else {
                    CoverImage(p.coverArt ?: first?.coverArt, first?.path, Modifier.size(40.dp), shape = RoundedCornerShape(6.dp))
                }
            },
            menu = listOf(
                MenuAction("Открыть", Icons.AutoMirrored.Filled.QueueMusic) { nav.openPlaylist(p.name) },
                MenuAction("Воспроизвести", Icons.Filled.PlayArrow, enabled = p.tracks.isNotEmpty()) {
                    core.player.playPlaylist(p.name, 0)
                    nav.openPlaying()
                },
                MenuAction("Изменить", Icons.Filled.Edit, enabled = !p.isPool) { nav.openEditPlaylist(p.name) },
                MenuAction("Удалить", Icons.Filled.Delete, danger = true, enabled = !p.isPool) { deleting = p },
            ),
            onClick = { nav.openPlaylist(p.name) },
        )
    }

    deleting?.let { p ->
        ConfirmDialog(
            title = "Удалить плейлист?",
            text = "«${p.name}» будет удалён из библиотеки. Песни останутся.",
            confirmLabel = "Удалить",
            onConfirm = { core.launch { core.library.removePlaylist(p.name) } },
            onDismiss = { deleting = null },
        )
    }
}

private fun subtitle(p: Playlist): String {
    val count = "${p.tracks.size} песен"
    val updated = p.updatedAt?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it * 1000)) }
    return if (updated != null) "$count · изменён $updated" else count
}
