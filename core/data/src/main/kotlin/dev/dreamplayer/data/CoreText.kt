package dev.dreamplayer.data

import dev.dreamplayer.core.CoreResult
import dev.dreamplayer.core.EngineStatus
import dev.dreamplayer.core.IdxStatus
import dev.dreamplayer.core.InitStatus
import dev.dreamplayer.core.Native

/** the value of an OK result, otherwise [default]. */
internal fun <T> CoreResult<T>.or(default: T): T = if (isOk) value ?: default else default

/** a core panic for the user: the core survived it, the call failed. */
internal fun describePanic(): String = "сбой ядра: ${Native.coreLastPanic() ?: "нет данных"}"

/** human-readable `InitStatus`. */
fun describeInit(code: Int): String = when (code) {
    InitStatus.OK -> "готово"
    InitStatus.NULL_POINTER -> "внутренняя ошибка (null)"
    InitStatus.INVALID_UTF8 -> "неверный путь к папке"
    InitStatus.ALREADY_INITIALIZED -> "ядро уже запущено"
    InitStatus.DB_FAILED -> "не удалось открыть базу данных"
    InitStatus.AUDIO_FAILED -> "не удалось запустить вывод звука"
    InitStatus.PANIC -> describePanic()
    else -> "код $code"
}

/** human-readable `EngineStatus`. */
fun describeEngine(code: Int): String = when (code) {
    EngineStatus.OK -> "готово"
    EngineStatus.NOT_INITIALIZED -> "ядро не запущено"
    EngineStatus.NULL_POINTER -> "внутренняя ошибка (null)"
    EngineStatus.INVALID_UTF8 -> "битый текст"
    EngineStatus.NO_DATABASE -> "база данных недоступна"
    EngineStatus.READ_FAILED -> "ошибка чтения базы"
    EngineStatus.TRACK_NOT_FOUND -> "трек не найден"
    EngineStatus.LOAD_FAILED -> "файл не читается"
    EngineStatus.PLAYBACK_FAILED -> "формат не поддерживается"
    EngineStatus.SEEK_FAILED -> "перемотка не поддерживается"
    EngineStatus.DEVICE_FAILED -> "устройство вывода недоступно"
    EngineStatus.CONFIG_WRITE_FAILED -> "настройки не сохранились"
    EngineStatus.PANIC -> describePanic()
    else -> "код $code"
}

/** human-readable `IdxStatus`. */
fun describeIdx(code: Int): String = when (code) {
    IdxStatus.OK -> "готово"
    IdxStatus.NOT_INITIALIZED -> "ядро не запущено"
    IdxStatus.NULL_POINTER -> "внутренняя ошибка (null)"
    IdxStatus.INVALID_UTF8 -> "битый текст"
    IdxStatus.NO_DATABASE -> "база данных недоступна"
    IdxStatus.NOT_FOUND -> "не найдено"
    IdxStatus.READ_FAILED -> "ошибка чтения базы"
    IdxStatus.WRITE_FAILED -> "ошибка записи в базу"
    IdxStatus.INDEX_FAILED -> "папка не читается"
    IdxStatus.PANIC -> describePanic()
    else -> "код $code"
}
