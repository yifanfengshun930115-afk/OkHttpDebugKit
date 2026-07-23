@file:JvmName("OkHttpDebugKitExtensions")

package com.gzq.okhttpdebugkit

import android.content.Context
import okhttp3.EventListener
import okhttp3.OkHttpClient

/**
 * 在当前 [OkHttpClient.Builder] 上安装 OkHttpDebugKit 采集能力。
 *
 * noop 产物中该方法不会添加采集拦截器，也不会读取请求或响应 body。若业务传入了
 * [delegateEventListenerFactory]，方法会把它设置回 builder，保证 release 变体中业务原有
 * OkHttp 事件监听仍然生效。
 *
 * @param context Android 上下文。noop 产物不会使用该对象。
 * @param config 本 client 使用的采集配置。noop 产物仅保留参数以兼容 debug 产物 API。
 * @param delegateEventListenerFactory 业务原本设置的 OkHttp 事件监听工厂。
 * @return 当前 builder，方便继续链式配置 OkHttp。
 */
@JvmOverloads
fun OkHttpClient.Builder.debugWithOkHttpDebugKit(
    context: Context,
    config: OkHttpDebugConfig = OkHttpDebugKit.currentConfig(),
    delegateEventListenerFactory: EventListener.Factory? = null,
): OkHttpClient.Builder {
    if (delegateEventListenerFactory != null) {
        eventListenerFactory(delegateEventListenerFactory)
    }
    return this
}
