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
 * EventListener that records per-call timing markers for captures.
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
        @JvmStatic
        @JvmOverloads
        fun factory(delegateFactory: EventListener.Factory? = null): EventListener.Factory =
            EventListener.Factory { call ->
                OkHttpDebugEventListener(
                    call = call,
                    delegate = delegateFactory?.create(call),
                )
            }

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

