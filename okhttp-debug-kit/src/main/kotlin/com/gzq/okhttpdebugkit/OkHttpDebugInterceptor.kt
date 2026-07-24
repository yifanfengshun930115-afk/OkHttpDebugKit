package com.gzq.okhttpdebugkit

import com.gzq.okhttpdebugkit.internal.BodyCapture
import com.gzq.okhttpdebugkit.internal.stackTraceString
import com.gzq.okhttpdebugkit.internal.toDebugHeaders
import com.gzq.okhttpdebugkit.internal.toRedactedDebugUrl
import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugError
import com.gzq.okhttpdebugkit.protocol.DebugHttpRequest
import com.gzq.okhttpdebugkit.protocol.DebugHttpResponse
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp 请求采集拦截器。
 *
 * 业务侧通常不需要直接创建该类，优先使用 [debugWithOkHttpDebugKit] 自动安装。只有在项目需要完全
 * 手动控制拦截器顺序时，才建议直接添加该拦截器。
 *
 * 拦截器会采集请求/响应元信息、脱敏后的 Header 和 URL，以及安全可读取的文本 body。二进制 body、
 * one-shot body、duplex body 不会被强行读取，避免改变业务请求行为。
 *
 * @param config 本拦截器使用的采集配置。
 * @param connectionManager 采集消息发送通道。传 `null` 时会使用 [OkHttpDebugKit.currentConnectionManager]。
 * @param stage 当前采集视角。业务侧一般不需要传该参数，由 [debugWithOkHttpDebugKit] 按模式设置。
 */
class OkHttpDebugInterceptor @JvmOverloads constructor(
    private val config: OkHttpDebugConfig = OkHttpDebugKit.currentConfig(),
    private val connectionManager: DebugConnectionManager? = OkHttpDebugKit.currentConnectionManager(),
    private val stage: String = STAGE_PLAIN,
) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!config.enabled) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val identity = CaptureCallIds.next(chain.call(), stage)
        val startedAtEpochMs = System.currentTimeMillis()
        val startedAtNs = System.nanoTime()
        val requestBody = BodyCapture.captureRequest(request.body, config.maxBodyBytes)
        val debugRequest = DebugHttpRequest(
            method = request.method,
            url = request.url.toRedactedDebugUrl(config.redactQueryParams),
            headers = request.headers.toDebugHeaders(config.redactHeaders),
            body = requestBody.body,
            bodyTruncated = requestBody.bodyTruncated,
            contentType = requestBody.contentType,
            contentLength = requestBody.contentLength,
        )

        try {
            val response = chain.proceed(request)
            val durationMs = elapsedMs(startedAtNs)
            emit(
                DebugCaptureMessage(
                    id = identity.captureId,
                    startedAtEpochMs = startedAtEpochMs,
                    groupId = identity.groupId,
                    stage = stage,
                    durationMs = durationMs,
                    request = debugRequest,
                    response = response.toDebugResponse(config),
                    timing = OkHttpDebugEventListener.snapshot(chain.call()),
                ),
            )
            return response
        } catch (throwable: Throwable) {
            val durationMs = elapsedMs(startedAtNs)
            emit(
                DebugCaptureMessage(
                    id = identity.captureId,
                    startedAtEpochMs = startedAtEpochMs,
                    groupId = identity.groupId,
                    stage = stage,
                    durationMs = durationMs,
                    request = debugRequest,
                    error = throwable.toDebugError(config.includeStackTrace),
                    timing = OkHttpDebugEventListener.snapshot(chain.call()),
                ),
            )
            throw throwable
        }
    }

    private fun emit(message: DebugCaptureMessage) {
        val target = connectionManager ?: OkHttpDebugKit.currentConnectionManager()
        target?.sendCapture(message)
        emitDerived(message, target)
    }

    private fun emitDerived(message: DebugCaptureMessage, target: DebugConnectionManager?) {
        if (target == null || config.captureTransformers.isEmpty()) {
            return
        }
        val context = OkHttpDebugCaptureContext(message)
        config.captureTransformers.forEachIndexed { transformerIndex, transformer ->
            val derivedCaptures = try {
                transformer.transform(context)
            } catch (throwable: Throwable) {
                target.sendCapture(message.toTransformerError(transformerIndex, throwable, config.includeStackTrace))
                emptyList()
            }
            derivedCaptures.forEachIndexed { derivedIndex, derived ->
                try {
                    target.sendCapture(derived.toDebugCapture(message, transformerIndex, derivedIndex))
                } catch (throwable: Throwable) {
                    target.sendCapture(message.toTransformerError(transformerIndex, throwable, config.includeStackTrace))
                }
            }
        }
    }
}

