package dev.dreamplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.TrackColor

/** the palette: dark, green accent; red — errors, yellow — warnings. */
object Palette {
    val Background = Color(0xFF0D100E)
    val Surface = Color(0xFF151A17)
    val SurfaceHigh = Color(0xFF1D2420)
    val SurfaceHighest = Color(0xFF262E29)
    val Outline = Color(0xFF34403A)
    val Text = Color(0xFFE6ECE8)
    val TextMuted = Color(0xFF8E9A93)
    val Green = Color(0xFF3DDC84)
    val GreenDim = Color(0xFF1F5E3C)
    val Red = Color(0xFFFF5F56)
    val Yellow = Color(0xFFFFCC33)
}

/** colors beyond the Material scheme (the TUI status colors). */
@Immutable
data class DreamColors(
    val playing: Color = Palette.Green,
    val ok: Color = Palette.Green,
    val warn: Color = Palette.Yellow,
    val error: Color = Palette.Red,
    val muted: Color = Palette.TextMuted,
) {
    fun of(kind: StatusKind): Color = when (kind) {
        StatusKind.Info -> Palette.Text
        StatusKind.Ok -> ok
        StatusKind.Warn -> warn
        StatusKind.Error -> error
    }
}

private val LocalDreamColors = staticCompositionLocalOf { DreamColors() }

object Dream {
    val colors: DreamColors
        @Composable @ReadOnlyComposable get() = LocalDreamColors.current
}

private val scheme = darkColorScheme(
    primary = Palette.Green,
    onPrimary = Color(0xFF00210F),
    primaryContainer = Palette.GreenDim,
    onPrimaryContainer = Color(0xFFB8F5CF),
    secondary = Palette.Green,
    onSecondary = Color(0xFF00210F),
    secondaryContainer = Palette.SurfaceHighest,
    onSecondaryContainer = Palette.Text,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHighest,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Red,
    onError = Color.Black,
)

@Composable
fun DreamTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDreamColors provides DreamColors()) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** display color of a track color mark. */
fun TrackColor.display(): Color = when (this) {
    TrackColor.Red -> Color(0xFFFF5F56)
    TrackColor.Pink -> Color(0xFFFF7AB6)
    TrackColor.Orange -> Color(0xFFFF9F43)
    TrackColor.Green -> Color(0xFF3DDC84)
    TrackColor.Blue -> Color(0xFF4D9DFF)
    TrackColor.Cyan -> Color(0xFF3CD6E0)
    TrackColor.Purple -> Color(0xFFB57CFF)
}
