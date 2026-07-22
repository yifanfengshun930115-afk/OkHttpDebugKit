package com.gzq.okhttpdebugkit

/**
 * Controls which OkHttp interception layers are captured.
 */
enum class OkHttpDebugCaptureMode {
    /**
     * Captures the application-level request and response. This is the default
     * and works well for projects without custom encryption/decryption interceptors.
     */
    APPLICATION,

    /**
     * Captures the innermost application-level request and response, after app
     * interceptors have rewritten the request and before they consume the response.
     */
    WIRE,

    /**
     * Captures both application and network layers and links them with one groupId.
     */
    DUAL,
}
