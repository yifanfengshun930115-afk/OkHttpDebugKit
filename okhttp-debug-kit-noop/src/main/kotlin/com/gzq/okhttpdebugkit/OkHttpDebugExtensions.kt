@file:JvmName("OkHttpDebugKitExtensions")

package com.gzq.okhttpdebugkit

import android.content.Context
import okhttp3.EventListener
import okhttp3.OkHttpClient

/**
 * No-op extension for release builds.
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
