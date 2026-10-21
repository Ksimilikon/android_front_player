package dev.dreamplayer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * the app's covers directory: covers picked in the system picker are content
 * URIs while the core stores a file path, so the picture is copied here
 * first; covers received in packets / by sync land here too. A file no track
 * and no playlist references any more is deleted ([sweep]).
 */
class CoverStore internal constructor(
    private val context: Context,
    private val paths: StoragePaths,
) {
    /**
     * covers written in this run that the library may not reference yet (a
     * pick the editor hasn't saved, a sync not committed): the sweep leaves
     * them until it sees them referenced. Never referenced — they go with the
     * first sweep of the next run.
     */
    private val held: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** a new file in the covers directory, held from the sweep before anything is written into it. */
    internal fun newFile(name: String): File = paths.unique(paths.covers, name).also { held.add(it.canonicalPath) }

    /**
     * deletes the files of the covers directory that aren't in [inUse] (the
     * cover paths of every track and playlist) and aren't held.
     */
    internal suspend fun sweep(inUse: Collection<String>) = withContext(Dispatchers.IO) {
        val used = inUse.mapTo(HashSet()) { File(it).canonicalPath }
        // referenced now: from here on its fate is the library's.
        held.removeAll(used)
        for (f in paths.covers.listFiles().orEmpty()) {
            if (f.isFile && f.canonicalPath !in used && f.canonicalPath !in held) f.delete()
        }
    }

    /** copies [uri] and returns the path of the copy (null — failed). */
    suspend fun importCover(uri: Uri): String? = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "cover.jpg"
        val target = newFile(name)
        runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target.path
        }.getOrElse {
            target.delete()
            null
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.replace('/', '_') else null
        }
}
