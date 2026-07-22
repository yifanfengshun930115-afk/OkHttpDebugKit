package com.gzq.okhttpdebugkit

/**
 * Runtime options for the debug capture transport.
 *
 * Apps should usually create this only from debug source sets or debug DI wiring.
 */
class OkHttpDebugConfig private constructor(
    val enabled: Boolean,
    val serverUrl: String,
    val serverUrls: List<String>,
    val token: String?,
    val sessionId: String,
    val maxBodyBytes: Long,
    val queueCapacity: Int,
    val reconnectInitialDelayMs: Long,
    val reconnectMaxDelayMs: Long,
    val redactHeaders: Set<String>,
    val redactQueryParams: Set<String>,
    val includeStackTrace: Boolean,
    val staticTags: Map<String, String>,
) {
    fun newBuilder(): Builder = Builder(this)

    class Builder {
        private var enabled: Boolean = true
        private var serverUrl: String = DEFAULT_SERVER_URL
        private var serverUrls: List<String> = listOf(DEFAULT_SERVER_URL)
        private var token: String? = null
        private var sessionId: String = java.util.UUID.randomUUID().toString()
        private var maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES
        private var queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
        private var reconnectInitialDelayMs: Long = DEFAULT_RECONNECT_INITIAL_DELAY_MS
        private var reconnectMaxDelayMs: Long = DEFAULT_RECONNECT_MAX_DELAY_MS
        private var redactHeaders: Set<String> = DEFAULT_REDACT_HEADERS
        private var redactQueryParams: Set<String> = DEFAULT_REDACT_QUERY_PARAMS
        private var includeStackTrace: Boolean = false
        private var staticTags: Map<String, String> = emptyMap()

        constructor()

        internal constructor(config: OkHttpDebugConfig) {
            enabled = config.enabled
            serverUrl = config.serverUrl
            serverUrls = config.serverUrls
            token = config.token
            sessionId = config.sessionId
            maxBodyBytes = config.maxBodyBytes
            queueCapacity = config.queueCapacity
            reconnectInitialDelayMs = config.reconnectInitialDelayMs
            reconnectMaxDelayMs = config.reconnectMaxDelayMs
            redactHeaders = config.redactHeaders
            redactQueryParams = config.redactQueryParams
            includeStackTrace = config.includeStackTrace
            staticTags = config.staticTags
        }

        fun enabled(value: Boolean) = apply { enabled = value }

        fun serverUrl(value: String) = apply {
            serverUrl = value
            serverUrls = listOf(value)
        }

        fun serverUrls(value: List<String>) = apply {
            require(value.isNotEmpty()) { "serverUrls must not be empty" }
            serverUrls = value
            serverUrl = value.first()
        }

        fun token(value: String?) = apply { token = value?.takeIf { it.isNotBlank() } }

        fun sessionId(value: String) = apply {
            require(value.isNotBlank()) { "sessionId must not be blank" }
            sessionId = value
        }

        fun maxBodyBytes(value: Long) = apply {
            require(value >= 0L) { "maxBodyBytes must be >= 0" }
            maxBodyBytes = value
        }

        fun queueCapacity(value: Int) = apply {
            require(value >= 0) { "queueCapacity must be >= 0" }
            queueCapacity = value
        }

        fun reconnectInitialDelayMs(value: Long) = apply {
            require(value > 0L) { "reconnectInitialDelayMs must be > 0" }
            reconnectInitialDelayMs = value
        }

        fun reconnectMaxDelayMs(value: Long) = apply {
            require(value > 0L) { "reconnectMaxDelayMs must be > 0" }
            reconnectMaxDelayMs = value
        }

        fun redactHeaders(value: Set<String>) = apply {
            redactHeaders = value.map { it.lowercase() }.toSet()
        }

        fun redactQueryParams(value: Set<String>) = apply {
            redactQueryParams = value.map { it.lowercase() }.toSet()
        }

        fun includeStackTrace(value: Boolean) = apply { includeStackTrace = value }

        fun staticTags(value: Map<String, String>) = apply { staticTags = value.toMap() }

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
                sessionId = sessionId,
                maxBodyBytes = maxBodyBytes,
                queueCapacity = queueCapacity,
                reconnectInitialDelayMs = reconnectInitialDelayMs,
                reconnectMaxDelayMs = maxOf(reconnectInitialDelayMs, reconnectMaxDelayMs),
                redactHeaders = redactHeaders,
                redactQueryParams = redactQueryParams,
                includeStackTrace = includeStackTrace,
                staticTags = staticTags,
            )
        }
    }

    companion object {
        const val DEFAULT_SERVER_URL: String = "ws://127.0.0.1:19090/session"
        val DEFAULT_SERVER_URLS: List<String> = (19090..19109).map { port ->
            "ws://127.0.0.1:$port/session"
        }
        const val DEFAULT_MAX_BODY_BYTES: Long = 1024L * 1024L
        const val DEFAULT_QUEUE_CAPACITY: Int = 200
        const val DEFAULT_RECONNECT_INITIAL_DELAY_MS: Long = 1_000L
        const val DEFAULT_RECONNECT_MAX_DELAY_MS: Long = 30_000L

        val DEFAULT_REDACT_HEADERS: Set<String> = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-api-key",
            "x-auth-token",
        )

        val DEFAULT_REDACT_QUERY_PARAMS: Set<String> = setOf(
            "access_token",
            "api_key",
            "apikey",
            "auth",
            "key",
            "token",
        )

        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        fun defaults(): OkHttpDebugConfig = Builder().build()
    }
}
