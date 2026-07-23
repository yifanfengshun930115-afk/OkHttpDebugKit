package com.gzq.okhttpdebugkit

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
}
