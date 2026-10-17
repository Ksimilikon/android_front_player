package dev.dreamplayer.app

import android.app.Application
import dev.dreamplayer.data.DreamCore

/** brings the native core up once per process (the playback service needs it too). */
class DreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DreamCore.init(this)
    }
}
