package dev.dreamplayer.data

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * the single thread every core call runs on. The core serializes everything
 * behind one global mutex, and loading a track holds it for a while — calling
 * it from the main thread would freeze the interface. Calls go through
 * [native], which logs them.
 */
internal class CoreRuntime(log: CoreLog) {
    val native = CoreCalls(log)

    val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "dream-core") }
        .asCoroutineDispatcher()

    suspend fun <T> call(block: CoreCalls.() -> T): T = withContext(dispatcher) { native.block() }
}
