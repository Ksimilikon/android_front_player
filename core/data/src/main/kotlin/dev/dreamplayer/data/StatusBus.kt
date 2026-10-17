package dev.dreamplayer.data

import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** one-off messages for the status banner (the TUI status line). */
class StatusBus {
    private val flow = MutableSharedFlow<StatusMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<StatusMessage> = flow

    fun post(text: String, kind: StatusKind = StatusKind.Info) {
        flow.tryEmit(StatusMessage(text, kind))
    }

    fun info(text: String) = post(text, StatusKind.Info)
    fun ok(text: String) = post(text, StatusKind.Ok)
    fun warn(text: String) = post(text, StatusKind.Warn)
    fun error(text: String) = post(text, StatusKind.Error)
}
