package com.gzq.okhttpdebugkit

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * No-op interceptor for release builds.
 */
class OkHttpDebugInterceptor @JvmOverloads constructor(
    private val config: OkHttpDebugConfig = OkHttpDebugKit.currentConfig(),
    private val connectionManager: DebugConnectionManager? = OkHttpDebugKit.currentConnectionManager(),
) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
