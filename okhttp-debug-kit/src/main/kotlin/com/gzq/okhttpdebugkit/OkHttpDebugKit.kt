package com.gzq.okhttpdebugkit

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.gzq.okhttpdebugkit.protocol.DebugAppInfo
import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugDeviceInfo
import com.gzq.okhttpdebugkit.protocol.DebugHelloMessage

/**
 * Entry point for installing the debug-only OkHttp capture transport.
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
        val appContext = context.applicationContext
        val hello = createHelloMessage(appContext, config)
        val newManager = DebugConnectionManager(config, hello)
        val oldManager = manager
        manager = newManager
        this.config = config
        oldManager?.shutdown()
        if (config.enabled) {
            newManager.start()
        }
        return newManager
    }

    @JvmStatic
    fun uninstall() {
        val oldManager = manager
        manager = null
        oldManager?.shutdown()
    }

    @JvmStatic
    fun currentConnectionManager(): DebugConnectionManager? = manager

    @JvmStatic
    fun currentConfig(): OkHttpDebugConfig = config

    internal fun emitCapture(message: DebugCaptureMessage) {
        manager?.sendCapture(message)
    }

    private fun createHelloMessage(
        context: Context,
        config: OkHttpDebugConfig,
    ): DebugHelloMessage {
        val packageName = context.packageName
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
        val appInfo = context.applicationInfo
        val debuggable = appInfo?.let {
            it.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        }

        return DebugHelloMessage(
            app = DebugAppInfo(
                packageName = packageName,
                versionName = packageInfo?.versionName,
                versionCode = packageInfo?.versionCodeCompat(),
                debuggable = debuggable,
            ),
            device = DebugDeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                sdkInt = Build.VERSION.SDK_INT,
            ),
            sessionId = config.sessionId,
            token = config.token,
        )
    }
}

@Suppress("DEPRECATION")
private fun android.content.pm.PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        versionCode.toLong()
    }

