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
 * Application interceptor that captures request/response metadata and safe text bodies.
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
                    sessionId = config.sessionId,
                    startedAtEpochMs = startedAtEpochMs,
                    groupId = identity.groupId,
                    stage = stage,
                    durationMs = durationMs,
                    request = debugRequest,
                    response = response.toDebugResponse(config),
                    timing = OkHttpDebugEventListener.snapshot(chain.call()),
                    tags = config.staticTags.takeIf { it.isNotEmpty() },
                ),
            )
            return response
        } catch (throwable: Throwable) {
            val durationMs = elapsedMs(startedAtNs)
            emit(
                DebugCaptureMessage(
                    id = identity.captureId,
                    sessionId = config.sessionId,
                    startedAtEpochMs = startedAtEpochMs,
                    groupId = identity.groupId,
                    stage = stage,
                    durationMs = durationMs,
                    request = debugRequest,
                    error = throwable.toDebugError(config.includeStackTrace),
                    timing = OkHttpDebugEventListener.snapshot(chain.call()),
                    tags = config.staticTags.takeIf { it.isNotEmpty() },
                ),
            )
            throw throwable
        }
    }

    private fun emit(message: DebugCaptureMessage) {
        val target = connectionManager ?: OkHttpDebugKit.currentConnectionManager()
        target?.sendCapture(message)
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

private fun elapsedMs(startedAtNs: Long): Long =
    (System.nanoTime() - startedAtNs) / 1_000_000L

internal const val STAGE_PLAIN = "plain"
internal const val STAGE_WIRE = "wire"
