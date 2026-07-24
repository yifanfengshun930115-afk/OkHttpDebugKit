package com.gzq.okhttpdebugkit

import com.gzq.okhttpdebugkit.protocol.DebugCaptureMessage
import com.gzq.okhttpdebugkit.protocol.DebugHttpRequest
import com.gzq.okhttpdebugkit.protocol.DebugHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpDebugConfigTest {
    @Test
    fun defaultServerUrlStaysStableForUsbReverse() {
        val config = OkHttpDebugConfig.defaults()

        assertEquals("ws://127.0.0.1:19090/session", config.serverUrl)
        assertEquals(listOf("ws://127.0.0.1:19090/session"), config.serverUrls)
    }

    @Test
    fun acceptsMultipleServerUrlsForPortFallbacks() {
        val config = OkHttpDebugConfig.builder()
            .serverUrls(
                listOf(
                    "ws://127.0.0.1:19090/session",
                    "ws://127.0.0.1:19091/session",
                    "ws://127.0.0.1:19091/session",
                ),
            )
            .build()

        assertEquals("ws://127.0.0.1:19090/session", config.serverUrl)
        assertEquals(
            listOf(
                "ws://127.0.0.1:19090/session",
                "ws://127.0.0.1:19091/session",
            ),
            config.serverUrls,
        )
    }

    @Test
    fun clientTagIsTrimmedWhenConfigured() {
        val config = OkHttpDebugConfig.builder()
            .clientTag("  OneNews debug  ")
            .build()

        assertEquals("OneNews debug", config.clientTag)
    }

    @Test
    fun storesBusinessCaptureTransformers() {
        val transformer = OkHttpDebugCaptureTransformer { context ->
            listOf(
                OkHttpDebugDerivedCapture.response(
                    "article-decoded",
                    context.responseBody,
                    "application/json",
                ),
            )
        }
        val config = OkHttpDebugConfig.builder()
            .addCaptureTransformer(transformer)
            .build()
        val context = OkHttpDebugCaptureContext(
            DebugCaptureMessage(
                id = "capture-1",
                startedAtEpochMs = 1L,
                groupId = "group-1",
                stage = "plain",
                request = DebugHttpRequest(
                    method = "GET",
                    url = "https://example.test/article",
                ),
                response = DebugHttpResponse(
                    code = 200,
                    message = "OK",
                    body = """{"title":"decoded"}""",
                    contentType = "application/json",
                ),
            ),
        )

        val derived = config.captureTransformers.single().transform(context).single()

        assertEquals("article-decoded", derived.stage)
        assertEquals("""{"title":"decoded"}""", derived.responseBody)
        assertEquals("application/json", derived.responseContentType)
    }
}
