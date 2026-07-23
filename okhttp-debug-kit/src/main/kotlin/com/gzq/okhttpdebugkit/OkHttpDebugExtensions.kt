@file:JvmName("OkHttpDebugKitExtensions")

package com.gzq.okhttpdebugkit

import android.content.Context
import okhttp3.EventListener
import okhttp3.OkHttpClient

/**
 * 在当前 [OkHttpClient.Builder] 上安装 OkHttpDebugKit 采集能力。
 *
 * 业务侧推荐优先使用这个扩展函数，而不是手动添加 [OkHttpDebugInterceptor] 和
 * [OkHttpDebugEventListener]。方法会根据 [OkHttpDebugConfig.captureMode] 自动选择采集层：
 * [OkHttpDebugCaptureMode.APPLICATION] 采集业务明文视角，[OkHttpDebugCaptureMode.WIRE]
 * 采集靠近网络侧的视角，[OkHttpDebugCaptureMode.DUAL] 同时采集两者。
 *
 * @param context Android 上下文。尚未全局安装时会用它自动调用 [OkHttpDebugKit.install]。
 * @param config 本 client 使用的采集配置，默认读取 [OkHttpDebugKit.currentConfig]。
 * @param delegateEventListenerFactory 业务原本设置的 OkHttp 事件监听工厂。传入后会继续转发事件，
 * 避免调试计时监听覆盖业务已有的监听逻辑。
 * @return 当前 builder，方便继续链式配置 OkHttp。
 */
@JvmOverloads
fun OkHttpClient.Builder.debugWithOkHttpDebugKit(
    context: Context,
    config: OkHttpDebugConfig = OkHttpDebugKit.currentConfig(),
    delegateEventListenerFactory: EventListener.Factory? = null,
): OkHttpClient.Builder {
    if (!config.enabled) {
        return this
    }
    val manager = OkHttpDebugKit.currentConnectionManager() ?: OkHttpDebugKit.install(context, config)
    when (config.captureMode) {
        OkHttpDebugCaptureMode.APPLICATION -> {
            interceptors().add(0, OkHttpDebugInterceptor(config, manager, STAGE_PLAIN))
        }
        OkHttpDebugCaptureMode.WIRE -> {
            addInterceptor(OkHttpDebugInterceptor(config, manager, STAGE_WIRE))
        }
        OkHttpDebugCaptureMode.DUAL -> {
            interceptors().add(0, OkHttpDebugInterceptor(config, manager, STAGE_PLAIN))
            addInterceptor(OkHttpDebugInterceptor(config, manager, STAGE_WIRE))
        }
    }
    return eventListenerFactory(OkHttpDebugEventListener.factory(delegateEventListenerFactory))
}
