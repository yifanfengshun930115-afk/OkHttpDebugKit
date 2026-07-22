package com.gzq.okhttpdebugkit

import okhttp3.Call
import okhttp3.EventListener

/**
 * No-op event listener for release builds.
 */
class OkHttpDebugEventListener @JvmOverloads constructor(
    private val call: Call? = null,
    private val delegate: EventListener? = null,
) : EventListener() {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun factory(delegateFactory: EventListener.Factory? = null): EventListener.Factory =
            EventListener.Factory { call ->
                delegateFactory?.create(call) ?: OkHttpDebugEventListener(call = call)
            }

        @JvmStatic
        fun snapshot(call: Call): Map<String, Any?>? = null
    }
}
