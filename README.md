# OkHttpDebugKit

Debug-only Android OkHttp capture SDK.

The SDK adds an OkHttp `Interceptor` and optional `EventListener` wrapper that send safe request and response captures to a desktop collector over WebSocket.

Default endpoint:

```text
ws://127.0.0.1:19090/session
```

Default body limit is 1 MiB. Text, JSON, XML, and `application/x-www-form-urlencoded` payloads are captured as strings. Binary payloads record metadata only. Duplex and one-shot request bodies are skipped. Response bodies are captured with `Response.peekBody`, so application code still receives the original body.

## Public API

- `OkHttpDebugKit.install(context, config)`
- `OkHttpDebugConfig.builder()`
- `DebugConnectionManager`
- `OkHttpDebugInterceptor`
- `OkHttpDebugEventListener`
- `OkHttpClient.Builder.debugWithOkHttpDebugKit(...)`

## Artifacts

Build the real debug AAR and release-safe no-op AAR with:

```bash
./gradlew :okhttp-debug-kit:assembleRelease :okhttp-debug-kit-noop:assembleRelease
```

Use the real artifact for debug/internal diagnostic variants and the no-op
artifact for production release variants.

See [docs/INTEGRATION.md](docs/INTEGRATION.md) for OneNews integration notes.
