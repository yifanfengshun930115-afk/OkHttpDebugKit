package com.gzq.okhttpdebugkit

import okhttp3.Call
import java.util.UUID
import java.util.WeakHashMap

internal data class CaptureIdentity(
    val groupId: String,
    val captureId: String,
)

internal object CaptureCallIds {
    private data class CallState(
        val groupId: String,
        var sequence: Int = 0,
    )

    private val calls = WeakHashMap<Call, CallState>()

    @Synchronized
    fun next(call: Call, stage: String): CaptureIdentity {
        val state = calls.getOrPut(call) {
            CallState(groupId = UUID.randomUUID().toString())
        }
        state.sequence += 1
        return CaptureIdentity(
            groupId = state.groupId,
            captureId = "${state.groupId}-$stage-${state.sequence}",
        )
    }
}
