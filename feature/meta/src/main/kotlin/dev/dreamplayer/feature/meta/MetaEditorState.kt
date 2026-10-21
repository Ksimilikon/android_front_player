package dev.dreamplayer.feature.meta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dreamplayer.common.splitList
import dev.dreamplayer.model.MetaEdit
import dev.dreamplayer.model.Track
import dev.dreamplayer.model.TrackColor

/**
 * the fields of the metadata editor, prefilled from [track]. Only tags and
 * user state are editable; technical fields are shown read-only.
 */
class MetaEditorState(val track: Track) {
    var title by mutableStateOf(track.title)
    var artists by mutableStateOf(track.artistsLine)
    var album by mutableStateOf(track.album.orEmpty())
    var genres by mutableStateOf(track.genres.joinToString(", "))
    /** a newly chosen cover (already copied into the app); null — unchanged. */
    var newCover by mutableStateOf<String?>(null)
    /** drop the separate cover file (the one embedded in the audio file stays). */
    var removeCover by mutableStateOf(false)
    /** on save, also embed [cover] into the audio file. */
    var embedCover by mutableStateOf(false)
    var color by mutableStateOf(track.color)
    var label by mutableStateOf(track.label.orEmpty())

    /** the separate cover file as it will be after saving (null — none). */
    val cover: String? get() = if (removeCover) null else newCover ?: track.coverArt

    fun pickCover(path: String) {
        newCover = path
        removeCover = false
    }

    fun dropCover() {
        newCover = null
        removeCover = true
        embedCover = false
    }

    /**
     * the edit set (as `to_edit` in the TUI): an empty title / artists means
     * "don't change"; album, genres and label are always applied (empty clears).
     */
    fun toEdit(): MetaEdit = MetaEdit(
        id = track.id ?: -1,
        title = title.trim().ifEmpty { null },
        artists = splitList(artists).ifEmpty { null },
        album = album.trim(),
        genres = splitList(genres),
        coverPath = newCover,
        clearCover = removeCover && track.coverArt != null,
        setColor = color != track.color,
        color = color,
        label = label.trim(),
    )

    companion object {
        val COLORS: List<TrackColor?> = listOf(null) + TrackColor.entries
    }
}
