package dev.dreamplayer.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.common.percent
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.list.ListRow
import dev.dreamplayer.ui.nav.Navigator
import dev.dreamplayer.ui.theme.Dream

/**
 * contents of the bottom sheet: import / export, the library, the master
 * volume and the way to the SETTINGS tab (flags and heavy maintenance).
 * [onDone] — close the sheet (after opening a tab).
 */
@Composable
fun SettingsSheet(core: DreamCore, nav: Navigator, onDone: () -> Unit) {
    val playback by core.player.playback.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        val path = core.paths.treeToPath(tree)
        if (path == null) core.status.warn("эта папка не на локальном накопителе") else core.launch { core.library.scan(path) }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("Обмен")
        Action("Импорт", "добавить пакет .dplr в библиотеку", Icons.Filled.Download) { nav.openImport(); onDone() }
        Action("Экспорт", "библиотеку, плейлисты или треки в пакет", Icons.Filled.Upload) { nav.openExport(); onDone() }
        Action("Устройства", "синхронизация и передача: сеть, точка доступа, USB", Icons.Filled.Devices) { nav.openSync(); onDone() }

        SectionTitle("Библиотека")
        Action("Новый плейлист", null, Icons.AutoMirrored.Filled.PlaylistAdd) { nav.openNewPlaylist(); onDone() }
        Action("Сканировать другую папку…", null, Icons.Filled.CreateNewFolder) { pickFolder.launch(null) }
        Action("Песни вне плейлистов", "найти и удалить с устройства ненужные", Icons.Filled.DeleteSweep) { nav.openOrphans(); onDone() }

        SectionTitle("Воспроизведение")
        MasterVolume(playback.masterVolume) { core.player.setMasterVolume(it) }

        SectionTitle("Приложение")
        Action("Настройки", "жесты, вкладки, пересканирование тегов, очистка", Icons.Filled.Settings) { nav.openSettings(); onDone() }
        Action("Логи", "вызовы ядра, события и сообщения", Icons.Filled.Terminal) { nav.openLogs(); onDone() }
        Action("О приложении", "версии, исходный код, создатели, лицензии", Icons.Filled.Info) { nav.openAbout(); onDone() }
    }
}

@Composable
private fun Action(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { Icon(icon, contentDescription = null, tint = if (danger) Dream.colors.error else Dream.colors.playing) },
        onClick = onClick,
    )
}

@Composable
private fun MasterVolume(value: Float, onChange: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val v = drag ?: value
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Dream.colors.muted, modifier = Modifier.size(20.dp))
        Slider(
            value = v,
            onValueChange = { drag = it; onChange(it) },
            onValueChangeFinished = { drag = null },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(percent(v), style = MaterialTheme.typography.labelMedium, color = Dream.colors.muted)
    }
}
