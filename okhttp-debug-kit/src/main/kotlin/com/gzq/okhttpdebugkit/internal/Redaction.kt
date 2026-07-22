package com.gzq.okhttpdebugkit.internal

import okhttp3.Headers
import okhttp3.HttpUrl
import java.io.PrintWriter
import java.io.StringWriter

private const val REDACTED = "<redacted>"

internal fun Headers.toDebugHeaders(redactHeaders: Set<String>): Map<String, List<String>> {
    val normalized = redactHeaders.map { it.lowercase() }.toSet()
    return names().associateWith { name ->
        if (name.lowercase() in normalized) {
            listOf(REDACTED)
        } else {
            values(name)
        }
    }
}

internal fun HttpUrl.toRedactedDebugUrl(redactQueryParams: Set<String>): String {
    if (redactQueryParams.isEmpty() || querySize == 0) {
        return toString()
    }
    val normalized = redactQueryParams.map { it.lowercase() }.toSet()
    val builder = newBuilder()
    queryParameterNames.forEach { name ->
        if (name.lowercase() in normalized) {
            builder.setQueryParameter(name, REDACTED)
        }
    }
    return builder.build().toString()
}

internal fun Throwable.stackTraceString(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString()
}

