package dev.dreamplayer.ui.nav

import dev.dreamplayer.model.Track

/**
 * what a tab may ask the tab host to do (the `TabEvent`s of the TUI that
 * concern navigation). Implemented by the app.
 */
interface Navigator {
    /** the tab of a playlist (opening doesn't start playback). */
    fun openQueue(playlistName: String)

    /** the song tab for [track] (live progress if it is the playing one). */
    fun openSong(track: Track)

    fun openNewPlaylist()
    fun openEditPlaylist(name: String)
    fun openEditMeta(track: Track)
    fun openImport()
    fun openExport()

    /** the log of the core calls, events and messages. */
    fun openLogs()

    /** library sync / transfer with other devices and the paired devices. */
    fun openSync()

    /** songs that are in no playlist, to delete from the device. */
    fun openOrphans()

    /** closes the current transient tab (editor cancel / save). */
    fun closeCurrent()
}
