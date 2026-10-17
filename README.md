# Dream Player — Android

Android-фронтенд для ядра [rs_dream_player](../rs_dream_player) (Kotlin + Jetpack Compose).
Функционально повторяет TUI ядра: плейлисты, очередь, песня, редакторы, импорт / экспорт.

## Сборка

Нужно: JDK 17+, Android SDK (`sdk.dir` в `local.properties`), NDK, Rust с таргетами
`aarch64-linux-android` / `x86_64-linux-android`, `cargo-ndk`.

```sh
./gradlew :app:assembleDebug        # ядро собирается cargo-ndk автоматически
./gradlew :app:installDebug
./gradlew testDebugUnitTest
```

Настройки (`gradle.properties` / `local.properties`): `dream.core.dir` — путь к ядру,
`dream.abis` — ABI для `.so`, `dream.ndk.dir`, `dream.cargo`.

Kotlin-контракт JNI (`core/ffi/.../Native.kt`, `Types.kt`) — копия
`rs_dream_player/bindings/android`; сборка падает, если они разошлись
(`./gradlew :core:ffi:syncCoreBindings` копирует заново).

## Модули

| модуль | что внутри |
|---|---|
| `:core:ffi` | JNI-контракт + `libdream_player.so` (cargo-ndk) |
| `:core:model` | доменные модели (Track, Playlist, Queue, PlaybackState…) |
| `:core:data` | DreamCore: библиотека, плеер (очередь, автопереход), импорт / экспорт |
| `:core:playback` | MediaSessionService: фон, уведомление, экран блокировки, аудиофокус |
| `:lib:common` | форматирование, поиск по трекам, перемещения в списках |
| `:lib:covers` | загрузка обложек (файл или встроенная в теги), кэш |
| `:ui:kit` | тема, единый список, плашка, панель плеера, вертикальный ползунок, формы |
| `:feature:*` | вкладки: playlists, queue, song, editor, meta, exchange, settings (шторка) |
| `:app` | хост вкладок (пейджер), корневой экран, Activity, разрешения |
| `build-logic` | convention-плагины Gradle и задача cargo-ndk |
