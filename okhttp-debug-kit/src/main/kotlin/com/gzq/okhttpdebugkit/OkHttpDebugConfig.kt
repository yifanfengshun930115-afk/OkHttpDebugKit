package com.gzq.okhttpdebugkit

/**
 * OkHttpDebugKit 的运行配置。
 *
 * 业务侧通常在 debug、内测或日志包的依赖注入入口中创建该配置，然后传给
 * [OkHttpDebugKit.install] 或 [debugWithOkHttpDebugKit]。正式 release 包建议依赖 noop
 * 产物，即使保留同样的配置代码也不会采集或上报请求数据。
 *
 * @property enabled 是否启用采集。关闭后不会安装拦截器逻辑，也不会连接桌面端。
 * @property serverUrl 单个桌面端 WebSocket 地址。保留该字段是为了兼容旧接入代码。
 * @property serverUrls 可轮询尝试的桌面端 WebSocket 地址列表，适合桌面端端口自动探测场景。
 * @property clientTag 当前客户端在桌面端展示和筛选时使用的稳定标签。
 * @property maxBodyBytes 单个请求体或响应体最多采集的字节数，超过后会截断。
 * @property queueCapacity WebSocket 未连接时最多缓存的消息数，超过后丢弃最旧消息。
 * @property reconnectInitialDelayMs 断线后第一次重连等待时间，单位毫秒。
 * @property reconnectMaxDelayMs 断线重连的最大等待时间，单位毫秒。
 * @property redactHeaders 需要脱敏的 HTTP Header 名称集合，大小写不敏感。
 * @property redactQueryParams 需要脱敏的 URL query 参数名集合，大小写不敏感。
 * @property includeStackTrace 请求失败时是否把异常堆栈发送到桌面端。
 * @property captureMode OkHttp 拦截层采集模式，决定采集明文视角、线上传输视角或两者都采。
 * @property captureTransformers 业务派生采集扩展点，可额外发送业务解密、模型转换等自定义阶段。
 */
