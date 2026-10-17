package dev.dreamplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import dev.dreamplayer.covers.CoverLoader
import dev.dreamplayer.ui.theme.Dream

/**
 * a track cover: the separate cover file, otherwise the one embedded in the
 * audio file; a note icon when there is none. Loaded off the main thread.
 */
@Composable
fun CoverImage(
    coverPath: String?,
    audioPath: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12),
) {
    BoxWithConstraints(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceAtLeast(64)
        var result by remember(coverPath, audioPath, px) { mutableStateOf(CoverLoader.peek(coverPath, audioPath, px)) }
        LaunchedEffect(coverPath, audioPath, px) {
            if (result == null) result = CoverLoader.load(coverPath, audioPath, px)
        }
        val bmp = result?.bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Dream.colors.muted,
                modifier = Modifier.fillMaxWidth().padding(maxWidth / 5).align(Alignment.Center),
            )
        }
    }
}
