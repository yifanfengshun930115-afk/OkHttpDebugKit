package com.gzq.okhttpdebugkit

import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugHelloMessage
import java.io.Closeable

/**
 * 负责维护和桌面端采集服务之间的 WebSocket 连接。
 *
 * noop 产物中该类不会创建 WebSocket、缓存消息或发送采集数据。保留该类是为了让业务侧 release
 * 变体继续编译和 debug 产物一致的连接状态查询、关闭等接入代码。
 *
 * @param config 连接和发送使用的配置。noop 产物不会使用该配置建立连接。
 * @param helloMessage WebSocket 握手消息。noop 产物不会发送该消息。
 */
class DebugConnectionManager @JvmOverloads constructor(
    private val config: OkHttpDebugConfig,
    private val helloMessage: DebugHelloMessage? = null,
) : Closeable {
    /**
     * 因离线队列容量不足而丢弃的消息数量。
     *
     * noop 产物不会入队或发送消息，该值固定为 `0`。
     */
    @Volatile
    var droppedMessageCount: Long = 0
        private set

    /**
     * 启动 WebSocket 连接。
     *
     * noop 产物中该方法不执行任何操作。
     */
    fun start() = Unit

    /**
     * 发送一条已经结构化的请求采集消息。
     *
     * noop 产物中该方法不执行任何操作。
     */
    fun sendCapture(message: DebugCaptureMessage) = Unit

    /**
     * 发送一条桌面端握手消息。
     *
     * noop 产物中该方法不执行任何操作。
     */
    fun sendHello(message: DebugHelloMessage) = Unit

    /**
     * 发送一段原始 JSON 文本到桌面端。
     *
     * noop 产物中该方法不执行任何操作。
     */
    fun send(rawJson: String) = Unit

    /**
     * 返回当前离线队列中等待发送的消息数量。
     *
     * noop 产物不会缓存消息，固定返回 `0`。
     */
    fun queuedMessageCount(): Int = 0

    /**
     * 返回当前是否已经建立 WebSocket 连接。
     *
     * noop 产物不会建立连接，固定返回 `false`。
     */
    fun isConnected(): Boolean = false

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
     * noop 产物中该方法不执行任何操作。
     */
    fun shutdown() = Unit
}
