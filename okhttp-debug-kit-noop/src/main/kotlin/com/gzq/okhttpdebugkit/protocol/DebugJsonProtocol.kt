package com.gzq.okhttpdebugkit.protocol

/**
 * No-op JSON facade. Kept for source compatibility with the debug artifact.
 */
object DebugJsonProtocol {
    @JvmStatic
    fun helloToJson(message: DebugHelloMessage): String = "{}"

    @JvmStatic
    fun captureToJson(message: DebugCaptureMessage): String = "{}"
}
