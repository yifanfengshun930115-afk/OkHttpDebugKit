package com.gzq.okhttpdebugkit.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugJsonProtocolTest {
    @Test
    fun helloJsonMatchesDesktopProtocol() {
        val json = JSONObject(
            DebugJsonProtocol.helloToJson(
                DebugHelloMessage(
                    app = DebugAppInfo(
                        packageName = "com.example.app",
                        versionName = "1.2.3",
                        versionCode = 123,
                        debuggable = true,
                    ),
                    device = DebugDeviceInfo(
                        manufacturer = "Google",
                        model = "Pixel",
                        sdkInt = 36,
                    ),
                    sessionId = "session-1",
                    token = "demo-token",
                ),
            ),
        )

        assertEquals("hello", json.getString("type"))
        assertEquals(1, json.getInt("protocolVersion"))
        assertEquals("session-1", json.getString("sessionId"))
        assertEquals("demo-token", json.getString("token"))
        assertEquals("com.example.app", json.getJSONObject("app").getString("packageName"))
        assertEquals(123L, json.getJSONObject("app").getLong("versionCode"))
        assertTrue(json.getJSONObject("app").getBoolean("debuggable"))
        assertEquals(36, json.getJSONObject("device").getInt("sdkInt"))
    }

    @Test
    fun captureJsonMatchesDesktopProtocol() {
        val json = JSONObject(
            DebugJsonProtocol.captureToJson(
                DebugCaptureMessage(
                    id = "capture-1",
                    sessionId = "session-1",
                    startedAtEpochMs = 1_720_000_000_000L,
                    groupId = "group-1",
                    stage = "plain",
                    durationMs = 24,
                    request = DebugHttpRequest(
                        method = "POST",
                        url = "https://example.test/api",
                        headers = mapOf("Content-Type" to listOf("application/x-www-form-urlencoded")),
                        body = "page=1",
                        bodyTruncated = false,
                        contentType = "application/x-www-form-urlencoded",
                        contentLength = 6,
                    ),
                    response = DebugHttpResponse(
                        code = 200,
                        message = "OK",
                        headers = mapOf("Content-Type" to listOf("application/json")),
                        body = """{"ok":true}""",
                        bodyTruncated = false,
                        contentType = "application/json",
                        contentLength = 11,
                    ),
                    timing = mapOf("dnsDurationMs" to 1L, "protocol" to "h2"),
                    tags = mapOf("source" to "unit-test"),
                ),
            ),
        )

        assertEquals("capture", json.getString("type"))
        assertEquals(1, json.getInt("protocolVersion"))
        assertEquals("capture-1", json.getString("id"))
        assertEquals("group-1", json.getString("groupId"))
        assertEquals("plain", json.getString("stage"))
        assertEquals("session-1", json.getString("sessionId"))
        assertEquals(24L, json.getLong("durationMs"))

        val request = json.getJSONObject("request")
        assertEquals("POST", request.getString("method"))
        assertEquals("https://example.test/api", request.getString("url"))
        assertEquals("page=1", request.getString("body"))
        assertFalse(request.getBoolean("bodyTruncated"))
        assertEquals(
            "application/x-www-form-urlencoded",
            request.getJSONObject("headers").getJSONArray("Content-Type").getString(0),
        )

        val response = json.getJSONObject("response")
        assertEquals(200, response.getInt("code"))
        assertEquals("""{"ok":true}""", response.getString("body"))
        assertEquals("h2", json.getJSONObject("timing").getString("protocol"))
        assertEquals("unit-test", json.getJSONObject("tags").getString("source"))
    }
}
