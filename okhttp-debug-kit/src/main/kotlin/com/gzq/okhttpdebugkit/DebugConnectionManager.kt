package com.gzq.okhttpdebugkit

import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugHelloMessage
import com.gzq.okhttpdebugkit.protocol.DebugJsonProtocol
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.net.URLEncoder
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Owns the WebSocket session used to push OkHttp captures to the desktop collector.
 *
 * Failures are deliberately swallowed after queueing/drop accounting so network debugging never
 * changes app behavior.
 */
class DebugConnectionManager @JvmOverloads constructor(
    private val config: OkHttpDebugConfig,
    private val helloMessage: DebugHelloMessage? = null,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) : Closeable {
    private val lock = Any()
    private val queue = ArrayDeque<String>()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "OkHttpDebugKit-WebSocket").apply { isDaemon = true }
    }

    @Volatile
    private var started = false

    @Volatile
    private var closed = false

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var connecting = false

    @Volatile
    private var reconnectAttempt = 0

    @Volatile
    var droppedMessageCount: Long = 0
        private set

    fun start() {
        if (!config.enabled) return
        synchronized(lock) {
            if (started || closed) return
            started = true
        }
        executeSafely { connectIfNeeded() }
    }

    fun sendCapture(message: DebugCaptureMessage) {
        send(DebugJsonProtocol.captureToJson(message))
    }

    fun sendHello(message: DebugHelloMessage) {
        send(DebugJsonProtocol.helloToJson(message))
    }

    fun send(rawJson: String) {
        if (!config.enabled || closed) return
        var shouldConnect = false
        synchronized(lock) {
            val openSocket = socket
            if (openSocket != null) {
                if (openSocket.send(rawJson)) {
                    return
                }
                socket = null
            }
            enqueueLocked(rawJson)
            shouldConnect = started && !connecting
        }
        if (shouldConnect) {
            executeSafely { connectIfNeeded() }
        }
    }

    fun queuedMessageCount(): Int = synchronized(lock) { queue.size }

    fun isConnected(): Boolean = socket != null

    override fun close() {
        shutdown()
    }

    fun shutdown() {
        val socketToClose = synchronized(lock) {
            if (closed) return
            closed = true
            started = false
            connecting = false
            val current = socket
            socket = null
            queue.clear()
            current
        }
        socketToClose?.close(1000, "OkHttpDebugKit shutdown")
        executor.shutdownNow()
    }

    private fun connectIfNeeded() {
        val request = synchronized(lock) {
            if (closed || !started || connecting || socket != null) {
                return
            }
            connecting = true
            Request.Builder()
                .url(endpointWithToken(config.serverUrl, config.token))
                .build()
        }
        client.newWebSocket(request, Listener())
    }

    private fun handleOpen(webSocket: WebSocket) {
        val pending = mutableListOf<String>()
        synchronized(lock) {
            socket = webSocket
            connecting = false
            reconnectAttempt = 0
            helloMessage?.let { pending += DebugJsonProtocol.helloToJson(it) }
            while (queue.isNotEmpty()) {
                pending += queue.removeFirst()
            }
        }
        pending.forEach { message ->
            if (!webSocket.send(message)) {
                send(message)
            }
        }
    }

    private fun handleDisconnected(webSocket: WebSocket?) {
        var shouldReconnect = false
        synchronized(lock) {
            if (socket === webSocket || webSocket == null) {
                socket = null
            }
            connecting = false
            shouldReconnect = started && !closed
        }
        if (shouldReconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        val delayMs = synchronized(lock) {
            reconnectAttempt += 1
            min(
                config.reconnectMaxDelayMs,
                config.reconnectInitialDelayMs * (1L shl min(reconnectAttempt - 1, 10)),
            )
        }
        runCatching {
            executor.schedule({ connectIfNeeded() }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun enqueueLocked(rawJson: String) {
        if (config.queueCapacity == 0) {
            droppedMessageCount += 1
            return
        }
        while (queue.size >= config.queueCapacity) {
            queue.removeFirst()
            droppedMessageCount += 1
        }
        queue.addLast(rawJson)
    }

    private fun executeSafely(block: () -> Unit) {
        runCatching {
            executor.execute {
                runCatching(block)
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            handleOpen(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDisconnected(webSocket)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleDisconnected(webSocket)
        }
    }
}

private fun endpointWithToken(serverUrl: String, token: String?): String {
    val nonBlankToken = token?.takeIf { it.isNotBlank() } ?: return serverUrl
    if (serverUrl.contains("?token=") || serverUrl.contains("&token=")) {
        return serverUrl
    }
    val separator = if (serverUrl.contains("?")) "&" else "?"
    val encodedToken = URLEncoder.encode(nonBlankToken, "UTF-8")
    return "$serverUrl${separator}token=$encodedToken"
}

