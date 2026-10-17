package dev.dreamplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.theme.Dream

/**
 * the small strip at the very top: cover, title and artists of the playing
 * track. Always present; without a playing track it shows a message and isn't
 * clickable.
 */
@Composable
fun NowPlayingStrip(track: Track?, isPaused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .then(if (track != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (track == null) {
                Text("Ничего не играет", style = MaterialTheme.typography.labelLarge, color = Dream.colors.muted)
                return@Row
            }
            CoverImage(track.coverArt, track.path, Modifier.size(30.dp), shape = RoundedCornerShape(4.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.artists.isNotEmpty()) {
                    Text(
                        track.artistsLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = Dream.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (isPaused) Icons.Filled.Pause else Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = if (isPaused) Dream.colors.muted else Dream.colors.playing,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
