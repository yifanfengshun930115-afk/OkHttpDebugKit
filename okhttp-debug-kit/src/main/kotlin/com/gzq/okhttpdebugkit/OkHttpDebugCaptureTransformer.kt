package com.gzq.okhttpdebugkit

import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugError
import com.gzq.okhttpdebugkit.protocol.DebugHttpRequest
import com.gzq.okhttpdebugkit.protocol.DebugHttpResponse

/**
 * 业务派生采集扩展点。
 *
 * 插件会先发送 OkHttp 拦截层自动采集到的 plain/wire 记录，再把该记录包装成
 * [OkHttpDebugCaptureContext] 交给业务侧。业务侧可基于加密响应、业务模型或特殊解析规则额外返回
 * 一个或多个派生阶段，桌面端会按同一个 groupId 归到同一条接口下展示。
 */
fun interface OkHttpDebugCaptureTransformer {
    fun transform(context: OkHttpDebugCaptureContext): List<OkHttpDebugDerivedCapture>
}

/**
 * 业务派生采集的上下文。
 */
class OkHttpDebugCaptureContext internal constructor(
    val capture: DebugCaptureMessage,
) {
    val id: String get() = capture.id
    val groupId: String get() = capture.groupId
    val stage: String get() = capture.stage
    val startedAtEpochMs: Long get() = capture.startedAtEpochMs
    val durationMs: Long? get() = capture.durationMs
    val request: DebugHttpRequest get() = capture.request
    val response: DebugHttpResponse? get() = capture.response
    val error: DebugError? get() = capture.error
    val timing: Map<String, Any?>? get() = capture.timing
    val method: String get() = request.method
    val url: String get() = request.url
    val requestBody: String? get() = request.body
    val responseBody: String? get() = response?.body
    val requestContentType: String? get() = request.contentType
    val responseContentType: String? get() = response?.contentType
}

/**
 * 业务侧返回的派生阶段。
 */
class OkHttpDebugDerivedCapture @JvmOverloads constructor(
    val stage: String,
    val requestBody: String? = null,
    val requestContentType: String? = null,
    val responseBody: String? = null,
    val responseContentType: String? = null,
    val responseCode: Int? = null,
    val responseMessage: String? = null,
) {
    init {
        require(stage.trim().isNotEmpty()) { "stage must not be blank" }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun response(
            stage: String,
            body: String?,
            contentType: String? = "text/plain; charset=utf-8",
        ): OkHttpDebugDerivedCapture =
            OkHttpDebugDerivedCapture(
                stage = stage,
                responseBody = body,
                responseContentType = contentType,
            )

        @JvmStatic
        @JvmOverloads
        fun request(
            stage: String,
            body: String?,
            contentType: String? = "text/plain; charset=utf-8",
        ): OkHttpDebugDerivedCapture =
            OkHttpDebugDerivedCapture(
                stage = stage,
                requestBody = body,
                requestContentType = contentType,
            )

        @JvmStatic
        @JvmOverloads
        fun exchange(
            stage: String,
            requestBody: String?,
            responseBody: String?,
            contentType: String? = "text/plain; charset=utf-8",
        ): OkHttpDebugDerivedCapture =
            OkHttpDebugDerivedCapture(
                stage = stage,
                requestBody = requestBody,
                requestContentType = contentType,
                responseBody = responseBody,
                responseContentType = contentType,
            )
    }
}
