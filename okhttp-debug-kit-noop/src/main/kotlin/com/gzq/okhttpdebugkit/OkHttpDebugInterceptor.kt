package com.gzq.okhttpdebugkit

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp 请求采集拦截器。
 *
 * noop 产物中该拦截器只把请求继续交给后续链路，不会采集、读取或发送任何请求数据。
 * 保留该类是为了让业务侧在 release 变体中继续编译和 debug 产物一致的手动接入代码。
 *
 * @param config 本拦截器使用的采集配置。noop 产物不会使用该配置执行采集。
 * @param connectionManager 采集消息发送通道。noop 产物不会使用该对象发送消息。
 */
class OkHttpDebugInterceptor @JvmOverloads constructor(
    private val config: OkHttpDebugConfig = OkHttpDebugKit.currentConfig(),
    private val connectionManager: DebugConnectionManager? = OkHttpDebugKit.currentConnectionManager(),
) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
