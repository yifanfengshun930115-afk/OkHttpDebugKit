@file:JvmName("OkHttpDebugKitExtensions")

package com.gzq.okhttpdebugkit

import android.content.Context
import okhttp3.EventListener
import okhttp3.OkHttpClient

/**
 * Installs capture hooks on an OkHttpClient.Builder.
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
