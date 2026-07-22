package com.gzq.okhttpdebugkit.protocol

data class DebugAppInfo(
    val packageName: String,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val debuggable: Boolean? = null,
)

data class DebugDeviceInfo(
    val manufacturer: String? = null,
    val model: String? = null,
    val sdkInt: Int? = null,
)

data class DebugHelloMessage(
    val app: DebugAppInfo,
    val device: DebugDeviceInfo,
    val sessionId: String,
    val token: String? = null,
    val protocolVersion: Int = 1,
    val type: String = "hello",
)

data class DebugHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
    val bodyTruncated: Boolean? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
)

data class DebugHttpResponse(
    val code: Int,
    val message: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
    val bodyTruncated: Boolean? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
)

data class DebugError(
    val type: String,
    val message: String? = null,
    val stack: String? = null,
)

data class DebugCaptureMessage(
    val id: String,
    val sessionId: String,
    val startedAtEpochMs: Long,
    val request: DebugHttpRequest,
    val durationMs: Long? = null,
    val response: DebugHttpResponse? = null,
    val error: DebugError? = null,
    val timing: Map<String, Any?>? = null,
    val tags: Map<String, String>? = null,
    val protocolVersion: Int = 1,
    val type: String = "capture",
)
