package dev.dreamplayer.feature.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.formatDuration
import dev.dreamplayer.common.percent
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Library
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.form.InfoLine
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.player.VerticalSlider
import dev.dreamplayer.ui.theme.Dream
import dev.dreamplayer.ui.theme.display

/**
 * one song: the cover up close with the per-track volume slider on its left
 * (the height of the cover), playback progress if this song is playing, all
 * metadata and the button to edit it.
 */
@Composable
fun SongTab(core: DreamCore, nav: Navigator, track: Track) {
    val library by core.library.library.collectAsStateWithLifecycle()
    val playback by core.player.playback.collectAsStateWithLifecycle()
    // a fresh copy after edits (by id), the passed one otherwise.
    val t = track.id?.let(library::track) ?: track
    val current = playback.isCurrent(t)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
        CoverWithVolume(core, t, current, playback.trackVolume)

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (t.artists.isNotEmpty()) {
                Text(t.artistsLine, style = MaterialTheme.typography.titleMedium, color = Dream.colors.muted, textAlign = TextAlign.Center)
            }
            if (t.invalid) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Dream.colors.warn, modifier = Modifier.size(16.dp))
                    Text(" файл недоступен", color = Dream.colors.warn, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (current) {
            Progress(core, playback.positionS, playback.durationS.takeIf { it > 0 } ?: t.durationS, playback.isPaused)
        } else {
            PlayHere(core, library, t, playback.queue?.tracks)
        }

        SectionTitle("Метаданные")
        InfoLine("Альбом", t.album ?: "—")
        InfoLine("Жанры", t.genres.joinToString(", ").ifEmpty { "—" })
        InfoLine("Метка", t.label ?: "—")
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoLine("Цвет", t.color?.id ?: "—", Modifier.weight(1f))
            t.color?.let { Box(Modifier.padding(end = 16.dp).size(14.dp).background(it.display(), CircleShape)) }
        }
        SectionTitle("Файл")
        InfoLine("Длительность", formatDuration(t.durationS))
        InfoLine("Битрейт", "${t.bitrate} kbps")
        InfoLine("Частота", "${t.sampleRate} Hz")
        InfoLine("Прослушиваний", t.listenCount.toString())
        InfoLine("Путь", t.path ?: "—")
        InfoLine("Обложка", t.coverArt ?: if (t.path != null) "встроенная / нет" else "—")

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        OutlinedButton(
            onClick = { nav.openEditMeta(t) },
            enabled = t.id != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Изменить метаданные")
        }
    }
}

/** the cover (a square up to 360dp) and the volume slider of the same height to its left. */
@Composable
private fun CoverWithVolume(core: DreamCore, t: Track, current: Boolean, engineVolume: Float) {
    // while dragging the local value leads; otherwise the engine / db value.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val volume = dragging ?: if (current) engineVolume else t.volume
    BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
        val cover = minOf(maxWidth - 56.dp, 360.dp)
        Row(
            Modifier.fillMaxWidth().height(cover),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.height(cover), horizontalAlignment = Alignment.CenterHorizontally) {
                VerticalSlider(
                    value = volume,
                    onValueChange = {
                        dragging = it
                        core.player.setTrackVolume(t, it, persist = false)
                    },
                    onValueChangeFinished = {
                        dragging?.let { core.player.setTrackVolume(t, it, persist = true) }
                        dragging = null
                    },
                    range = Track.VOLUME_MIN..Track.VOLUME_MAX,
                    marker = 1f,
                    enabled = t.id != null || current,
                    modifier = Modifier.weight(1f),
                )
                Text(percent(volume), style = MaterialTheme.typography.labelSmall, color = Dream.colors.muted)
            }
            Spacer(Modifier.size(16.dp))
            CoverImage(t.coverArt, t.path, Modifier.size(cover), shape = RoundedCornerShape(16.dp))
        }
    }
}

/** progress with seeking + play / pause (the song is the playing one). */
@Composable
private fun Progress(core: DreamCore, positionS: Long, durationS: Long, isPaused: Boolean) {
    var seeking by remember { mutableStateOf<Float?>(null) }
    val pos = seeking ?: positionS.toFloat()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Slider(
            value = pos.coerceIn(0f, durationS.coerceAtLeast(1).toFloat()),
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                seeking?.let { core.player.seek(it.toLong()) }
                seeking = null
            },
            valueRange = 0f..durationS.coerceAtLeast(1).toFloat(),
            enabled = durationS > 0,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(pos.toLong()), style = MaterialTheme.typography.labelMedium, color = Dream.colors.muted)
            Spacer(Modifier.weight(1f))
            Button(onClick = { core.player.playPause() }) {
                Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationS), style = MaterialTheme.typography.labelMedium, color = Dream.colors.muted)
        }
    }
}

/**
 * the song isn't playing: play it — inside the current queue if it's there,
 * otherwise from the pool.
 */
@Composable
private fun PlayHere(core: DreamCore, library: Library, t: Track, queue: List<Track>?) {
    Button(
        onClick = {
            val inQueue = queue?.indexOfFirst { it.key == t.key } ?: -1
            if (inQueue >= 0) {
                core.player.select(inQueue)
            } else {
                val i = library.pool.tracks.indexOfFirst { it.key == t.key }
                if (i >= 0) core.player.playPlaylist(library.pool.name, i)
            }
        },
        enabled = !t.invalid,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Воспроизвести")
    }
}
