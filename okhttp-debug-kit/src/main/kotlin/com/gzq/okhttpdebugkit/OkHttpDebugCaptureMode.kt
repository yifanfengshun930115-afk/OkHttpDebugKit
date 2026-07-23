package com.gzq.okhttpdebugkit

/**
 * 控制 OkHttpDebugKit 从哪个 OkHttp 拦截层采集请求。
 *
 * 普通业务请求优先使用 [APPLICATION]。如果项目在 OkHttp 拦截器中做加密、签名或解密，
 * 可以使用 [DUAL] 同时查看业务明文视角和实际发往服务端的视角。
 */
enum class OkHttpDebugCaptureMode {
    /**
     * 采集业务应用层视角的请求和响应。
     *
     * 这是默认模式，适合没有自定义加密、解密拦截器的大多数业务接入。
     */
    APPLICATION,

    /**
     * 采集靠近网络侧的应用拦截器视角。
     *
     * 该模式可以看到经过业务拦截器改写后的请求，以及响应被业务拦截器消费前的内容。
     */
    WIRE,

    /**
     * 同时采集 [APPLICATION] 和 [WIRE] 两个视角。
     *
     * 两条采集记录会通过同一个 groupId 关联，方便排查加密前后、解密前后的差异。
     */
    DUAL,
}
