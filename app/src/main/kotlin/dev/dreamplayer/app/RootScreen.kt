package dev.dreamplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.dreamplayer.feature.meta.MetaEditorTab
import dev.dreamplayer.feature.playlists.PlaylistsTab
import dev.dreamplayer.feature.queue.QueueTab
import dev.dreamplayer.feature.settings.SettingsSheet
import dev.dreamplayer.feature.song.SongTab
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import dev.dreamplayer.ui.StatusBanner
import dev.dreamplayer.ui.nav.DreamTabRow
import dev.dreamplayer.ui.player.NowPlayingStrip
import dev.dreamplayer.ui.player.PlayerBar
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * the frame (as `App::draw` in the TUI): the now-playing strip, the tab bar,
 * the tabs (swipe between them), the settings sheet button in the bottom-left
 * corner, the status banner and the playback panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(core: DreamCore, host: TabHost) {
    val playback by core.player.playback.collectAsStateWithLifecycle()
    var status by remember {
        mutableStateOf(
            core.initError?.let { StatusMessage(it, StatusKind.Error) }
                ?: core.previousCrash?.let { StatusMessage(it, StatusKind.Warn) },
        )
    }
    LaunchedEffect(Unit) { core.status.messages.collect { status = it } }

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

    BackHandler(enabled = host.current != TabSpec.Playlists) { host.back() }

    val current = playback.currentTrack
    // Surface sets the content color (light text) for everything inside.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        NowPlayingStrip(
            track = current,
            isPaused = playback.isPaused,
            onClick = { current?.let(host::openSong) },
            modifier = Modifier.statusBarsPadding(),
        )
        DreamTabRow(host.titles, host.currentIndex, onSelect = host::select, onClose = host::close)

        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            HorizontalPager(
                state = pager,
                key = { host.tabs.getOrNull(it)?.id ?: it },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (val tab = host.tabs.getOrNull(page)) {
                    TabSpec.Playlists -> PlaylistsTab(core, host)
                    is TabSpec.Queue -> QueueTab(core, host, tab.name)
                    is TabSpec.Song -> SongTab(core, host, tab.track)
                    is TabSpec.NewPlaylist -> PlaylistEditorTab(core, host, tab.state)
                    is TabSpec.EditPlaylist -> PlaylistEditorTab(core, host, tab.state)
                    is TabSpec.EditMeta -> MetaEditorTab(core, host, tab.state)
                    is TabSpec.Import -> ImportTab(core, tab.state)
                    is TabSpec.Export -> ExportTab(core, tab.state)
                    null -> {}
                }
            }
            SmallFloatingActionButton(
                onClick = { sheetOpen = true },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) { Icon(Icons.Filled.Tune, contentDescription = "Настройки") }
            StatusBanner(status, Modifier.align(Alignment.BottomCenter).padding(start = 56.dp))
        }

        PlayerBar(
            track = current,
            isPaused = playback.isPaused,
            positionS = playback.positionS,
            durationS = playback.durationS,
            onPrevious = core.player::previous,
            onPlayPause = core.player::playPause,
            onNext = core.player::next,
            onOpen = { current?.let(host::openSong) },
        )
    }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            SettingsSheet(core, host, onDone = closeSheet)
        }
    }
}
