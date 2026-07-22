package com.gzq.okhttpdebugkit

import android.content.Context
import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage

/**
 * Release-safe entry point. Calls are intentionally accepted and ignored.
 */
object OkHttpDebugKit {
    @Volatile
    private var manager: DebugConnectionManager? = null

    @Volatile
    private var config: OkHttpDebugConfig = OkHttpDebugConfig.defaults()

    @JvmStatic
    @JvmOverloads
    fun install(
        context: Context,
        config: OkHttpDebugConfig = OkHttpDebugConfig.defaults(),
    ): DebugConnectionManager {
        val newManager = DebugConnectionManager(config)
        manager = newManager
        this.config = config
        return newManager
    }

    @JvmStatic
    fun uninstall() {
        manager = null
        config = OkHttpDebugConfig.defaults()
    }

    @JvmStatic
    fun currentConnectionManager(): DebugConnectionManager? = manager

    @JvmStatic
    fun currentConfig(): OkHttpDebugConfig = config

    internal fun emitCapture(message: DebugCaptureMessage) = Unit
}
