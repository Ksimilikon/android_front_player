package dev.dreamplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.feature.editor.PlaylistEditorTab
import dev.dreamplayer.feature.exchange.ExportTab
import dev.dreamplayer.feature.exchange.ImportTab
import dev.dreamplayer.feature.exchange.SyncTab
import dev.dreamplayer.feature.logs.LogsTab
import dev.dreamplayer.feature.meta.MetaEditorTab
import dev.dreamplayer.feature.playlists.PlaylistsTab
import dev.dreamplayer.feature.queue.OrphansTab
import dev.dreamplayer.feature.queue.PlayingTab
import dev.dreamplayer.feature.queue.PlaylistTab
import dev.dreamplayer.feature.settings.AboutTab
import dev.dreamplayer.feature.settings.SettingsSheet
import dev.dreamplayer.feature.settings.SettingsTab
import dev.dreamplayer.feature.song.SongTab
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import dev.dreamplayer.ui.AlertBanner
import dev.dreamplayer.ui.StatusBanner
import dev.dreamplayer.ui.nav.DreamTabRow
import dev.dreamplayer.ui.nav.Editing
import dev.dreamplayer.ui.nav.PullAction
import dev.dreamplayer.ui.nav.PullKind
import dev.dreamplayer.ui.player.NowPlayingStrip
import dev.dreamplayer.ui.player.PlayerBar
import dev.dreamplayer.ui.player.PlayerBarMode
import kotlin.math.abs
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * the frame (as `App::draw` in the TUI): the now-playing strip, the tab bar,
 * the tabs (swipe between them), the status banner and the playback panel
 * (with the menu button on its left).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(core: DreamCore, host: TabHost) {
    val playback by core.player.playback.collectAsStateWithLifecycle()
    // info / success — the bottom banner that hides itself; warnings and
    // errors — the top one that stays until swiped away.
    var status by remember { mutableStateOf<StatusMessage?>(null) }
    val alerts = remember {
        mutableStateListOf<StatusMessage>().apply {
            core.previousCrash?.let { add(StatusMessage(it, StatusKind.Warn)) }
            core.initError?.let { add(StatusMessage(it, StatusKind.Error)) }
        }
    }
    LaunchedEffect(Unit) {
        core.status.messages.collect { m ->
            when (m.kind) {
                StatusKind.Warn, StatusKind.Error -> {
                    alerts.add(m)
                    if (alerts.size > MAX_ALERTS) alerts.removeAt(0)
                }
                else -> status = m
            }
        }
    }

    val pullDp by core.settings.pullDistanceDp.collectAsStateWithLifecycle()
    val barGestures by core.settings.barGestures.collectAsStateWithLifecycle()
    val haptics by core.settings.pullHaptics.collectAsStateWithLifecycle()
    val hapticMs by core.settings.pullHapticMs.collectAsStateWithLifecycle()
    val hapticStrength by core.settings.pullHapticStrength.collectAsStateWithLifecycle()
    val scanning by core.library.scanning.collectAsStateWithLifecycle()
    // an editor closed by the pull gesture with unsaved changes: what to do with them.
    var asking by remember { mutableStateOf<TabSpec?>(null) }

    /** closes [tab]; an editor with unsaved changes asks save / don't save / cancel first. */
    val requestClose: (TabSpec) -> Unit = { tab ->
        if (tab.editing?.hasChanges == true) asking = tab else host.close(host.tabs.indexOf(tab))
    }

    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetOpen = false }
    }

    val pager = rememberPagerState { host.tabs.size }
    // the host -> the pager (tab bar tap, a tab opened / closed).
    LaunchedEffect(host.currentIndex, host.tabs.size) {
        val target = host.currentIndex
        when {
            pager.currentPage != target ->
                if (abs(pager.currentPage - target) == 1) pager.animateScrollToPage(target) else pager.scrollToPage(target)
            // a page was removed under the pager (the song tab closing itself):
            // snap back to a whole page.
            pager.currentPageOffsetFraction != 0f && !pager.isScrollInProgress -> pager.scrollToPage(target)
        }
    }
    // the pager -> the host: only once the scroll has fully stopped, so the
    // song tab isn't removed in the middle of a fling.
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, page) -> host.onSettled(page) }
    }

    // the PLAYING tab exists while something is chosen for playback; it is titled after the queue.
    val queueName = playback.queue?.name
    LaunchedEffect(queueName) { host.syncPlaying(queueName) }

    BackHandler(enabled = host.current != TabSpec.Playlists) { host.back() }

    val current = playback.currentTrack
    // Surface sets the content color (light text) for everything inside.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        NowPlayingStrip(
            track = current,
            isPaused = playback.isPaused,
            positionS = playback.positionS,
            durationS = playback.durationS,
            onClick = { current?.let(host::openSong) },
            onSeek = core.player::seek,
            modifier = Modifier.statusBarsPadding(),
        )
        DreamTabRow(
            host.titles,
            host.currentIndex,
            onSelect = host::select,
            // an editor with unsaved changes asks first (as the pull gesture).
            onClose = { i -> host.tabs.getOrNull(i)?.let(requestClose) },
            position = { pager.currentPage + pager.currentPageOffsetFraction },
        )

        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            HorizontalPager(
                state = pager,
                key = { host.tabs.getOrNull(it)?.id ?: it },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tab = host.tabs.getOrNull(page) ?: return@HorizontalPager
                val start = tab == TabSpec.Playlists
                PullAction(
                    kind = if (start) PullKind.Refresh else PullKind.Close,
                    busy = start && scanning,
                    threshold = pullDp.dp,
                    haptics = haptics,
                    hapticMs = hapticMs,
                    hapticStrength = hapticStrength / 100f,
                    onTrigger = {
                        when {
                            start -> core.launch { core.library.scan() }
                            else -> requestClose(tab)
                        }
                    },
                ) {
                    when (tab) {
                        TabSpec.Playlists -> PlaylistsTab(core, host)
                        TabSpec.Playing -> PlayingTab(core, host)
                        is TabSpec.Playlist -> PlaylistTab(core, host, tab.name, tab.focus, focusKey = tab)
                        is TabSpec.Song -> SongTab(core, host, tab.track)
                        is TabSpec.NewPlaylist -> PlaylistEditorTab(core, host, tab.state)
                        is TabSpec.EditPlaylist -> PlaylistEditorTab(core, host, tab.state)
                        is TabSpec.EditMeta -> MetaEditorTab(core, host, tab.state)
                        is TabSpec.Import -> ImportTab(core, host, tab.state)
                        is TabSpec.Export -> ExportTab(core, host, tab.state)
                        is TabSpec.Sync -> SyncTab(core, tab.state, host)
                        TabSpec.Orphans -> OrphansTab(core, host)
                        TabSpec.Logs -> LogsTab(core)
                        TabSpec.Settings -> SettingsTab(core)
                        TabSpec.About -> AboutTab(BuildConfig.VERSION_NAME, BuildConfig.CORE_VERSION)
                    }
                }
            }
            StatusBanner(status, Modifier.align(Alignment.BottomCenter))
        }

        PlayerBar(
            track = current,
            isPaused = playback.isPaused,
            mode = if (barGestures) PlayerBarMode.Gestures else PlayerBarMode.Buttons,
            onPrevious = core.player::previous,
            onPlayPause = core.player::playPause,
            onNext = core.player::next,
            onOpen = { current?.let(host::openSong) },
            onMenu = { sheetOpen = true },
            repeatOne = playback.repeatOne,
            onRepeat = core.player::toggleRepeatOne,
        )
    }
    AlertBanner(
        alerts = alerts,
        onDismiss = { alerts.remove(it) },
        onOpen = host::openLogs,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
    )
    }
    }

    asking?.let { tab ->
        UnsavedDialog(
            onSave = { tab.editing?.let { it.saveRequests++ } },
            onDiscard = { host.close(host.tabs.indexOf(tab)) },
            onDismiss = { asking = null },
        )
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            SettingsSheet(core, host, onDone = closeSheet)
        }
    }
}

/** warnings / errors kept for the top banner; older ones are dropped. */
private const val MAX_ALERTS = 20

/** the editor state of an editing / creating tab (null — the tab edits nothing). */
private val TabSpec.editing: Editing?
    get() = when (this) {
        is TabSpec.NewPlaylist -> state
        is TabSpec.EditPlaylist -> state
        is TabSpec.EditMeta -> state
        else -> null
    }

/** closing an editor with unsaved changes: save / don't save / cancel. */
@Composable
private fun UnsavedDialog(onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сохранить изменения?") },
        text = { Text("Вкладка закрывается, а изменения не сохранены.") },
        confirmButton = { TextButton(onClick = { onDismiss(); onSave() }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                TextButton(onClick = { onDismiss(); onDiscard() }) { Text("Не сохранять", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
