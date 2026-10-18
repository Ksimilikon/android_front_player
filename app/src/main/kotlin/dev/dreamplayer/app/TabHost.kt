package dev.dreamplayer.app

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.feature.editor.PlaylistEditorState
import dev.dreamplayer.feature.exchange.ExportState
import dev.dreamplayer.feature.exchange.ImportState
import dev.dreamplayer.feature.exchange.LinkChoice
import dev.dreamplayer.feature.exchange.SyncTabState
import dev.dreamplayer.feature.meta.MetaEditorState
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.nav.TabTitle

/** an open tab. [id] identifies it: opening an already open id switches to it. */
sealed class TabSpec(val id: String, val title: String, val closable: Boolean) {
    data object Playlists : TabSpec("playlists", "Плейлисты", closable = false)

    /** the opened playlist (one tab, its content is replaced). */
    class Queue(val name: String) : TabSpec("queue", name, closable = true)

    /** one song: opened only from the player bar / top strip / "Подробнее". */
    class Song(val track: Track) : TabSpec("song", "Песня", closable = true)

    class NewPlaylist(val state: PlaylistEditorState) : TabSpec("new-playlist", "Новый плейлист", closable = true)
    class EditPlaylist(name: String, val state: PlaylistEditorState) : TabSpec("edit:$name", "Изм: $name", closable = true)
    class EditMeta(track: Track, val state: MetaEditorState) : TabSpec("meta:${track.id}", "Мета: ${track.title.take(12)}", closable = true)
    class Import(val state: ImportState) : TabSpec("import", "Импорт", closable = true)
    class Export(val state: ExportState) : TabSpec("export", "Экспорт", closable = true)
    data object Logs : TabSpec("logs", "Логи", closable = true)
    class Sync(val state: SyncTabState) : TabSpec("sync", "Устройства", closable = true)
    data object Orphans : TabSpec("orphans", "Вне плейлистов", closable = true)
}

/**
 * the open tabs and the current one (the TUI `screens` + `current`), also the
 * [Navigator] the tabs talk to. Order: PLAYLISTS, the playlist tab, other
 * transient tabs, and the song tab always last — it closes itself as soon
 * as another tab becomes current, and being last its removal never shifts the
 * index of the current tab.
 */
class TabHost(private val core: DreamCore) : Navigator {
    val tabs = mutableStateListOf<TabSpec>(TabSpec.Playlists)
    var currentId by mutableStateOf(TabSpec.Playlists.id)
        private set

    /** where to return when a transient tab is closed: tab id -> the tab it was opened from. */
    private val origin = mutableMapOf<String, String>()

    val currentIndex: Int by derivedStateOf { tabs.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
    val current: TabSpec get() = tabs[currentIndex]
    val titles: List<TabTitle> get() = tabs.map { TabTitle(it.title, it.closable) }

    fun select(index: Int) {
        tabs.getOrNull(index)?.let { currentId = it.id }
    }

    /** the pager settled on [index] (a swipe or the end of a scroll). */
    fun onSettled(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        currentId = tab.id
        if (tab !is TabSpec.Song) {
            val song = tabs.indexOfFirst { it is TabSpec.Song }
            if (song >= 0) tabs.removeAt(song)
        }
    }

    fun close(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        if (!tab.closable) return
        if (tab.id == currentId) {
            val back = origin[tab.id]?.takeIf { id -> id != tab.id && tabs.any { it.id == id } }
            currentId = back ?: tabs[(index - 1).coerceAtLeast(0)].id
        }
        origin.remove(tab.id)
        tabs.removeAt(index)
    }

    /** system back: close a transient tab, otherwise return to PLAYLISTS. false — nothing to do. */
    fun back(): Boolean = when (val tab = current) {
        TabSpec.Playlists -> false
        is TabSpec.Queue -> true.also { currentId = TabSpec.Playlists.id }
        else -> true.also { close(tabs.indexOf(tab)) }
    }

    private fun open(spec: TabSpec) {
        val from = currentId
        val existing = tabs.indexOfFirst { it.id == spec.id }
        when {
            // same id: the content may differ (another playlist / song) — replace.
            existing >= 0 -> tabs[existing] = spec
            spec is TabSpec.Song -> tabs.add(spec)
            spec is TabSpec.Queue -> tabs.add(1, spec)
            else -> {
                val song = tabs.indexOfFirst { it is TabSpec.Song }
                if (song >= 0) tabs.add(song, spec) else tabs.add(spec)
            }
        }
        if (from != spec.id) origin[spec.id] = from
        currentId = spec.id
    }

    // ───────────────────────── Navigator ─────────────────────────

    override fun openQueue(playlistName: String) = open(TabSpec.Queue(playlistName))

    override fun openSong(track: Track) = open(TabSpec.Song(track))

    override fun openNewPlaylist() = open(TabSpec.NewPlaylist(PlaylistEditorState(null, emptyList())))

    override fun openEditPlaylist(name: String) {
        val pl = core.library.library.value.find(name)
        if (pl == null || pl.isPool) {
            core.status.warn("этот плейлист нельзя изменить")
            return
        }
        // an editor that's already open keeps its unsaved edits.
        if (tabs.any { it.id == "edit:$name" }) {
            currentId = "edit:$name"
            return
        }
        open(TabSpec.EditPlaylist(name, PlaylistEditorState(name, pl.tracks)))
    }

    override fun openEditMeta(track: Track) {
        if (track.id == null) {
            core.status.warn("трек не из библиотеки")
            return
        }
        if (tabs.any { it.id == "meta:${track.id}" }) {
            currentId = "meta:${track.id}"
            return
        }
        open(TabSpec.EditMeta(track, MetaEditorState(track)))
    }

    override fun openImport() {
        if (tabs.none { it is TabSpec.Import }) open(TabSpec.Import(ImportState())) else currentId = "import"
    }

    override fun openExport() {
        if (tabs.none { it is TabSpec.Export }) open(TabSpec.Export(ExportState())) else currentId = "export"
    }

    override fun openLogs() = open(TabSpec.Logs)

    override fun openOrphans() = open(TabSpec.Orphans)

    override fun openSync() {
        // an open tab keeps its form.
        if (tabs.none { it is TabSpec.Sync }) open(TabSpec.Sync(SyncTabState())) else currentId = "sync"
    }

    /** the computer made the phone its USB accessory: the DEVICES tab, ready for a USB sync. */
    fun openSyncUsb() {
        openSync()
        (tabs.firstOrNull { it is TabSpec.Sync } as? TabSpec.Sync)?.state?.link = LinkChoice.Usb
    }

    override fun closeCurrent() = close(currentIndex)
}
