package dev.dreamplayer.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dreamplayer.common.formatDuration
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.CoverImage
import dev.dreamplayer.ui.theme.Dream
import dev.dreamplayer.ui.theme.display

/** a track in any list: position, cover, title / artists, color mark, duration. */
@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    number: Int? = null,
    playing: Boolean = false,
    selected: Boolean = false,
    leadingExtra: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    menu: List<MenuAction> = emptyList(),
    menuOpen: MutableState<Boolean>? = null,
    gestures: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    ListRow(
        title = track.title,
        subtitle = listOfNotNull(track.artistsLine.ifEmpty { null }, track.label).joinToString(" · "),
        modifier = modifier,
        highlighted = playing,
        selected = selected,
        dimmed = track.invalid,
        menu = menu,
        menuOpen = menuOpen,
        gestures = gestures,
        onClick = onClick,
        leading = {
            leadingExtra?.invoke()
            if (number != null) {
                Text(
                    "$number",
                    modifier = Modifier.widthIn(min = 24.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (playing) Dream.colors.playing else Dream.colors.muted,
                    textAlign = TextAlign.End,
                )
            }
            CoverImage(track.coverArt, track.path, Modifier.size(40.dp), shape = RoundedCornerShape(6.dp))
        },
        trailing = trailing ?: {
            Box(contentAlignment = Alignment.CenterEnd) {
                track.color?.let {
                    Box(Modifier.size(10.dp).background(it.display(), CircleShape))
                }
            }
            if (track.durationS > 0) {
                Text(formatDuration(track.durationS), style = MaterialTheme.typography.labelMedium, color = Dream.colors.muted)
            }
        },
    )
}
