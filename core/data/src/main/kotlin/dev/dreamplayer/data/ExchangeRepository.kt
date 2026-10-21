package dev.dreamplayer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dev.dreamplayer.common.humanSize
import dev.dreamplayer.core.CdcStatus
import dev.dreamplayer.core.ImportFileKind
import dev.dreamplayer.core.PACKET_VERSION
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import java.io.File

/** what to pack. */
sealed interface ExportWhat {
    data object Library : ExportWhat
    data class Playlists(val names: List<String>) : ExportWhat
    data class Tracks(val ids: List<Long>) : ExportWhat
}

data class ExportFlags(val audio: Boolean, val names: Boolean, val covers: Boolean)

/**
 * packets: export into a document chosen by the user and the core's
 * two-step import. Packets and audio can be gigabytes, so the bytes never pass
 * through the Java heap — the core reads / writes file descriptors.
 */
class ExchangeRepository internal constructor(
    private val context: Context,
    private val rt: CoreRuntime,
    private val paths: StoragePaths,
    private val library: LibraryRepository,
    private val coreLog: CoreLog,
    private val covers: CoverStore,
) {
    private val resolver get() = context.contentResolver

    /** the tab's log line also goes to the core log. */
    private fun ((StatusMessage) -> Unit).teed(): (StatusMessage) -> Unit = { m ->
        coreLog.add(LogSource.Status, m.kind, m.text)
        this(m)
    }

    /** packs [what] into [target] (a CreateDocument URI); the log lines go to [log]. */
    suspend fun export(target: Uri, what: ExportWhat, flags: ExportFlags, tabLog: (StatusMessage) -> Unit): Boolean {
        val log = tabLog.teed()
        log(info("упаковка: ${describe(what)} (аудио: ${yes(flags.audio)}, имена: ${yes(flags.names)}, обложки: ${yes(flags.covers)})…"))
        var size = 0L
        val code = runCatching {
            resolver.openFileDescriptor(target, "wt")!!.use { pfd ->
                val code = rt.call {
                    when (what) {
                        ExportWhat.Library -> cdcPackLibrary(PACKET_VERSION, flags.audio, flags.names, flags.covers, pfd.fd)
                        is ExportWhat.Playlists -> cdcPackPlaylists(
                            PACKET_VERSION, what.names.toTypedArray(), flags.audio, flags.names, flags.covers, pfd.fd,
                        )
                        is ExportWhat.Tracks -> cdcPackTracks(PACKET_VERSION, what.ids.toLongArray(), flags.audio, flags.names, flags.covers, pfd.fd)
                    }
                }
                size = pfd.statSize
                code
            }
        }.getOrElse { CdcStatus.WRITE_FAILED }
        if (code != CdcStatus.OK) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            log(StatusMessage("экспорт не удался: ${describeCdc(code)}", StatusKind.Error))
            return false
        }
        log(StatusMessage("экспортировано ${humanSize(size)}", StatusKind.Ok))
        return true
    }

    /**
     * imports packet [packet]: the core checks it, the songs are written into
     * `Music/[subdir]` through MediaStore, covers into the app's directory, then
     * the core saves the final paths. Any failure rolls the written files back.
     */
    suspend fun import(packet: Uri, force: Boolean, subdir: String, tabLog: (StatusMessage) -> Unit): Boolean {
        val log = tabLog.teed()
        // step 1: the core reads the packet and checks the dates.
        val prepared = runCatching {
            resolver.openFileDescriptor(packet, "r")!!.use { pfd ->
                log(info("чтение пакета (${humanSize(pfd.statSize)})${if (force) ", принудительно" else ""}…"))
                rt.call { cdcImportPrepare(pfd.fd, force) }
            }
        }.getOrElse {
            log(StatusMessage("не удалось открыть пакет: ${it.message}", StatusKind.Error))
            return false
        }
        when (prepared.status) {
            CdcStatus.OK -> {}
            CdcStatus.OUTDATED -> return false.also { log(StatusMessage(describeCdc(prepared.status), StatusKind.Warn)) }
            else -> return false.also { log(StatusMessage("импорт отклонён: ${describeCdc(prepared.status)}", StatusKind.Error)) }
        }
        val songs = prepared.files.count { it.kind == ImportFileKind.SONG }
        val covers = prepared.files.size - songs
        log(info("новых песен: $songs, обложек: $covers → Music/$subdir"))

        // step 2: the files are written by us, the core fills each descriptor.
        val written = mutableListOf<Written>()
        for ((i, f) in prepared.files.withIndex()) {
            val res = runCatching {
                when (f.kind) {
                    ImportFileKind.SONG -> writeSong(prepared.session, i, f.name, subdir)
                    // a playlist cover or (v0.12+) a song's own cover file.
                    else -> writeCover(prepared.session, i, f.name)
                }
            }.getOrElse { Result.failure(it) }
            res.onSuccess(written::add).onFailure {
                log(StatusMessage("не удалось записать ${f.name}: ${it.message}", StatusKind.Error))
                rollback(written)
                rt.call { cdcImportCancel(prepared.session) }
                return false
            }
        }

        // step 3: the core saves the paths (and frees the session).
        val code = rt.call { cdcImportCommit(prepared.session, written.map { it.path }.toTypedArray<String?>()) }
        if (code != CdcStatus.OK) {
            rollback(written)
            val kind = if (code == CdcStatus.OUTDATED) StatusKind.Warn else StatusKind.Error
            log(StatusMessage("импорт не удался: ${describeCdc(code)}", kind))
            return false
        }
        library.reload()
        log(StatusMessage("импортировано: песен $songs, обложек $covers", StatusKind.Ok))
        return true
    }

    internal class Written(val path: String, val uri: Uri?)

    /** fills an open descriptor with a file's bytes (the core writes them); false — failed. */
    internal fun interface Fill {
        suspend fun into(fd: Int): Boolean
    }

    private fun importFill(session: Long, index: Int) = Fill { fd -> rt.call { cdcImportWriteFile(session, index, fd) } == CdcStatus.OK }

    private suspend fun writeSong(session: Long, index: Int, name: String, subdir: String): Result<Written> =
        saveSong(name, subdir, importFill(session, index))

    private suspend fun writeCover(session: Long, index: Int, name: String): Result<Written> =
        saveCover(name, importFill(session, index))

    /** a song into the shared music collection; MediaStore resolves name clashes itself. */
    internal suspend fun saveSong(name: String, subdir: String, fill: Fill): Result<Written> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // before scoped storage: a plain file (WRITE_EXTERNAL_STORAGE).
            val dir = File(paths.music, subdir).apply { mkdirs() }
            return saveFile(paths.unique(dir, name), fill)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, audioMime(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$subdir")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return Result.failure(IllegalStateException("MediaStore отказал"))
        val ok = runCatching { resolver.openFileDescriptor(uri, "w")!!.use { pfd -> fill.into(pfd.fd) } }.getOrDefault(false)
        if (!ok) {
            resolver.delete(uri, null, null)
            return Result.failure(IllegalStateException("не удалось записать"))
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        val path = dataPath(uri) ?: run {
            resolver.delete(uri, null, null)
            return Result.failure(IllegalStateException("неизвестен путь файла"))
        }
        return Result.success(Written(path, uri))
    }

    /** a cover into the app's covers directory. */
    internal suspend fun saveCover(name: String, fill: Fill): Result<Written> =
        saveFile(covers.newFile(name), fill)

    /**
     * writes over an existing file by path; false — not allowed / failed (on
     * scoped storage only files this app created are writable by path).
     */
    internal suspend fun overwrite(path: String, fill: Fill): Boolean = runCatching {
        val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE
        ParcelFileDescriptor.open(File(path), mode).use { pfd -> fill.into(pfd.fd) }
    }.getOrDefault(false)

    private suspend fun saveFile(file: File, fill: Fill): Result<Written> {
        val ok = runCatching {
            val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
            ParcelFileDescriptor.open(file, mode).use { pfd -> fill.into(pfd.fd) }
        }.getOrDefault(false)
        if (!ok) {
            file.delete()
            return Result.failure(IllegalStateException("не удалось записать ${file.name}"))
        }
        return Result.success(Written(file.path, null))
    }

    private fun rollback(written: List<Written>) {
        for (w in written) {
            if (w.uri != null) runCatching { resolver.delete(w.uri, null, null) } else File(w.path).delete()
        }
    }

    /** the absolute path of a MediaStore item (the core works with paths). */
    @Suppress("DEPRECATION")
    private fun dataPath(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun audioMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return if (mime != null && mime.startsWith("audio/")) mime else "audio/mpeg"
    }

    private fun describe(w: ExportWhat) = when (w) {
        ExportWhat.Library -> "вся библиотека"
        is ExportWhat.Playlists -> "плейлистов: ${w.names.size}"
        is ExportWhat.Tracks -> "треков: ${w.ids.size}"
    }

    private fun yes(b: Boolean) = if (b) "да" else "нет"
    private fun info(text: String) = StatusMessage(text, StatusKind.Info)
}
