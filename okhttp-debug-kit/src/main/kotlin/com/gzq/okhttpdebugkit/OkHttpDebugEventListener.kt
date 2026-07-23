package com.gzq.okhttpdebugkit

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Collections
import java.util.WeakHashMap

/**
 * 用于记录 OkHttp 单次请求耗时明细的事件监听器。
 *
 * 业务侧通常不需要直接创建该类，优先通过 [debugWithOkHttpDebugKit] 或 [factory] 安装。
 * 如果业务本身已经设置了 [EventListener]，可以通过 [factory] 的 delegate 参数继续保留原有监听逻辑。
 *
 * @param call 当前 OkHttp 请求。传入后监听器会把计时状态和该请求绑定。
 * @param delegate 业务已有的事件监听器。非空时，所有事件都会继续转发给它。
 */
class OkHttpDebugEventListener @JvmOverloads constructor(
    private val call: Call? = null,
    private val delegate: EventListener? = null,
) : EventListener() {
    private val timingState = TimingState()

    init {
        call?.let { TimingRegistry.register(it, timingState) }
    }

    override fun callStart(call: Call) {
        timingState.mark("callStart")
        delegate?.callStart(call)
    }

    override fun dnsStart(call: Call, domainName: String) {
        timingState.mark("dnsStart")
        delegate?.dnsStart(call, domainName)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        timingState.mark("dnsEnd")
        timingState.put("dnsAddressCount", inetAddressList.size)
        delegate?.dnsEnd(call, domainName, inetAddressList)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        timingState.mark("connectStart")
        delegate?.connectStart(call, inetSocketAddress, proxy)
    }

    override fun secureConnectStart(call: Call) {
        timingState.mark("secureConnectStart")
        delegate?.secureConnectStart(call)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        timingState.mark("secureConnectEnd")
        delegate?.secureConnectEnd(call, handshake)
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        timingState.mark("connectEnd")
        protocol?.let { timingState.put("protocol", it.toString()) }
        delegate?.connectEnd(call, inetSocketAddress, proxy, protocol)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        timingState.mark("connectFailed")
        timingState.put("connectError", ioe.javaClass.name)
        delegate?.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
    }

    override fun requestHeadersStart(call: Call) {
        timingState.mark("requestHeadersStart")
        delegate?.requestHeadersStart(call)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        timingState.mark("requestHeadersEnd")
        delegate?.requestHeadersEnd(call, request)
    }

    override fun requestBodyStart(call: Call) {
        timingState.mark("requestBodyStart")
        delegate?.requestBodyStart(call)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        timingState.mark("requestBodyEnd")
        timingState.put("requestBodyBytes", byteCount)
        delegate?.requestBodyEnd(call, byteCount)
    }

    override fun responseHeadersStart(call: Call) {
        timingState.mark("responseHeadersStart")
        delegate?.responseHeadersStart(call)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        timingState.mark("responseHeadersEnd")
        delegate?.responseHeadersEnd(call, response)
    }

    override fun responseBodyStart(call: Call) {
        timingState.mark("responseBodyStart")
        delegate?.responseBodyStart(call)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        timingState.mark("responseBodyEnd")
        timingState.put("responseBodyBytes", byteCount)
        delegate?.responseBodyEnd(call, byteCount)
    }

    override fun callEnd(call: Call) {
        timingState.mark("callEnd")
        delegate?.callEnd(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        timingState.mark("callFailed")
        timingState.put("callError", ioe.javaClass.name)
        delegate?.callFailed(call, ioe)
    }

    companion object {
        /**
         * 创建 OkHttp 可直接使用的 [EventListener.Factory]。
         *
         * @param delegateFactory 业务已有的事件监听工厂。非空时，每个请求都会先创建业务监听器，
         * 再由 OkHttpDebugKit 包装并转发事件。
         */
        @JvmStatic
        @JvmOverloads
        fun factory(delegateFactory: EventListener.Factory? = null): EventListener.Factory =
            EventListener.Factory { call ->
                OkHttpDebugEventListener(
                    call = call,
                    delegate = delegateFactory?.create(call),
                )
            }

        /**
         * 获取指定请求当前已记录的计时快照。
         *
         * 返回值会被采集拦截器附加到请求记录中。业务侧一般不需要主动调用，除非需要自行读取
         * dns、连接、TLS、请求体、响应体等阶段耗时。
         *
         * @return 没有计时数据时返回 `null`。
         */
        @JvmStatic
        fun snapshot(call: Call): Map<String, Any?>? =
            TimingRegistry.snapshot(call).takeIf { it.isNotEmpty() }
    }
}

private object TimingRegistry {
    private val states: MutableMap<Call, TimingState> = Collections.synchronizedMap(WeakHashMap())

    fun register(call: Call, state: TimingState) {
        states[call] = state
    }

    fun snapshot(call: Call): Map<String, Any?> =
        states[call]?.snapshot() ?: emptyMap()
}

private class TimingState {
    private val marks = linkedMapOf<String, Long>()
    private val values = linkedMapOf<String, Any?>()

    @Synchronized
    fun mark(name: String) {
        marks[name] = System.nanoTime()
    }

    @Synchronized
    fun put(name: String, value: Any?) {
        values[name] = value
    }

    @Synchronized
    fun snapshot(): Map<String, Any?> {
        val output = linkedMapOf<String, Any?>()
        output.putAll(values)
        addDuration(output, "dnsDurationMs", "dnsStart", "dnsEnd")
        addDuration(output, "connectDurationMs", "connectStart", "connectEnd")
        addDuration(output, "secureConnectDurationMs", "secureConnectStart", "secureConnectEnd")
        addDuration(output, "requestHeadersDurationMs", "requestHeadersStart", "requestHeadersEnd")
        addDuration(output, "requestBodyDurationMs", "requestBodyStart", "requestBodyEnd")
        addDuration(output, "responseHeadersDurationMs", "responseHeadersStart", "responseHeadersEnd")
        addDuration(output, "responseBodyDurationMs", "responseBodyStart", "responseBodyEnd")

        val callStart = marks["callStart"]
        if (callStart != null) {
            val end = marks["callEnd"] ?: marks["callFailed"] ?: System.nanoTime()
            output["callDurationMs"] = (end - callStart) / 1_000_000L
        }
        return output
    }

    private fun addDuration(
        output: MutableMap<String, Any?>,
        name: String,
        startName: String,
        endName: String,
    ) {
        val start = marks[startName] ?: return
        val end = marks[endName] ?: return
        output[name] = (end - start) / 1_000_000L
    }
}
