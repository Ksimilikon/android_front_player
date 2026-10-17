package dev.dreamplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dreamplayer.common.formatDuration
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.theme.Dream

/**
 * the bottom playback panel: previous | track + progress | play/pause | next.
 * Tapping the middle opens the song tab.
 */
@Composable
fun PlayerBar(
    track: Track?,
    isPaused: Boolean,
    positionS: Long,
    durationS: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = track != null
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = enabled) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущая")
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = enabled, onClick = onOpen)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    track?.title ?: "Выберите песню",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Dream.colors.muted,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (durationS > 0) (positionS.toFloat() / durationS).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.weight(1f),
                        drawStopIndicator = {},
                    )
                    Text(
                        if (enabled) formatDuration(positionS) else "0:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dream.colors.muted,
                    )
                }
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = "Пауза / воспроизведение")
            }
            IconButton(onClick = onNext, enabled = enabled) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Следующая")
            }
        }
    }
}
