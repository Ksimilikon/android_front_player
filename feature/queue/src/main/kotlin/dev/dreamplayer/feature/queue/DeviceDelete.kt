package dev.dreamplayer.feature.queue

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.model.Track
import dev.dreamplayer.ui.ConfirmDialog
import kotlinx.coroutines.launch

/**
 * "delete from the device" for a tab: call the returned function with the
 * tracks; it asks to confirm, then (Android 11+) the system asks the user to
 * allow deleting the files, then the files and their library tracks go.
 */
@Composable
fun rememberDeviceDelete(core: DreamCore): (List<Track>) -> Unit {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf<List<Track>>(emptyList()) }
    var waiting by remember { mutableStateOf<List<Track>>(emptyList()) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val tracks = waiting
        waiting = emptyList()
        core.launch { core.files.finishDelete(tracks, granted = res.resultCode == Activity.RESULT_OK) }
    }

    if (asking.isNotEmpty()) {
        val tracks = asking
        ConfirmDialog(
            title = if (tracks.size == 1) "Удалить с устройства?" else "Удалить с устройства ${tracks.size} песен?",
            text = if (tracks.size == 1) {
                "Файл «${tracks[0].title}» будет удалён с устройства и из библиотеки:\n${tracks[0].path ?: "—"}"
            } else {
                "Файлы будут удалены с устройства и из библиотеки."
            },
            confirmLabel = "Удалить",
            onConfirm = {
                // the system dialog is started from the main thread.
                scope.launch {
                    val sender = core.files.requestDelete(tracks)
                    if (sender != null) {
                        waiting = tracks
                        consent.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            },
            onDismiss = { asking = emptyList() },
        )
    }
    return { tracks -> if (tracks.isNotEmpty()) asking = tracks }
}
