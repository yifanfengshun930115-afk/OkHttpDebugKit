package com.gzq.okhttpdebugkit

import android.content.Context

/**
 * Entry point for installing the debug-only OkHttp capture transport.
 */
object OkHttpDebugKit {
    @Volatile
    private var installed: Boolean = false

    fun install(context: Context, config: OkHttpDebugConfig = OkHttpDebugConfig()): Boolean {
        context.applicationContext
        installed = config.enabled
        return installed
    }

    fun isInstalled(): Boolean = installed
}

data class OkHttpDebugConfig(
    val enabled: Boolean = true,
)