private fun Response.toDebugResponse(config: OkHttpDebugConfig): DebugHttpResponse {
    val capturedBody = BodyCapture.captureResponse(peekBody = { byteCount -> peekBody(byteCount) }, body, config.maxBodyBytes)
    return DebugHttpResponse(
        code = code,
        message = message,
        headers = headers.toDebugHeaders(config.redactHeaders),
        body = capturedBody.body,
        bodyTruncated = capturedBody.bodyTruncated,
        contentType = capturedBody.contentType,
        contentLength = capturedBody.contentLength,
    )
}

private fun Throwable.toDebugError(includeStackTrace: Boolean): DebugError =
    DebugError(
        type = javaClass.name,
        message = message,
        stack = if (includeStackTrace) stackTraceString() else null,
    )

private fun OkHttpDebugDerivedCapture.toDebugCapture(
    base: DebugCaptureMessage,
    transformerIndex: Int,
    derivedIndex: Int,
): DebugCaptureMessage {
    val nextStage = stage.trim()
    require(!isReservedStage(nextStage)) { "derived stage must not use reserved stage: $nextStage" }
    return base.copy(
        id = "${base.id}-${stageIdFragment(nextStage)}-${transformerIndex + 1}-${derivedIndex + 1}",
        stage = nextStage,
        request = base.request.withDerivedRequest(this),
        response = base.response.withDerivedResponse(this),
        error = null,
    )
}

private fun DebugHttpRequest.withDerivedRequest(derived: OkHttpDebugDerivedCapture): DebugHttpRequest {
    val nextBody = derived.requestBody ?: body
    return copy(
        body = nextBody,
        bodyTruncated = if (derived.requestBody != null) false else bodyTruncated,
        contentType = derived.requestContentType ?: contentType,
        contentLength = if (derived.requestBody != null) derived.requestBody.utf8Length() else contentLength,
    )
}

private fun DebugHttpResponse?.withDerivedResponse(derived: OkHttpDebugDerivedCapture): DebugHttpResponse? {
    if (
        this == null &&
        derived.responseBody == null &&
        derived.responseContentType == null &&
        derived.responseCode == null &&
        derived.responseMessage == null
    ) {
        return null
    }
    val nextBody = derived.responseBody ?: this?.body
    return DebugHttpResponse(
        code = derived.responseCode ?: this?.code ?: 0,
        message = derived.responseMessage ?: this?.message.orEmpty(),
        headers = this?.headers.orEmpty(),
        body = nextBody,
        bodyTruncated = if (derived.responseBody != null) false else this?.bodyTruncated,
        contentType = derived.responseContentType ?: this?.contentType,
        contentLength = if (derived.responseBody != null) derived.responseBody.utf8Length() else this?.contentLength,
    )
}

private fun DebugCaptureMessage.toTransformerError(
    transformerIndex: Int,
    throwable: Throwable,
    includeStackTrace: Boolean,
): DebugCaptureMessage =
    copy(
        id = "$id-$STAGE_TRANSFORMER_ERROR-${transformerIndex + 1}",
        stage = STAGE_TRANSFORMER_ERROR,
        response = null,
        error = throwable.toDebugError(includeStackTrace),
    )

private fun String?.utf8Length(): Long? = this?.toByteArray(Charsets.UTF_8)?.size?.toLong()

private fun isReservedStage(stage: String): Boolean =
    stage == STAGE_PLAIN || stage == STAGE_WIRE

private fun stageIdFragment(stage: String): String =
    stage
        .lowercase()
        .replace(Regex("[^a-z0-9_.-]"), "-")
        .trim('-')
        .ifEmpty { "custom" }

private fun elapsedMs(startedAtNs: Long): Long =
    (System.nanoTime() - startedAtNs) / 1_000_000L

internal const val STAGE_PLAIN = "plain"
internal const val STAGE_WIRE = "wire"
internal const val STAGE_TRANSFORMER_ERROR = "transformer-error"
