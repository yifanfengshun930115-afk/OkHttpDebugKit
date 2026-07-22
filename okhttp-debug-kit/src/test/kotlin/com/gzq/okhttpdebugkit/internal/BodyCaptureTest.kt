package com.gzq.okhttpdebugkit.internal

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyCaptureTest {
    @Test
    fun capturesTextRequestBodyWithLimit() {
        val body = "abcdef".toRequestBody("text/plain".toMediaType())

        val captured = BodyCapture.captureRequest(body, maxBodyBytes = 3)

        assertEquals("abc", captured.body)
        assertTrue(captured.bodyTruncated == true)
        assertEquals("text/plain; charset=utf-8", captured.contentType)
        assertEquals(6L, captured.contentLength)
    }

    @Test
    fun skipsBinaryRequestBody() {
        val body = byteArrayOf(0, 1, 2, 3)
            .toRequestBody("application/octet-stream".toMediaType())

        val captured = BodyCapture.captureRequest(body, maxBodyBytes = 1024)

        assertNull(captured.body)
        assertNull(captured.bodyTruncated)
        assertEquals("application/octet-stream", captured.contentType)
        assertEquals(4L, captured.contentLength)
    }
}

