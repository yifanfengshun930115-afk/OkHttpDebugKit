package com.gzq.okhttpdebugkit

/**
 * OkHttpDebugKit 的运行配置。
 *
 * noop 产物会保留和 debug 产物一致的公开 API，方便业务侧在不同构建变体中复用同一份接入代码。
 * 在 noop 产物中，这些配置只用于保持接口兼容，不会触发请求采集、body 读取或 WebSocket 连接。
 *
 * @property enabled 是否启用采集。noop 产物即使设置为 `true` 也不会真正采集数据。
 * @property serverUrl 单个桌面端 WebSocket 地址。保留该字段是为了兼容旧接入代码。
 * @property serverUrls 可轮询尝试的桌面端 WebSocket 地址列表。
 * @property token 连接桌面端时附加的可选鉴权 token，空字符串会被当作未设置。
 * @property clientTag 当前客户端在桌面端展示和筛选时使用的稳定标签，noop 产物不会发送。
 * @property sessionId 当前 App 会话 ID，用于保持和 debug 产物一致的配置结构。
 * @property maxBodyBytes 单个请求体或响应体最多采集的字节数，noop 产物不会读取 body。
 * @property queueCapacity WebSocket 未连接时最多缓存的消息数，noop 产物不会缓存消息。
 * @property reconnectInitialDelayMs 断线后第一次重连等待时间，单位毫秒。
 * @property reconnectMaxDelayMs 断线重连的最大等待时间，单位毫秒。
 * @property redactHeaders 需要脱敏的 HTTP Header 名称集合，大小写不敏感。
 * @property redactQueryParams 需要脱敏的 URL query 参数名集合，大小写不敏感。
 * @property includeStackTrace 请求失败时是否发送异常堆栈，noop 产物不会发送。
 * @property staticTags 附加到采集消息上的固定标签，noop 产物不会发送。
 * @property captureMode OkHttp 拦截层采集模式，noop 产物仅保留该配置值。
 */
class OkHttpDebugConfig private constructor(
    val enabled: Boolean,
    val serverUrl: String,
    val serverUrls: List<String>,
    val token: String?,
    val clientTag: String?,
    val sessionId: String,
    val maxBodyBytes: Long,
    val queueCapacity: Int,
    val reconnectInitialDelayMs: Long,
    val reconnectMaxDelayMs: Long,
    val redactHeaders: Set<String>,
    val redactQueryParams: Set<String>,
    val includeStackTrace: Boolean,
    val staticTags: Map<String, String>,
    val captureMode: OkHttpDebugCaptureMode,
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
        private var enabled: Boolean = false
        private var serverUrl: String = DEFAULT_SERVER_URL
        private var serverUrls: List<String> = listOf(DEFAULT_SERVER_URL)
        private var token: String? = null
        private var clientTag: String? = null
        private var sessionId: String = java.util.UUID.randomUUID().toString()
        private var maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES
        private var queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
        private var reconnectInitialDelayMs: Long = DEFAULT_RECONNECT_INITIAL_DELAY_MS
        private var reconnectMaxDelayMs: Long = DEFAULT_RECONNECT_MAX_DELAY_MS
        private var redactHeaders: Set<String> = DEFAULT_REDACT_HEADERS
        private var redactQueryParams: Set<String> = DEFAULT_REDACT_QUERY_PARAMS
        private var includeStackTrace: Boolean = false
        private var staticTags: Map<String, String> = emptyMap()
        private var captureMode: OkHttpDebugCaptureMode = OkHttpDebugCaptureMode.APPLICATION

        /**
         * 创建一份默认配置。
         */
        constructor()

        internal constructor(config: OkHttpDebugConfig) {
            enabled = config.enabled
            serverUrl = config.serverUrl
            serverUrls = config.serverUrls
            token = config.token
            clientTag = config.clientTag
            sessionId = config.sessionId
            maxBodyBytes = config.maxBodyBytes
            queueCapacity = config.queueCapacity
            reconnectInitialDelayMs = config.reconnectInitialDelayMs
            reconnectMaxDelayMs = config.reconnectMaxDelayMs
            redactHeaders = config.redactHeaders
            redactQueryParams = config.redactQueryParams
            includeStackTrace = config.includeStackTrace
            staticTags = config.staticTags
            captureMode = config.captureMode
        }

        /**
         * 设置是否启用采集。
         *
         * noop 产物会接受该配置，但不会真正打开采集能力。
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
         * 设置连接桌面端时携带的鉴权 token。
         *
         * 传入 `null` 或空白字符串表示不携带 token。
         */
        fun token(value: String?) = apply { token = value?.takeIf { it.isNotBlank() } }

        /**
         * 设置桌面端展示和筛选客户端来源时使用的稳定标签。
         *
         * noop 产物不会发送该值，该方法只用于保持 API 兼容。
         */
        fun clientTag(value: String?) = apply { clientTag = value?.trim()?.takeIf { it.isNotEmpty() } }

        /**
         * 设置当前 App 运行会话 ID。
         */
        fun sessionId(value: String) = apply {
            require(value.isNotBlank()) { "sessionId must not be blank" }
            sessionId = value
        }

        /**
         * 设置请求体和响应体的最大采集字节数。
         *
         * noop 产物不会读取 body，该值只用于保持 API 兼容。
         */
        fun maxBodyBytes(value: Long) = apply {
            require(value >= 0L) { "maxBodyBytes must be >= 0" }
            maxBodyBytes = value
        }

        /**
         * 设置 WebSocket 未连接时的消息缓存容量。
         *
         * noop 产物不会缓存消息，该值只用于保持 API 兼容。
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
         * 名称会统一转成小写后保存，保持和 debug 产物一致的校验行为。
         */
        fun redactHeaders(value: Set<String>) = apply {
            redactHeaders = value.map { it.lowercase() }.toSet()
        }

        /**
         * 设置需要脱敏的 URL query 参数名。
         *
         * 名称会统一转成小写后保存，保持和 debug 产物一致的校验行为。
         */
        fun redactQueryParams(value: Set<String>) = apply {
            redactQueryParams = value.map { it.lowercase() }.toSet()
        }

        /**
         * 设置请求失败时是否采集异常堆栈。
         *
         * noop 产物不会发送异常堆栈，该值只用于保持 API 兼容。
         */
        fun includeStackTrace(value: Boolean) = apply { includeStackTrace = value }

        /**
         * 设置发送到桌面端的固定标签。
         *
         * noop 产物不会发送标签，该值只用于保持 API 兼容。
         */
        fun staticTags(value: Map<String, String>) = apply { staticTags = value.toMap() }

        /**
         * 设置 OkHttp 拦截层采集模式。
         */
        fun captureMode(value: OkHttpDebugCaptureMode) = apply { captureMode = value }

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
                token = token,
                clientTag = clientTag,
                sessionId = sessionId,
                maxBodyBytes = maxBodyBytes,
                queueCapacity = queueCapacity,
                reconnectInitialDelayMs = reconnectInitialDelayMs,
                reconnectMaxDelayMs = maxOf(reconnectInitialDelayMs, reconnectMaxDelayMs),
                redactHeaders = redactHeaders,
                redactQueryParams = redactQueryParams,
                includeStackTrace = includeStackTrace,
                staticTags = staticTags,
                captureMode = captureMode,
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
