# Android Integration

This SDK is intended for debug builds only. Do not put real service tokens in sample code or source control.

## Gradle

Build and copy both AARs into the app's `libs/` directory:

```bash
./gradlew releaseOkHttpDebugKitPackages
```

The packaged artifacts are under `build/okhttp-debug-kit-packages/`.

Recommended variant dependencies:

```groovy
dependencies {
    debugImplementation(files("$rootDir/libs/okhttp-debug-kit-1.0-release.aar"))
    logReleaseImplementation(files("$rootDir/libs/okhttp-debug-kit-1.0-release.aar"))
    releaseImplementation(files("$rootDir/libs/okhttp-debug-kit-noop-1.0-release.aar"))
}
```

The no-op AAR keeps the same public API but does not open WebSockets, add
interceptors, enqueue captures, or read request/response bodies.

Shared bridge example:

```kotlin
package com.bulletin.debug

import android.content.Context
import com.gzq.okhttpdebugkit.OkHttpDebugCaptureMode
import com.gzq.okhttpdebugkit.OkHttpDebugConfig
import com.gzq.okhttpdebugkit.OkHttpDebugKit
import com.gzq.okhttpdebugkit.debugWithOkHttpDebugKit
import okhttp3.EventListener
import okhttp3.OkHttpClient

object OneNewsOkHttpDebug {
    private val config: OkHttpDebugConfig = OkHttpDebugConfig.builder()
        .serverUrl("ws://127.0.0.1:19090/session")
        .token("demo-token")
        .captureMode(OkHttpDebugCaptureMode.APPLICATION)
        .staticTags(mapOf("source" to "OneNews"))
        .build()

    fun install(context: Context) {
        OkHttpDebugKit.install(context, config)
    }

    @JvmStatic
    @JvmOverloads
    fun apply(
        builder: OkHttpClient.Builder,
        context: Context,
        delegateEventListenerFactory: EventListener.Factory? = null,
    ): OkHttpClient.Builder {
        return builder.debugWithOkHttpDebugKit(context, config, delegateEventListenerFactory)
    }
}
```

Call `OneNewsOkHttpDebug.install(appContext)` from debug app startup before creating clients.

Use `OkHttpDebugCaptureMode.APPLICATION` for ordinary clients. For clients that encrypt/decrypt inside OkHttp interceptors, use `OkHttpDebugCaptureMode.DUAL` so the desktop groups `plain` and `wire` captures under one request.

For a USB device, keep the SDK URL as `ws://127.0.0.1:19090/session` and run:

```bash
adb reverse tcp:19090 tcp:19090
```

For an Android emulator, either use `adb reverse` or set the URL to `ws://10.0.2.2:19090/session`. For a physical device without reverse, use the PC LAN IP.

## OneNews Test Points

Do not modify OneNews from this SDK project. These snippets document where to integrate when testing inside `/Users/gzq/AndroidStudioProjects/OneNews`.

### Main News Client

File:

```text
/Users/gzq/AndroidStudioProjects/OneNews/app/src/main/java/com/bulletin/message/Instance.java:53
```

`createOkHttp()` builds the main news `OkHttpClient` for `BuildConfig.NEWS_API`.

The existing client uses `ElapsedEventListener::new`. Pass it as the delegate factory so the debug timing listener does not replace it:

```java
return OneNewsOkHttpDebug.apply(
        new OkHttpClient.Builder()
                .addInterceptor(new ChuckerInterceptor(UContextHolder.getContext()))
                .addInterceptor(logging)
                .addInterceptor(new SecureHttpInterceptor())
                .dispatcher(new Dispatcher(Dispatchers.IO))
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS),
        UContextHolder.getContext(),
        ElapsedEventListener::new
).build();
```

Main API file:

```text
/Users/gzq/AndroidStudioProjects/OneNews/app/src/main/java/com/bulletin/message/http/ApiServices.java
```

It contains many `@FormUrlEncoded @POST("api")` endpoints, including `getNewsList`, `getNewsDetail`, `hotSearchKeywords`, and `allTopCategory`. These are good form body capture checks.

### Weather Client

File:

```text
/Users/gzq/AndroidStudioProjects/OneNews/app/src/main/java/com/bulletin/message/weather/request/RetrofitHelper.java:29
```

`provideWeatherHttpClient()` builds the weather client for `BuildConfig.WEATHER_API`; weather endpoints use POST APIs.

```java
return OneNewsOkHttpDebug.apply(
        new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new EncryptionInterceptor())
                .dispatcher(new Dispatcher(Dispatchers.IO))
                .connectTimeout(requestTimeOut, TimeUnit.SECONDS)
                .readTimeout(requestTimeOut, TimeUnit.SECONDS)
                .writeTimeout(requestTimeOut, TimeUnit.SECONDS),
        UContextHolder.getContext()
).build();
```

### AQI Client

File:

```text
/Users/gzq/AndroidStudioProjects/OneNews/app/src/main/java/com/bulletin/message/weather/request/RetrofitHelper.java:44
```

`provideAqiHttpClient()` builds the AQI client. AQI calls use:

```text
https://api.waqi.info/feed/{location}?token=<redacted>
```

The SDK redacts query parameters named `token`, `key`, `api_key`, `apikey`, `auth`, and `access_token` by default.

```java
return OneNewsOkHttpDebug.apply(
        new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(requestTimeOut, TimeUnit.SECONDS)
                .readTimeout(requestTimeOut, TimeUnit.SECONDS)
                .writeTimeout(requestTimeOut, TimeUnit.SECONDS),
        UContextHolder.getContext()
).build();
```

### VIP Billing Client

File:

```text
/Users/gzq/AndroidStudioProjects/OneNews/app/src/billing/java/com/bulletin/message/vip/api/VipInstance.kt:24
```

`okHttpClient` is the VIP billing client for `BuildConfig.BILLING_API`.

```kotlin
okHttpClient = OneNewsOkHttpDebug.apply(
    OkHttpClient.Builder()
        .addInterceptor(ChuckerInterceptor(UContextHolder.getContext()))
        .addInterceptor(loggingInterceptor)
        .addInterceptor(VipEncryptionInterceptor())
        .dispatcher(Dispatcher(Dispatchers.IO))
        .connectTimeout(requestTimeOut.toLong(), TimeUnit.SECONDS)
        .readTimeout(requestTimeOut.toLong(), TimeUnit.SECONDS)
        .writeTimeout(requestTimeOut.toLong(), TimeUnit.SECONDS),
    UContextHolder.getContext(),
).build()
```

## Expected Capture Shape

The desktop side receives:

```json
{
  "type": "capture",
  "protocolVersion": 1,
  "id": "generated-id",
  "sessionId": "generated-session",
  "startedAtEpochMs": 1720000000000,
  "durationMs": 42,
  "request": {
    "method": "POST",
    "url": "https://example.test/api",
    "headers": {
      "Content-Type": ["application/x-www-form-urlencoded"]
    },
    "body": "page=1&pageSize=20",
    "bodyTruncated": false,
    "contentType": "application/x-www-form-urlencoded",
    "contentLength": 18
  },
  "response": {
    "code": 200,
    "message": "OK",
    "headers": {
      "Content-Type": ["application/json"]
    },
    "body": "{\"ok\":true}",
    "bodyTruncated": false,
    "contentType": "application/json",
    "contentLength": 11
  }
}
```
