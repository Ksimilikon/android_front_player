package dev.dreamplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.dreamplayer.data.DreamCore
import dev.dreamplayer.playback.PlaybackConnection
import dev.dreamplayer.ui.theme.DreamTheme

class MainActivity : ComponentActivity() {
    private val core get() = DreamCore.get
    private var connection: PlaybackConnection? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // the app is always dark: light system bar icons regardless of the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // the tab host lives with the activity instance (editor states included).
        val host = (lastNonConfigurationInstance as? TabHost) ?: TabHost(core)
        hostRef = host
        setContent {
            DreamTheme { RootScreen(core, host) }
        }
        requestPermissions()
    }

    private var hostRef: TabHost? = null

    @Deprecated("kept only to carry the tab host across configuration changes")
    override fun onRetainCustomNonConfigurationInstance(): Any? = hostRef

    override fun onStart() {
        super.onStart()
        connection = PlaybackConnection(this)
    }

    override fun onStop() {
        connection?.close()
        connection = null
        super.onStop()
    }

    private fun requestPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) onPermissions() else permissions.launch(wanted.toTypedArray())
    }

    /** index the music directory once per process, when reading it is allowed. */
    private fun onPermissions() {
        val audio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, audio) != PackageManager.PERMISSION_GRANTED) {
            core.status.warn("нет доступа к музыке — библиотека не будет просканирована")
            return
        }
        if (!scanned) {
            scanned = true
            core.scanMusicOnStart()
        }
    }

    private companion object {
        var scanned = false
    }
}
