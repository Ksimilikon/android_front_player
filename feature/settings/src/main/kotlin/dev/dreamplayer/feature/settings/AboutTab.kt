package dev.dreamplayer.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.form.InfoLine
import dev.dreamplayer.ui.form.SectionTitle
import dev.dreamplayer.ui.theme.Dream

/** the ABOUT tab: what the app is, versions, where the code is, who made it, the licenses. */
@Composable
fun AboutTab(appVersion: String, coreVersion: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text(
            "Dream Player",
            style = MaterialTheme.typography.headlineSmall,
            color = Dream.colors.playing,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Text(
            "Плеер для музыки, которая лежит на телефоне.",
            style = MaterialTheme.typography.bodyMedium,
            color = Dream.colors.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        SectionTitle("Что умеет")
        for (line in FEATURES) Bullet(line)

        SectionTitle("Версии")
        InfoLine("Приложение", appVersion)
        InfoLine("Ядро", coreVersion)

        SectionTitle("Исходный код")
        Link("Приложение (Android)", APP_REPO)
        Link("Ядро (Rust)", CORE_REPO)

        SectionTitle("Создатели")
        Link("ksimilikon", AUTHOR_PROFILE)
        Bullet("Claude Opus")

        SectionTitle("Лицензии")
        License("Приложение — GNU GPL v3", "licenses/app.txt")
        License("Ядро — Mozilla Public License 2.0", "licenses/core.txt")
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = Dream.colors.playing)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** a link that opens in the browser. */
@Composable
private fun Link(label: String, url: String) {
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(url) } }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(url.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = Dream.colors.playing)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Открыть", tint = Dream.colors.muted)
    }
}

/** a license: folded to its name, unfolds into the full text (from the app's assets). */
@Composable
private fun License(title: String, asset: String) {
    var open by rememberSaveable(asset) { mutableStateOf(false) }
    val context = LocalContext.current
    val text = remember(asset, open) {
        if (!open) null else runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrNull()
    }
    Row(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (open) "Свернуть" else "Развернуть",
            tint = Dream.colors.muted,
        )
    }
    if (open) {
        SelectionContainer {
            Text(
                text ?: "текст лицензии не найден в сборке",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Dream.colors.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

private val FEATURES = listOf(
    "плейлисты, очередь воспроизведения и обзор каждой песни",
    "редактор плейлистов: порядок, обложка, поиск, сортировка, предпрослушивание",
    "изменение тегов и обложек, запись их в сам файл",
    "импорт и экспорт библиотеки, плейлистов и песен пакетами .dplr",
    "синхронизация и передача между устройствами: Wi-Fi, точка доступа, USB-кабель",
    "поиск песен вне плейлистов и очистка библиотеки",
    "ядро на Rust (индексация, воспроизведение, теги, синхронизация), интерфейс на Kotlin и Jetpack Compose",
)

private const val APP_REPO = "https://github.com/ksimilikon/android_front_player"
private const val CORE_REPO = "https://github.com/ksimilikon/rs_dream_player"
private const val AUTHOR_PROFILE = "https://github.com/ksimilikon"
