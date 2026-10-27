package dev.dreamplayer.data

import dev.dreamplayer.core.CdcStatus

/** human-readable `CdcStatus` (as `cdc::describe` in the TUI). */
fun describeCdc(code: Int): String = when (code) {
    CdcStatus.OK -> "готово"
    CdcStatus.BAD_START_WORD -> "это не пакет dream player"
    CdcStatus.UNSUPPORTED_VERSION -> "неподдерживаемая версия пакета"
    CdcStatus.UNEXPECTED_EOF -> "пакет обрезан"
    CdcStatus.TRAILING_BYTES -> "лишние данные в конце пакета"
    CdcStatus.INVALID_UTF8 -> "битый текст в пакете"
    CdcStatus.TOO_MANY_ROWS -> "слишком много записей"
    CdcStatus.NAME_TOO_LONG -> "слишком длинное имя"
    CdcStatus.DATA_TOO_LONG -> "слишком большой файл"
    CdcStatus.DUPLICATE_SECTION -> "повторяющаяся секция пакета"
    CdcStatus.COUNT_MISMATCH -> "число записей не совпадает"
    CdcStatus.OUTDATED -> "пакет старше локальных плейлистов"
    CdcStatus.NULL_POINTER -> "внутренняя ошибка (null)"
    CdcStatus.NO_DATABASE -> "база данных недоступна"
    CdcStatus.TRACK_NOT_FOUND -> "трек не найден"
    CdcStatus.INVALID_HASH -> "неверный хеш трека"
    CdcStatus.READ_FAILED -> "ошибка чтения"
    CdcStatus.PLAYLIST_NOT_FOUND -> "плейлист не найден"
    CdcStatus.WRITE_FAILED -> "ошибка записи"
    CdcStatus.INVALID_ARGUMENT -> "неверный аргумент"
    CdcStatus.NOT_INITIALIZED -> "ядро не запущено"
    CdcStatus.PANIC -> describePanic()
    else -> "код $code"
}
