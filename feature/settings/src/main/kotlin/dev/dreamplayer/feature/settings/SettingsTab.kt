package dev.dreamplayer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dreamplayer.data.AppSettings
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.ui.ConfirmDialog
import dev.dreamplayer.ui.buzz
import dev.dreamplayer.ui.canSetVibrationStrength
import dev.dreamplayer.ui.form.FormSwitch
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.list.ListRow
import dev.dreamplayer.ui.theme.Dream
import kotlin.math.roundToInt

/** the SETTINGS tab: interface flags and the heavy library maintenance. */
@Composable
fun SettingsTab(core: DreamCore) {
    val settings = core.settings
    val autoClose by settings.autoClosePlaylist.collectAsStateWithLifecycle()
    val barGestures by settings.barGestures.collectAsStateWithLifecycle()
    val pullDp by settings.pullDistanceDp.collectAsStateWithLifecycle()
    val haptics by settings.pullHaptics.collectAsStateWithLifecycle()
    val hapticMs by settings.pullHapticMs.collectAsStateWithLifecycle()
    val hapticStrength by settings.pullHapticStrength.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canStrength = remember(context) { canSetVibrationStrength(context) }
    val retagging by core.library.retagging.collectAsStateWithLifecycle()
    var cleaning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SectionTitle("Вкладки")
        FormSwitch(
            "Закрывать вкладку плейлиста",
            autoClose,
            settings::setAutoClosePlaylist,
            description = "когда уходишь с неё на другую вкладку",
        )

        SectionTitle("Нижняя панель")
        Hint(
            if (barGestures) "проведите по песне влево — следующая, вправо — предыдущая; нажатие — пауза / воспроизведение"
            else "кнопки «предыдущая», «пауза», «следующая» справа; нажатие на песню открывает её",
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !barGestures, onClick = { settings.setBarGestures(false) }, label = { Text("Кнопки") })
            FilterChip(selected = barGestures, onClick = { settings.setBarGestures(true) }, label = { Text("Жесты") })
        }

        SectionTitle("Жест «потянуть вниз»")
        Hint(
            "Долистайте вкладку до самого верха и продолжайте тянуть пальцем вниз — сверху появится кружок. " +
                "Когда он станет цветным, отпустите: на «Плейлистах» обновится список песен, на остальных вкладках " +
                "вкладка закроется. Передумали — верните палец назад.",
        )
        SettingSlider(
            "Насколько тянуть",
            pullDp,
            AppSettings.PULL_DP_RANGE,
            "dp",
            "меньше — срабатывает раньше",
            settings::setPullDistanceDp,
        )
        FormSwitch(
            "Вибрация при жесте",
            haptics,
            settings::setPullHaptics,
            description = "только для жеста «потянуть вниз»: короткая вибрация, когда кружок стал цветным",
        )
        if (haptics) {
            SettingSlider(
                "Длительность вибрации",
                hapticMs,
                AppSettings.VIBRATION_MS_RANGE,
                "мс",
                "дольше — заметнее",
                { settings.setPullHapticMs(it); buzz(context, it, hapticStrength / 100f) },
            )
            SettingSlider(
                "Сила вибрации",
                hapticStrength,
                AppSettings.VIBRATION_STRENGTH_RANGE,
                "%",
                if (canStrength) "сильнее — заметнее" else "этот телефон не умеет менять силу вибрации — меняйте длительность",
                { settings.setPullHapticStrength(it); buzz(context, hapticMs, it / 100f) },
                enabled = canStrength,
            )
            OutlinedButton(
                onClick = { buzz(context, hapticMs, hapticStrength / 100f) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) { Text("Проверить вибрацию") }
        }

        SectionTitle("Библиотека")
        ListRow(
            title = "Пересканировать теги",
            subtitle = if (retagging) "идёт… можно пользоваться приложением" else "все файлы музыки — может занять долго",
            leading = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = Dream.colors.playing) },
            trailing = if (retagging) {
                { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) }
            } else {
                null
            },
            onClick = { if (!retagging) core.launch { core.library.rescanTags() } },
        )
        ListRow(
            title = "Удалить невалидные треки",
            subtitle = "треки, файлов которых больше нет",
            leading = { Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = Dream.colors.error) },
            onClick = { cleaning = true },
        )
    }

    if (cleaning) {
        ConfirmDialog(
            title = "Удалить невалидные треки?",
            text = "Треки с недоступными файлами будут удалены из библиотеки и плейлистов.",
            confirmLabel = "Удалить",
            onConfirm = { core.launch { core.library.removeInvalid() } },
            onDismiss = { cleaning = false },
        )
    }
}

/** a whole-number setting on a slider; saved (and [onChange] called) when the finger is lifted. */
@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    hint: String,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val v = drag ?: value.toFloat()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else Dream.colors.muted,
                modifier = Modifier.weight(1f),
            )
            Text("${v.roundToInt()} $unit", style = MaterialTheme.typography.labelLarge, color = if (enabled) Dream.colors.playing else Dream.colors.muted)
        }
        Slider(
            value = v,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { onChange(it.roundToInt()) }
                drag = null
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Dream.colors.muted)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Dream.colors.muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
