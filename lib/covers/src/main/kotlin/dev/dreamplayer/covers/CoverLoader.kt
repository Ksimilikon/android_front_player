package dev.dreamplayer.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * loads covers for display. Priority (as in the TUI): the separate cover file
 * from the index, then the picture embedded in the audio file. Results
 * (including "no cover") are cached by (source, size bucket); files written
 * over (a sync, a new cover) are forgotten with [invalidate], and [changes]
 * tells the shown covers to look again.
 */
object CoverLoader {
    /** ~24 MiB of decoded bitmaps. */
    private val cache = object : LruCache<String, Entry>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Entry): Int = value.bitmap?.allocationByteCount ?: 64
    }

    private class Entry(val bitmap: Bitmap?)

    private val generation = MutableStateFlow(0L)

    /** bumped on every invalidation: shown covers re-read the cache. */
    val changes: StateFlow<Long> = generation.asStateFlow()

    /** a cached result without touching the disk (null — not loaded yet). */
    fun peek(coverPath: String?, audioPath: String?, sizePx: Int): Result? =
        cache.get(key(coverPath, audioPath, sizePx))?.let { Result(it.bitmap) }

    /** the cover downscaled to about [sizePx] on its longest side; null bitmap — none. */
    suspend fun load(coverPath: String?, audioPath: String?, sizePx: Int): Result {
        val key = key(coverPath, audioPath, sizePx)
        cache.get(key)?.let { return Result(it.bitmap) }
        val bmp = withContext(Dispatchers.IO) {
            coverPath?.let { fromFile(it, sizePx) } ?: audioPath?.let { embedded(it, sizePx) }
        }
        cache.put(key, Entry(bmp))
        return Result(bmp)
    }

    /** forget every cached cover (after a cover was changed). */
    fun invalidate() {
        cache.evictAll()
        generation.update { it + 1 }
    }

    /**
     * forget the covers read from [paths] (cover files or audio files that
     * were written over); the rest of the cache stays.
     */
    fun invalidate(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val gone = paths.toSet()
        for (key in cache.snapshot().keys) {
            val (cover, audio) = key.split(SEP)
            if (cover in gone || audio in gone) cache.remove(key)
        }
        generation.update { it + 1 }
    }

    @JvmInline
    value class Result(val bitmap: Bitmap?)

    private fun key(cover: String?, audio: String?, size: Int): String {
        // buckets of 64px: neighbouring sizes share an entry.
        val bucket = ((size + 63) / 64) * 64
        return "${cover.orEmpty()}$SEP${audio.orEmpty()}$SEP$bucket"
    }

    /** a character no path holds. */
    private const val SEP = '\u0000'

    private fun fromFile(path: String, size: Int): Bitmap? {
        if (!File(path).isFile) return null
        return decode(size) { opts -> BitmapFactory.decodeFile(path, opts) }
    }

    private fun embedded(path: String, size: Int): Bitmap? {
        if (!File(path).isFile) return null
        // not `use`: MediaMetadataRetriever is AutoCloseable only since API 29.
        val r = MediaMetadataRetriever()
        val bytes = try {
            r.setDataSource(path)
            r.embeddedPicture
        } catch (_: RuntimeException) {
            null
        } finally {
            r.release()
        } ?: return null
        return decode(size) { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
    }

    /** two-pass decode: bounds first, then with a power-of-two sample size. */
    private inline fun decode(size: Int, block: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        block(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        return runCatching { block(BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
    }
}
