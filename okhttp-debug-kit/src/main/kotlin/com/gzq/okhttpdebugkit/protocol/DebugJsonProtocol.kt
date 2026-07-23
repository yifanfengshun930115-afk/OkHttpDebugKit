package com.gzq.okhttpdebugkit.protocol

import org.json.JSONArray
import org.json.JSONObject

object DebugJsonProtocol {
    @JvmStatic
    fun helloToJson(message: DebugHelloMessage): String {
        return JSONObject()
            .put("type", message.type)
            .put("protocolVersion", message.protocolVersion)
            .put("app", JSONObject()
                .put("packageName", message.app.packageName)
                .putOptional("versionName", message.app.versionName)
                .putOptional("versionCode", message.app.versionCode)
                .putOptional("debuggable", message.app.debuggable))
            .put("device", JSONObject()
                .putOptional("manufacturer", message.device.manufacturer)
                .putOptional("model", message.device.model)
                .putOptional("sdkInt", message.device.sdkInt)
                .putOptional("deviceTag", message.device.deviceTag))
            .putOptional("clientTag", message.clientTag)
            .toString()
    }

    @JvmStatic
    fun captureToJson(message: DebugCaptureMessage): String {
        return JSONObject()
            .put("type", message.type)
            .put("protocolVersion", message.protocolVersion)
            .put("id", message.id)
            .put("startedAtEpochMs", message.startedAtEpochMs)
            .put("groupId", message.groupId)
            .put("stage", message.stage)
            .putOptional("durationMs", message.durationMs)
            .put("request", message.request.toJson())
            .putOptional("response", message.response?.toJson())
            .putOptional("error", message.error?.toJson())
            .putOptional("timing", message.timing?.toJsonObject())
            .toString()
    }
}

private fun DebugHttpRequest.toJson(): JSONObject = JSONObject()
    .put("method", method)
    .put("url", url)
    .put("headers", headers.toHeaderJsonObject())
    .putOptional("body", body)
    .putOptional("bodyTruncated", bodyTruncated)
    .putOptional("contentType", contentType)
    .putOptional("contentLength", contentLength)

private fun DebugHttpResponse.toJson(): JSONObject = JSONObject()
    .put("code", code)
    .put("message", message)
    .put("headers", headers.toHeaderJsonObject())
    .putOptional("body", body)
    .putOptional("bodyTruncated", bodyTruncated)
    .putOptional("contentType", contentType)
    .putOptional("contentLength", contentLength)

private fun DebugError.toJson(): JSONObject = JSONObject()
    .put("type", type)
    .putOptional("message", message)
    .putOptional("stack", stack)

private fun Map<String, List<String>>.toHeaderJsonObject(): JSONObject {
    val json = JSONObject()
    entries.sortedBy { it.key.lowercase() }.forEach { (name, values) ->
        json.put(name, JSONArray(values))
    }
    return json
}

private fun Map<String, Any?>.toJsonObject(): JSONObject {
    val json = JSONObject()
    entries.sortedBy { it.key }.forEach { (key, value) ->
        when (value) {
            null -> Unit
            is Number, is Boolean, is String -> json.put(key, value)
            is Map<*, *> -> json.put(key, value.toStringKeyMap().toJsonObject())
            is Iterable<*> -> json.put(key, value.toJsonArray())
            else -> json.put(key, value.toString())
        }
    }
    return json
}

private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> =
    entries.associate { it.key.toString() to it.value }

private fun Iterable<*>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { value ->
        when (value) {
            null -> array.put(JSONObject.NULL)
            is Number, is Boolean, is String -> array.put(value)
            is Map<*, *> -> array.put(value.toStringKeyMap().toJsonObject())
            is Iterable<*> -> array.put(value.toJsonArray())
            else -> array.put(value.toString())
        }
    }
    return array
}

private fun JSONObject.putOptional(name: String, value: Any?): JSONObject {
    if (value != null) {
        put(name, value)
    }
    return this
}
