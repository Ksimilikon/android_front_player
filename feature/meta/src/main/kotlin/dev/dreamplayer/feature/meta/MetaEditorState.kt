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

    private val initial = listOf(title, artists, album, genres)

    /**
     * the edit set (as `to_edit` in the TUI): an unchanged tag is not sent
     * (so the song file isn't rewritten for nothing); an empty title /
     * artists means "don't change", an emptied album / genres clears them.
     * The label is always applied (empty clears).
     */
    fun toEdit(): MetaEdit = MetaEdit(
        id = track.id ?: -1,
        title = changed(0, title)?.trim()?.ifEmpty { null },
        artists = changed(1, artists)?.let(::splitList)?.ifEmpty { null },
        album = changed(2, album)?.trim(),
        genres = changed(3, genres)?.let(::splitList),
        coverPath = newCover,
        clearCover = removeCover && track.coverArt != null,
        setColor = color != track.color,
        color = color,
        label = label.trim(),
    )

    private fun changed(i: Int, text: String): String? = text.takeIf { it != initial[i] }

    companion object {
        val COLORS: List<TrackColor?> = listOf(null) + TrackColor.entries
    }
}
