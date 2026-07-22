package com.gzq.okhttpdebugkit

import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugHelloMessage
import java.io.Closeable

/**
 * No-op connection manager for release builds.
 */
class DebugConnectionManager @JvmOverloads constructor(
    private val config: OkHttpDebugConfig,
    private val helloMessage: DebugHelloMessage? = null,
) : Closeable {
    @Volatile
    var droppedMessageCount: Long = 0
        private set

    fun start() = Unit

    fun sendCapture(message: DebugCaptureMessage) = Unit

    fun sendHello(message: DebugHelloMessage) = Unit

    fun send(rawJson: String) = Unit

    fun queuedMessageCount(): Int = 0

    fun isConnected(): Boolean = false

    override fun close() {
        shutdown()
    }

    fun shutdown() = Unit
}
