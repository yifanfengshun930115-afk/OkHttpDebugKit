package com.gzq.okhttpdebugkit.internal

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.buffer
import java.nio.charset.Charset

internal data class CapturedBody(
    val body: String?,
    val bodyTruncated: Boolean?,
    val contentType: String?,
    val contentLength: Long?,
)

internal object BodyCapture {
    fun captureRequest(body: RequestBody?, maxBodyBytes: Long): CapturedBody {
        if (body == null) {
            return CapturedBody(
                body = null,
                bodyTruncated = null,
                contentType = null,
                contentLength = null,
            )
        }

        val contentType = body.contentType()
        val contentLength = body.safeContentLength()
        if (body.isDuplex() || body.isOneShot() || !contentType.isTextLike()) {
            return CapturedBody(
                body = null,
                bodyTruncated = null,
                contentType = contentType?.toString(),
                contentLength = contentLength,
            )
        }
        val textContentType = contentType ?: return CapturedBody(
            body = null,
            bodyTruncated = null,
            contentType = null,
            contentLength = contentLength,
        )

        return runCatching {
            val result = captureByWriting(body, maxBodyBytes)
            CapturedBody(
                body = result.buffer.readString(textContentType.charsetOrUtf8()),
                bodyTruncated = result.totalBytes > maxBodyBytes,
                contentType = textContentType.toString(),
                contentLength = contentLength,
            )
        }.getOrElse {
            CapturedBody(
                body = null,
                bodyTruncated = null,
                contentType = textContentType.toString(),
                contentLength = contentLength,
            )
        }
    }

    fun captureResponse(
        peekBody: (Long) -> ResponseBody,
        body: ResponseBody?,
        maxBodyBytes: Long,
    ): CapturedBody {
        if (body == null) {
            return CapturedBody(
                body = null,
                bodyTruncated = null,
                contentType = null,
                contentLength = null,
            )
        }

        val contentType = body.contentType()
        val contentLength = body.safeContentLength()
        if (!contentType.isTextLike()) {
            return CapturedBody(
                body = null,
                bodyTruncated = null,
                contentType = contentType?.toString(),
                contentLength = contentLength,
            )
        }
        val textContentType = contentType ?: return CapturedBody(
            body = null,
            bodyTruncated = null,
            contentType = null,
            contentLength = contentLength,
        )

        return runCatching {
            val peeked = peekBody(maxBodyBytes)
            val text = peeked.string()
            CapturedBody(
                body = text,
                bodyTruncated = when {
                    contentLength != null -> contentLength > maxBodyBytes
                    maxBodyBytes == 0L -> false
                    text.toByteArray(textContentType.charsetOrUtf8()).size >= maxBodyBytes -> true
                    else -> false
                },
                contentType = textContentType.toString(),
                contentLength = contentLength,
            )
        }.getOrElse {
            CapturedBody(
                body = null,
                bodyTruncated = null,
                contentType = textContentType.toString(),
                contentLength = contentLength,
            )
        }
    }

    private fun captureByWriting(body: RequestBody, maxBodyBytes: Long): RequestBodyCaptureResult {
        val captureBuffer = Buffer()
        val sink = CappingSink(captureBuffer, maxBodyBytes)
        val bufferedSink = sink.buffer()
        body.writeTo(bufferedSink)
        bufferedSink.flush()
        return RequestBodyCaptureResult(captureBuffer, sink.totalBytes)
    }
}

private data class RequestBodyCaptureResult(
    val buffer: Buffer,
    val totalBytes: Long,
)

private class CappingSink(
    private val captureBuffer: Buffer,
    private val maxBytes: Long,
) : Sink {
    var totalBytes: Long = 0L
        private set

    override fun write(source: Buffer, byteCount: Long) {
        val remaining = maxBytes - captureBuffer.size
        val bytesToCopy = minOf(remaining, byteCount).coerceAtLeast(0L)
        if (bytesToCopy > 0L) {
            source.copyTo(captureBuffer, offset = 0L, byteCount = bytesToCopy)
        }
        source.skip(byteCount)
        totalBytes += byteCount
    }

    override fun flush() = Unit

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}

private fun RequestBody.safeContentLength(): Long? =
    runCatching { contentLength() }
        .getOrDefault(-1L)
        .takeIf { it >= 0L }

private fun ResponseBody.safeContentLength(): Long? =
    runCatching { contentLength() }
        .getOrDefault(-1L)
        .takeIf { it >= 0L }

private fun MediaType?.isTextLike(): Boolean {
    if (this == null) return false
    val type = type.lowercase()
    val subtype = subtype.lowercase()
    return type == "text" ||
        subtype == "json" ||
        subtype.endsWith("+json") ||
        subtype == "x-www-form-urlencoded" ||
        subtype == "xml" ||
        subtype.endsWith("+xml") ||
        subtype == "html" ||
        subtype == "javascript" ||
        subtype == "plain" ||
        subtype == "csv"
}

private fun MediaType.charsetOrUtf8(): Charset = charset(Charsets.UTF_8) ?: Charsets.UTF_8
