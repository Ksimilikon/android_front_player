package dev.dreamplayer.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import dev.dreamplayer.core.IdxStatus
import dev.dreamplayer.covers.CoverLoader
import dev.dreamplayer.model.Track
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * deleting songs from the device: the file goes, and so does the track in the
 * library. Android 11+ lets an app delete other apps' media only after the
 * user agrees in a system dialog, so it is two steps: [requestDelete] gives
 * the dialog (null — not needed), [finishDelete] deletes what is allowed and
 * cleans the library. Writing into the song file (tags, [embedCover]) asks
 * the same way: [writeRequest].
 */
class TrackFiles internal constructor(
    private val context: Context,
    private val rt: CoreRuntime,
    private val library: LibraryRepository,
    private val player: PlayerController,
    private val status: StatusBus,
) {
    private val resolver get() = context.contentResolver

    /**
     * the user gave the app "all files access" (Android 11+): it writes any
     * file in the shared storage by its path, no dialogs. Without it Android
     * lets an app change only the files it created itself.
     */
    val hasFullAccess: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** the system screen where the user turns "all files access" on for this app (null — not on this Android). */
    fun fullAccessIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    }

    /**
     * the system dialog asking to delete the files of [tracks] (Android 11+);
     * null — no dialog is needed and the deletion is already done.
     */
    suspend fun requestDelete(tracks: List<Track>): IntentSender? {
        // with "all files access" the files are deleted directly, no dialog.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasFullAccess) {
            val uris = tracks.mapNotNull { it.path?.takeIf(::exists)?.let { p -> mediaUriOrScan(p) } }
            if (uris.isNotEmpty()) return MediaStore.createDeleteRequest(resolver, uris).intentSender
        }
        finishDelete(tracks, granted = true)
        return null
    }

    /**
     * deletes the files of [tracks] that are still there (with Android 11+
     * the system did it after [granted]) and removes from the library every
     * track whose file is gone.
     */
    suspend fun finishDelete(tracks: List<Track>, granted: Boolean) {
        if (!granted) {
            status.info("удаление отменено")
            return
        }
        val failed = mutableListOf<String>()
        val gone = withContext(Dispatchers.IO) {
            tracks.filter { t ->
                val path = t.path ?: return@filter true
                if (exists(path) && !deleteFile(path)) failed += t.title
                !exists(path)
            }
        }
        if (gone.any { player.playback.value.isCurrent(it) }) player.stop()
        val codes = rt.call { gone.mapNotNull { it.id }.map { idxRemoveTrack(it) } }
        library.reload()
        val removed = codes.count { it == IdxStatus.OK }
        when {
            failed.isNotEmpty() ->
                status.error("не удалось удалить с устройства: ${failed.take(3).joinToString()}${if (failed.size > 3) "…" else ""}")
            codes.any { it != IdxStatus.OK } -> status.error("файлы удалены, но не все песни убраны из библиотеки")
            else -> status.ok(if (removed == 1) "песня удалена с устройства" else "удалено с устройства: $removed")
        }
    }

    /**
     * embeds [picture] (png / jpg) into the audio file of [track] and dates
     * the file as changed, so sync sends it again. When the core couldn't
     * write another app's file, Android 11+ gives the system dialog asking to
     * allow it: after the user agrees call this again with [asked] = true.
     * null — finished (the outcome is reported).
     */
    suspend fun embedCover(track: Track, picture: String, asked: Boolean = false): IntentSender? {
        val id = track.id ?: run {
            status.warn("трек не из библиотеки")
            return null
        }
        val code = rt.call { idxEmbedTrackCover(id, picture) }
        if (code == IdxStatus.WRITE_FAILED && !asked) writeRequest(track)?.let { return it }
        when (code) {
            IdxStatus.OK -> {
                rt.call { idxSetTrackFileChanged(id) }
                track.path?.let { CoverLoader.invalidate(listOf(it)) }
                status.ok("обложка встроена в файл")
            }
            IdxStatus.INVALID_ARGUMENT -> status.error("встроить можно только PNG или JPEG")
            IdxStatus.WRITE_FAILED -> status.error("не удалось записать файл песни")
            else -> status.error("не удалось встроить обложку: ${describeIdx(code)}")
        }
        return null
    }

    /**
     * the system dialog asking to allow writing the file of [track] (another
     * app's file, Android 11+); null — there is nothing to ask.
     */
    suspend fun writeRequest(track: Track): IntentSender? {
        // with "all files access" there is nothing to ask: the write itself failed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || hasFullAccess) return null
        val path = track.path ?: return null
        val uri = mediaUriOrScan(path) ?: run {
            status.warn("Android ещё не знает этот файл — разрешение на запись не запросить. Поможет «Полный доступ к файлам» в настройках")
            return null
        }
        return MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender
    }

    /**
     * the MediaStore item of a file; one MediaStore doesn't know yet (just
     * downloaded by another app — the system indexes such files when it gets
     * to them, sometimes days later) is indexed right now. The write / delete
     * dialog can be asked only for a known item.
     */
    private suspend fun mediaUriOrScan(path: String): Uri? = withContext(Dispatchers.IO) {
        mediaUri(path) ?: withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri -> if (cont.isActive) cont.resume(uri) }
            }
        } ?: mediaUri(path)
    }

    private fun exists(path: String) = File(path).exists()

    /** the file itself: through MediaStore when it knows it, otherwise directly. */
    private fun deleteFile(path: String): Boolean {
        val uri = mediaUri(path)
        try {
            if (uri != null && resolver.delete(uri, null, null) > 0) return true
        } catch (_: RecoverableSecurityException) {
            // Android 10: another app's file, only the user may allow it
            // (with "all files access" — the file itself, below).
            if (!hasFullAccess) return false
        } catch (_: SecurityException) {
            // fall back to the file itself.
        }
        return File(path).delete() || !exists(path)
    }

    /**
     * the MediaStore item of a file by its path (null — MediaStore doesn't
     * know it): among the audio, then among all files (a song MediaStore
     * didn't take for audio); by the path as given and as resolved
     * (`/sdcard/...` and `/storage/emulated/0/...` are one file).
     */
    @Suppress("DEPRECATION")
    private fun mediaUri(path: String): Uri? {
        val paths = listOf(path, runCatching { File(path).canonicalPath }.getOrDefault(path)).distinct()
        for (collection in listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Files.getContentUri("external"))) {
            for (p in paths) {
                resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DATA} = ?", arrayOf(p), null)
                    ?.use { c -> if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0)) }
            }
        }
        return null
    }

    private companion object {
        /** how long to wait for the system to index a file. */
        const val SCAN_TIMEOUT_MS = 5000L
    }
}
