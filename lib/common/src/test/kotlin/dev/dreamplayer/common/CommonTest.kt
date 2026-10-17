package dev.dreamplayer.common

import dev.dreamplayer.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommonTest {
    private fun track(title: String, artist: String, path: String) = Track(
        id = null, path = path, title = title, artists = listOf(artist), album = null,
        genres = emptyList(), durationS = 0, sampleRate = 0, bitrate = 320, listenCount = 0,
        coverArt = null, volume = 1f, invalid = false, color = null, label = null,
    )

    @Test
    fun searchMatchesAllWordsCaseInsensitive() {
        val tracks = listOf(
            track("Song A", "Queen", "/music/rock/a.flac"),
            track("Other", "ABBA", "/music/pop/b.mp3"),
        )
        assertEquals(listOf(0), filterTracks(tracks, "queen"))
        assertEquals(listOf(1), filterTracks(tracks, "POP"))
        assertEquals(listOf(1), filterTracks(tracks, "music abba"))
        assertEquals(listOf(0, 1), filterTracks(tracks, ""))
        assertEquals(emptyList(), filterTracks(tracks, "320"))
    }

    @Test
    fun targetIndexClamps() {
        assertEquals(0, targetIndex("1", 5))
        assertEquals(4, targetIndex("99", 5))
        assertEquals(0, targetIndex("0", 5))
        assertNull(targetIndex("x", 5))
        assertNull(targetIndex("1", 0))
    }

    @Test
    fun movedKeepsOthers() {
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).moved(1, 0))
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).moved(5, 0))
    }

    @Test
    fun durations() {
        assertEquals("1:23", formatDuration(83))
        assertEquals("1:02:03", formatDuration(3723))
    }
}
