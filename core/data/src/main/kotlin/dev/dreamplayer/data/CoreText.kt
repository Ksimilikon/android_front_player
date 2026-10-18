package dev.dreamplayer.data

import dev.dreamplayer.core.ConStatus
import dev.dreamplayer.core.CoreResult
import dev.dreamplayer.core.EngineStatus
import dev.dreamplayer.core.IdxStatus
import dev.dreamplayer.core.InitStatus
import dev.dreamplayer.core.Native
import dev.dreamplayer.core.PairFailure
import dev.dreamplayer.core.SyncStatus
import dev.dreamplayer.core.WireStatus

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
    IdxStatus.INVALID_ARGUMENT -> "неверный аргумент"
    IdxStatus.PANIC -> describePanic()
    else -> "код $code"
}

/** human-readable `ConStatus`. */
fun describeCon(code: Int): String = when (code) {
    ConStatus.OK -> "готово"
    ConStatus.NOT_INITIALIZED -> "ядро не запущено"
    ConStatus.NULL_POINTER -> "внутренняя ошибка (null)"
    ConStatus.INVALID_UTF8 -> "битый текст"
    ConStatus.INVALID_ARGUMENT -> "неверный аргумент"
    ConStatus.NO_DATABASE -> "база данных недоступна"
    ConStatus.KEYS_FAILED -> "не удалось загрузить ключи устройства"
    ConStatus.UNKNOWN_DEVICE -> "нет такого подключения"
    ConStatus.UNKNOWN_PAIRED_DEVICE -> "нет такого сопряжённого устройства"
    ConStatus.WRONG_STATE -> "недопустимо в текущем состоянии (устройство подключено?)"
    ConStatus.BUSY -> "устройство занято передачей"
    ConStatus.DB_FAILED -> "ошибка базы данных"
    ConStatus.INVALID_PACKET -> "это не зашифрованный пакет"
    ConStatus.WEAK_KEY -> "слабый ключ устройства"
    ConStatus.DECRYPT_FAILED -> "не удалось расшифровать"
    ConStatus.TOO_LONG -> "слишком большие данные"
    ConStatus.PANIC -> describePanic()
    else -> "код $code"
}

/** human-readable `SyncStatus`. */
fun describeSync(code: Int): String = when (code) {
    SyncStatus.OK -> "готово"
    SyncStatus.NOT_INITIALIZED -> "ядро не запущено"
    SyncStatus.NULL_POINTER -> "внутренняя ошибка (null)"
    SyncStatus.INVALID_ARGUMENT -> "внутренняя ошибка: пути не совпадают с файлами"
    SyncStatus.NO_DATABASE -> "база данных недоступна"
    SyncStatus.KEYS_FAILED -> "не удалось загрузить ключи устройства"
    SyncStatus.UNKNOWN_DEVICE -> "нет такого подключения"
    SyncStatus.WRONG_STATE -> "недопустимо в текущем состоянии"
    SyncStatus.INVALID_UTF8 -> "битый путь"
    SyncStatus.NOT_FOUND -> "часть плейлистов / треков не найдена в библиотеке"
    SyncStatus.DB_FAILED -> "ошибка базы данных"
    SyncStatus.IO_FAILED -> "не удалось записать (соединение / файл)"
    SyncStatus.PANIC -> describePanic()
    else -> "код $code"
}

/** human-readable protocol status (`Ack` / `Abort` of the sync protocol). */
fun describeWire(code: Int): String = when (code) {
    WireStatus.OK -> "готово"
    WireStatus.INVALID_PACKET -> "битый пакет"
    WireStatus.UNEXPECTED -> "неожиданный пакет"
    WireStatus.DECRYPT_FAILED -> "не удалось расшифровать (устройство не сопряжено с этим?)"
    WireStatus.PAIR_FAILED -> "сопряжение не удалось (ключ устройства изменился?)"
    WireStatus.REJECTED -> "отклонено: локальные плейлисты новее"
    WireStatus.WRITE_FAILED -> "не удалось сохранить данные"
    WireStatus.BUSY -> "устройство занято"
    WireStatus.CANCELLED -> "отменено"
    WireStatus.INTERNAL -> "внутренняя ошибка"
    WireStatus.MODE_MISMATCH ->
        "режимы не совпадают: для синхронизации на обоих устройствах «синхронизация», для передачи — на одном «отправка», на другом «приём»"
    else -> "код $code"
}

/** why pairing failed (`PairFailure`). */
fun describePairFailure(code: Int): String = when (code) {
    PairFailure.NONE -> "—"
    PairFailure.REJECTED -> "отклонено"
    PairFailure.TIMEOUT -> "нет ответа"
    PairFailure.ERROR -> "ошибка соединения"
    PairFailure.KEY_MISMATCH -> "ключ не совпадает с сохранённым"
    PairFailure.ALREADY_PAIRED -> "уже сопряжено через другое подключение"
    else -> "код $code"
}
