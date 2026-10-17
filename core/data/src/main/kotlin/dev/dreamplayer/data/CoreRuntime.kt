package dev.dreamplayer.data

import dev.dreamplayer.core.Native
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * the single thread every core call runs on. The core serializes everything
 * behind one global mutex, and loading a track holds it for a while — calling
 * it from the main thread would freeze the interface.
 */
internal class CoreRuntime {
    val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "dream-core") }
        .asCoroutineDispatcher()

    suspend fun <T> call(block: Native.() -> T): T = withContext(dispatcher) { Native.block() }
}
