package dev.dreamplayer.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** directories of the app and conversion of picker URIs into file paths. */
class StoragePaths internal constructor(context: Context) {
    val cache: File = context.cacheDir
    val config: File = File(context.filesDir, "config").apply { mkdirs() }
    val data: File = File(context.filesDir, "data").apply { mkdirs() }

    /** covers chosen by the user / imported from packets (app-private). */
    val covers: File = File(context.filesDir, "covers").apply { mkdirs() }

    /** the shared music directory — the core indexes it by default. */
    @Suppress("DEPRECATION")
    val music: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

    /**
     * a folder chosen with ACTION_OPEN_DOCUMENT_TREE -> a file path the core
     * can walk: `primary:Music/x` -> `/storage/emulated/0/Music/x`,
     * `XXXX-XXXX:x` -> `/storage/XXXX-XXXX/x`. null — not a local volume.
     */
    fun treeToPath(tree: Uri): String? {
        if (tree.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val volume = id.substringBefore(':')
        val rel = id.substringAfter(':', "")
        @Suppress("DEPRECATION")
        val root = when (volume) {
            "primary" -> Environment.getExternalStorageDirectory()
            "home" -> File(Environment.getExternalStorageDirectory(), "Documents")
            else -> File("/storage", volume)
        }
        return if (rel.isEmpty()) root.path else File(root, rel).path
    }

    /** `dir/name` that doesn't exist yet: `name (1).ext`, `name (2).ext`, ... */
    internal fun unique(dir: File, name: String): File {
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 0
        while (true) {
            val f = File(dir, if (n == 0) name else "$stem ($n)$ext")
            if (!f.exists()) return f
            n++
        }
    }
}