class OkHttpDebugConfig private constructor(
    val enabled: Boolean,
    val serverUrl: String,
    val serverUrls: List<String>,
    val clientTag: String?,
    val maxBodyBytes: Long,
    val queueCapacity: Int,
    val reconnectInitialDelayMs: Long,
    val reconnectMaxDelayMs: Long,
    val redactHeaders: Set<String>,
    val redactQueryParams: Set<String>,
    val includeStackTrace: Boolean,
    val captureMode: OkHttpDebugCaptureMode,
    val captureTransformers: List<OkHttpDebugCaptureTransformer>,
) {
    /**
     * 基于当前配置创建一个可继续修改的 [Builder]。
     */
    fun newBuilder(): Builder = Builder(this)

    /**
     * [OkHttpDebugConfig] 的构建器。
     *
     * 所有 setter 都会返回自身，方便业务侧用链式调用组装配置。
     */
    class Builder {
        private var enabled: Boolean = true
        private var serverUrl: String = DEFAULT_SERVER_URL
        private var serverUrls: List<String> = listOf(DEFAULT_SERVER_URL)
        private var clientTag: String? = null
        private var maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES
        private var queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
        private var reconnectInitialDelayMs: Long = DEFAULT_RECONNECT_INITIAL_DELAY_MS
        private var reconnectMaxDelayMs: Long = DEFAULT_RECONNECT_MAX_DELAY_MS
        private var redactHeaders: Set<String> = DEFAULT_REDACT_HEADERS
        private var redactQueryParams: Set<String> = DEFAULT_REDACT_QUERY_PARAMS
        private var includeStackTrace: Boolean = false
        private var captureMode: OkHttpDebugCaptureMode = OkHttpDebugCaptureMode.APPLICATION
        private var captureTransformers: List<OkHttpDebugCaptureTransformer> = emptyList()

        /**
         * 创建一份默认配置。
         */
        constructor()

        internal constructor(config: OkHttpDebugConfig) {
            enabled = config.enabled
            serverUrl = config.serverUrl
            serverUrls = config.serverUrls
            clientTag = config.clientTag
            maxBodyBytes = config.maxBodyBytes
            queueCapacity = config.queueCapacity
            reconnectInitialDelayMs = config.reconnectInitialDelayMs
            reconnectMaxDelayMs = config.reconnectMaxDelayMs
            redactHeaders = config.redactHeaders
            redactQueryParams = config.redactQueryParams
            includeStackTrace = config.includeStackTrace
            captureMode = config.captureMode
            captureTransformers = config.captureTransformers
        }

        /**
         * 设置是否启用采集。
         *
         * 传入 `false` 时，debug 产物也会跳过采集和 WebSocket 连接。
         */
        fun enabled(value: Boolean) = apply { enabled = value }

        /**
         * 设置单个桌面端 WebSocket 地址，例如 `ws://127.0.0.1:19090/session`。
         *
         * 调用该方法会覆盖 [serverUrls] 中的地址列表。
         */
        fun serverUrl(value: String) = apply {
            serverUrl = value
            serverUrls = listOf(value)
        }

        /**
         * 设置可轮询尝试的桌面端 WebSocket 地址列表。
         *
         * 地址必须以 `ws://` 或 `wss://` 开头。最终构建时会去掉空白项并去重。
         */
        fun serverUrls(value: List<String>) = apply {
            require(value.isNotEmpty()) { "serverUrls must not be empty" }
            serverUrls = value
            serverUrl = value.first()
        }

        /**
         * 设置桌面端展示和筛选客户端来源时使用的稳定标签。
         */
        fun clientTag(value: String?) = apply { clientTag = value?.trim()?.takeIf { it.isNotEmpty() } }

        /**
         * 设置请求体和响应体的最大采集字节数。
         *
         * 设为 `0` 表示只采集元信息，不采集 body 文本。
         */
        fun maxBodyBytes(value: Long) = apply {
            require(value >= 0L) { "maxBodyBytes must be >= 0" }
            maxBodyBytes = value
        }

        /**
         * 设置 WebSocket 未连接时的消息缓存容量。
         *
         * 设为 `0` 表示离线时不缓存消息，避免占用内存。
         */
        fun queueCapacity(value: Int) = apply {
            require(value >= 0) { "queueCapacity must be >= 0" }
            queueCapacity = value
        }

        /**
         * 设置断线后的初始重连间隔，单位毫秒。
         */
        fun reconnectInitialDelayMs(value: Long) = apply {
            require(value > 0L) { "reconnectInitialDelayMs must be > 0" }
            reconnectInitialDelayMs = value
        }

        /**
         * 设置断线后的最大重连间隔，单位毫秒。
         *
         * 最终配置会保证该值不小于 [reconnectInitialDelayMs]。
         */
        fun reconnectMaxDelayMs(value: Long) = apply {
            require(value > 0L) { "reconnectMaxDelayMs must be > 0" }
            reconnectMaxDelayMs = value
        }

        /**
         * 设置需要脱敏的 Header 名称。
         *
         * 名称会统一转成小写后匹配，命中后值会被替换为脱敏占位。
         */
        fun redactHeaders(value: Set<String>) = apply {
            redactHeaders = value.map { it.lowercase() }.toSet()
        }

        /**
         * 设置需要脱敏的 URL query 参数名。
         *
         * 名称会统一转成小写后匹配，适合屏蔽 token、key 等敏感参数。
         */
        fun redactQueryParams(value: Set<String>) = apply {
            redactQueryParams = value.map { it.lowercase() }.toSet()
        }

        /**
         * 设置请求失败时是否采集异常堆栈。
         *
         * 开启后排查更方便，但堆栈可能包含业务类名或参数上下文，请按团队安全要求使用。
         */
        fun includeStackTrace(value: Boolean) = apply { includeStackTrace = value }

        /**
         * 设置 OkHttp 拦截层采集模式。
         */
        fun captureMode(value: OkHttpDebugCaptureMode) = apply { captureMode = value }

        /**
         * 设置业务派生采集扩展点。
         *
         * 扩展点会在插件自动发送 plain/wire 记录后调用。业务侧可以根据 [OkHttpDebugCaptureContext.stage]
         * 判断当前视角，只在需要的阶段返回额外的 [OkHttpDebugDerivedCapture]。
         */
        fun captureTransformers(value: List<OkHttpDebugCaptureTransformer>) = apply {
            captureTransformers = value
        }

        /**
         * 追加一个业务派生采集扩展点。
         */
        fun addCaptureTransformer(value: OkHttpDebugCaptureTransformer) = apply {
            captureTransformers = captureTransformers + value
        }

        /**
         * 设置单个业务派生采集扩展点。
         */
        fun captureTransformer(value: OkHttpDebugCaptureTransformer?) = apply {
            captureTransformers = listOfNotNull(value)
        }

        /**
         * 兼容旧接入方式的双视角开关。
         *
         * 传入 `true` 等价于 [OkHttpDebugCaptureMode.DUAL]，传入 `false` 等价于
         * [OkHttpDebugCaptureMode.APPLICATION]。
         */
        fun dualCaptureEnabled(value: Boolean) = apply {
            captureMode = if (value) OkHttpDebugCaptureMode.DUAL else OkHttpDebugCaptureMode.APPLICATION
        }

        /**
         * 构建不可变配置对象。
         *
         * 该方法会校验 WebSocket 地址格式，并规范化地址列表。
         */
        fun build(): OkHttpDebugConfig {
            val normalizedServerUrls = serverUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            require(normalizedServerUrls.isNotEmpty()) { "serverUrls must not be empty" }
            require(normalizedServerUrls.all { it.startsWith("ws://") || it.startsWith("wss://") }) {
                "serverUrls must start with ws:// or wss://"
            }
            return OkHttpDebugConfig(
                enabled = enabled,
                serverUrl = normalizedServerUrls.first(),
                serverUrls = normalizedServerUrls,
                clientTag = clientTag,
                maxBodyBytes = maxBodyBytes,
                queueCapacity = queueCapacity,
                reconnectInitialDelayMs = reconnectInitialDelayMs,
                reconnectMaxDelayMs = maxOf(reconnectInitialDelayMs, reconnectMaxDelayMs),
                redactHeaders = redactHeaders,
                redactQueryParams = redactQueryParams,
                includeStackTrace = includeStackTrace,
                captureMode = captureMode,
                captureTransformers = captureTransformers,
            )
        }
    }

    companion object {
        /**
         * 默认桌面端 WebSocket 地址。
         */
        const val DEFAULT_SERVER_URL: String = "ws://127.0.0.1:19090/session"

        /**
         * 默认桌面端 WebSocket 地址列表，覆盖 19090 到 19109 端口。
         */
        val DEFAULT_SERVER_URLS: List<String> = (19090..19109).map { port ->
            "ws://127.0.0.1:$port/session"
        }

        /**
         * 默认 body 采集上限，1 MiB。
         */
        const val DEFAULT_MAX_BODY_BYTES: Long = 1024L * 1024L

        /**
         * 默认离线消息缓存容量。
         */
        const val DEFAULT_QUEUE_CAPACITY: Int = 200

        /**
         * 默认初始重连间隔，单位毫秒。
         */
        const val DEFAULT_RECONNECT_INITIAL_DELAY_MS: Long = 1_000L

        /**
         * 默认最大重连间隔，单位毫秒。
         */
        const val DEFAULT_RECONNECT_MAX_DELAY_MS: Long = 30_000L

        /**
         * 默认脱敏 Header 名称集合。
         */
        val DEFAULT_REDACT_HEADERS: Set<String> = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-api-key",
            "x-auth-token",
        )

        /**
         * 默认脱敏 URL query 参数名集合。
         */
        val DEFAULT_REDACT_QUERY_PARAMS: Set<String> = setOf(
            "access_token",
            "api_key",
            "apikey",
            "auth",
            "key",
            "token",
        )

        /**
         * 创建默认配置构建器。
         */
        @JvmStatic
        fun builder(): Builder = Builder()

        /**
         * 创建默认配置对象。
         */
        @JvmStatic
        fun defaults(): OkHttpDebugConfig = Builder().build()
    }
}
