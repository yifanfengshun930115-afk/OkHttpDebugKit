package com.gzq.okhttpdebugkit

/**
 * 控制 OkHttpDebugKit 从哪个 OkHttp 拦截层采集请求。
 *
 * noop 产物会保留和 debug 产物一致的枚举值，方便业务侧在 release 变体中继续编译同一份接入代码。
 * 在 noop 产物中，该枚举只保存配置含义，不会触发实际采集。
 */
enum class OkHttpDebugCaptureMode {
    /**
     * 业务应用层视角。noop 产物不会真正采集。
     */
    APPLICATION,

    /**
     * 靠近网络侧的应用拦截器视角。noop 产物不会真正采集。
     */
    WIRE,

    /**
     * 双视角模式。noop 产物不会真正采集。
     */
    DUAL,
}
