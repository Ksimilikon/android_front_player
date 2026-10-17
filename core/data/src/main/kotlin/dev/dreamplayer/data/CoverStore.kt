package dev.dreamplayer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * covers picked in the system picker are content URIs; the core stores a file
 * path, so the picture is copied into the app's covers directory first.
 */
class CoverStore internal constructor(
    private val context: Context,
    private val paths: StoragePaths,
) {
    /** copies [uri] and returns the path of the copy (null — failed). */
    suspend fun importCover(uri: Uri): String? = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "cover.jpg"
        val target = paths.unique(paths.covers, name)
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
