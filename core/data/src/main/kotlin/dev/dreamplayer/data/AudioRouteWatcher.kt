package dev.dreamplayer.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.dreamplayer.model.LogSource
import dev.dreamplayer.model.StatusKind

/**
 * watches the audio outputs for the whole process (not only while the
 * playback service exists): an external output going away pauses playback, a
 * new one gets the sound moved to it (see [PlayerController.onOutputAdded]).
 * Two signals for "unplugged", since either alone can be missed: the device
 * callback and the system's "becoming noisy" broadcast.
 */
internal class AudioRouteWatcher(
    private val context: Context,
    private val player: PlayerController,
    private val log: CoreLog,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** registering reports every connected device once — that's not a change. */
    private var initialReport = true

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<AudioDeviceInfo>) {
            if (initialReport) {
                initialReport = false
                return
            }
            added.firstOrNull(::isExternalOutput)?.let {
                log.add(LogSource.System, StatusKind.Info, "подключено устройство вывода: ${describe(it)}")
                player.onOutputAdded(label(it))
            }
        }

        override fun onAudioDevicesRemoved(removed: Array<AudioDeviceInfo>) {
            removed.firstOrNull(::isExternalOutput)?.let {
                log.add(LogSource.System, StatusKind.Info, "отключено устройство вывода: ${describe(it)}")
                player.onOutputRemoved(label(it))
            }
        }
    }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            log.add(LogSource.System, StatusKind.Info, "система: AUDIO_BECOMING_NOISY")
            player.onOutputRemoved("наушники")
        }
    }

    fun start() {
        audio.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        // a protected broadcast: only the system can send it, exporting is safe
        // (and avoids any doubt about system broadcasts reaching the receiver).
        ContextCompat.registerReceiver(
            context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun isExternalOutput(d: AudioDeviceInfo): Boolean = d.isSink && d.type in EXTERNAL

    private fun describe(d: AudioDeviceInfo): String = "${label(d)} (тип ${d.type}, \"${d.productName}\", id ${d.id})"

    private fun label(d: AudioDeviceInfo): String = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, BLE_HEADSET, BLE_SPEAKER -> d.productName?.toString()?.ifBlank { null } ?: "Bluetooth"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-аудио"
        else -> "наушники"
    }

    private companion object {
        // API 31 constants, spelled out for minSdk 26.
        const val BLE_HEADSET = 26
        const val BLE_SPEAKER = 27

        /** outputs that can come and go (not built into the phone). */
        val EXTERNAL = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_HEARING_AID,
            BLE_HEADSET,
            BLE_SPEAKER,
        )
    }
}
