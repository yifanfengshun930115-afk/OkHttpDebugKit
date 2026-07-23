package com.gzq.okhttpdebugkit

import android.content.Context
import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage

/**
 * OkHttpDebugKit 的全局安装入口。
 *
 * noop 产物会保留和 debug 产物一致的公开 API，方便业务侧 release 变体继续复用同一份接入代码。
 * 调用本对象的方法不会连接桌面端，也不会采集或发送网络请求数据。
 */
object OkHttpDebugKit {
    @Volatile
    private var manager: DebugConnectionManager? = null

    @Volatile
    private var config: OkHttpDebugConfig = OkHttpDebugConfig.defaults()

    /**
     * 安装 noop 版本的 OkHttpDebugKit。
     *
     * @param context Android 上下文。noop 产物不会持有或使用该对象。
     * @param config 本次安装保存的配置，用于保持 [currentConfig] 行为和 debug 产物一致。
     * @return noop 连接管理器；所有发送和连接方法都不会执行实际工作。
     */
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

    /**
     * 卸载当前 noop 实例并恢复默认配置。
     */
    @JvmStatic
    fun uninstall() {
        manager = null
        config = OkHttpDebugConfig.defaults()
    }

    /**
     * 返回当前 noop 连接管理器。
     *
     * 如果尚未调用 [install]，或已调用 [uninstall]，则返回 `null`。
     */
    @JvmStatic
    fun currentConnectionManager(): DebugConnectionManager? = manager

    /**
     * 返回当前全局配置。
     *
     * 未安装前返回 noop 默认配置；调用 [install] 后返回最近一次安装传入的配置。
     */
    @JvmStatic
    fun currentConfig(): OkHttpDebugConfig = config

    internal fun emitCapture(message: DebugCaptureMessage) = Unit
}
