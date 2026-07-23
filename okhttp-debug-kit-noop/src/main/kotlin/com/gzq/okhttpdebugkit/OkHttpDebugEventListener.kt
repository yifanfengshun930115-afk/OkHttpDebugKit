package com.gzq.okhttpdebugkit

import okhttp3.Call
import okhttp3.EventListener

/**
 * 用于记录 OkHttp 单次请求耗时明细的事件监听器。
 *
 * noop 产物中该监听器不会记录计时数据。保留该类是为了让业务侧 release 变体继续编译和 debug
 * 产物一致的接入代码。
 *
 * @param call 当前 OkHttp 请求。noop 产物不会保存该请求的计时状态。
 * @param delegate 业务已有的事件监听器。noop 产物通过 [factory] 创建时会优先返回业务监听器。
 */
class OkHttpDebugEventListener @JvmOverloads constructor(
    private val call: Call? = null,
    private val delegate: EventListener? = null,
) : EventListener() {
    companion object {
        /**
         * 创建 OkHttp 可直接使用的 [EventListener.Factory]。
         *
         * @param delegateFactory 业务已有的事件监听工厂。非空时会直接返回业务监听器，避免影响
         * release 变体中的原有 OkHttp 行为。
         */
        @JvmStatic
        @JvmOverloads
        fun factory(delegateFactory: EventListener.Factory? = null): EventListener.Factory =
            EventListener.Factory { call ->
                delegateFactory?.create(call) ?: OkHttpDebugEventListener(call = call)
            }

        /**
         * 获取指定请求当前已记录的计时快照。
         *
         * noop 产物不会记录计时数据，固定返回 `null`。
         */
        @JvmStatic
        fun snapshot(call: Call): Map<String, Any?>? = null
    }
}
