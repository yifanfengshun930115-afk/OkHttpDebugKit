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
    return addInterceptor(OkHttpDebugInterceptor(config, manager))
        .eventListenerFactory(OkHttpDebugEventListener.factory(delegateEventListenerFactory))
}

