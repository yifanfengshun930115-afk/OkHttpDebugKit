package com.gzq.okhttpdebugkit

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import com.gzq.okhttpdebugkit.protocol.DebugAppInfo
import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugDeviceInfo
import com.gzq.okhttpdebugkit.protocol.DebugHelloMessage
import java.security.MessageDigest

/**
 * OkHttpDebugKit 的全局安装入口。
 *
 * 业务侧通常在 Application 初始化阶段调用 [install]，再通过
 * [debugWithOkHttpDebugKit] 给具体的 [okhttp3.OkHttpClient.Builder] 安装采集拦截器。
 * 重复安装会关闭旧连接并使用新配置重新建立桌面端连接。
 */
object OkHttpDebugKit {
    @Volatile
    private var manager: DebugConnectionManager? = null

    @Volatile
    private var config: OkHttpDebugConfig = OkHttpDebugConfig.defaults()

    /**
     * 安装 OkHttpDebugKit，并在配置启用时连接桌面端采集服务。
     *
     * @param context Android 上下文。方法内部会使用 applicationContext，避免持有 Activity。
     * @param config 本次安装使用的采集配置。
     * @return 当前安装产生的连接管理器，可用于查看连接状态或主动关闭连接。
     */
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

    /**
     * 卸载当前 OkHttpDebugKit 实例并关闭 WebSocket 连接。
     *
     * 已经创建出来的 OkHttpClient 不会自动移除拦截器；业务侧如果需要彻底停止采集，
     * 应同时停止继续复用旧 client 或重新创建未安装调试拦截器的 client。
     */
    @JvmStatic
    fun uninstall() {
        val oldManager = manager
        manager = null
        oldManager?.shutdown()
    }

    /**
     * 返回当前连接管理器。
     *
     * 如果尚未调用 [install]，或已调用 [uninstall]，则返回 `null`。
     */
    @JvmStatic
    fun currentConnectionManager(): DebugConnectionManager? = manager

    /**
     * 返回当前全局配置。
     *
     * 未安装前返回默认配置；调用 [install] 后返回最近一次安装传入的配置。
     */
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
                deviceTag = context.safeDeviceTag(),
            ),
            sessionId = config.sessionId,
            token = config.token,
            clientTag = config.clientTag ?: config.staticTags.clientTagFromStaticTags(),
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

private fun Context.safeDeviceTag(): String? {
    val androidId = runCatching {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    return androidId?.let { "android:${it.sha256ShortHex()}" }
}

private fun String.sha256ShortHex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.take(6).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun Map<String, String>.clientTagFromStaticTags(): String? {
    val preferredKeys = listOf("clientTag", "staticTag", "source", "app", "flavor", "channel")
    for (key in preferredKeys) {
        val value = entries
            .firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (value != null) {
            return value
        }
    }
    return null
}
