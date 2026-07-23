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
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * 负责维护和桌面端采集服务之间的 WebSocket 连接。
 *
 * 业务侧通常通过 [OkHttpDebugKit.install] 获取该对象，用于查看连接状态、队列积压数量或主动关闭连接。
 * 连接失败、发送失败等异常会在内部吞掉，并通过离线队列或丢弃计数处理，避免调试能力改变业务网络行为。
 *
 * @param config 连接和发送使用的配置。
 * @param helloMessage WebSocket 建立成功后优先发送的握手消息。
 * @param client 创建 WebSocket 使用的 OkHttpClient，通常使用默认值即可。
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
    private var endpointIndex = 0

    /**
     * 因离线队列容量不足而丢弃的消息数量。
     *
     * 该值只递增不自动清零，可用于判断桌面端长时间未连接时是否发生了采集数据丢弃。
     */
    @Volatile
    var droppedMessageCount: Long = 0
        private set

    /**
     * 启动 WebSocket 连接。
     *
     * [OkHttpDebugKit.install] 会在配置启用时自动调用该方法，业务侧一般不需要重复调用。
     */
    fun start() {
        if (!config.enabled) return
        synchronized(lock) {
            if (started || closed) return
            started = true
        }
        executeSafely { connectIfNeeded() }
    }

    /**
     * 发送一条已经结构化的请求采集消息。
     *
     * 如果当前未连接，会按 [OkHttpDebugConfig.queueCapacity] 进入离线队列。
     */
    fun sendCapture(message: DebugCaptureMessage) {
        send(DebugJsonProtocol.captureToJson(message))
    }

    /**
     * 发送一条桌面端握手消息。
     *
     * 通常由连接管理器在 WebSocket 建立成功后自动发送，业务侧一般不需要主动调用。
     */
    fun sendHello(message: DebugHelloMessage) {
        send(DebugJsonProtocol.helloToJson(message))
    }

    /**
     * 发送一段原始 JSON 文本到桌面端。
     *
     * 该方法主要提供给内部协议发送使用。业务侧如果直接调用，需要保证 JSON 符合桌面端协议。
     */
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

    /**
     * 返回当前离线队列中等待发送的消息数量。
     */
    fun queuedMessageCount(): Int = synchronized(lock) { queue.size }

    /**
     * 返回当前是否已经建立 WebSocket 连接。
     */
    fun isConnected(): Boolean = socket != null

    /**
     * 关闭连接管理器。
     *
     * 等价于调用 [shutdown]。
     */
    override fun close() {
        shutdown()
    }

    /**
     * 关闭 WebSocket、清空离线队列并停止后台线程。
     *
     * 调用后该对象不可再次启动；如需重新采集，请重新调用 [OkHttpDebugKit.install]。
     */
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
                .url(nextEndpointLocked())
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
            endpointIndex = 0
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

    private fun nextEndpointLocked(): String {
        val endpoints = config.serverUrls.ifEmpty { listOf(config.serverUrl) }
        val endpoint = endpoints[endpointIndex % endpoints.size]
        endpointIndex = (endpointIndex + 1) % endpoints.size
        return endpoint
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
