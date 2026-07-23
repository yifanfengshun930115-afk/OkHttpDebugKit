# OkHttpDebugKit

Debug-only Android OkHttp capture SDK.

The SDK adds an OkHttp `Interceptor` and optional `EventListener` wrapper that send safe request and response captures to a desktop collector over WebSocket.

Default endpoint:

```text
ws://127.0.0.1:19090/session
```

Default body limit is 1 MiB. Text, JSON, XML, and `application/x-www-form-urlencoded` payloads are captured as strings. Binary payloads record metadata only. Duplex and one-shot request bodies are skipped. Response bodies are captured with `Response.peekBody`, so application code still receives the original body.

## Capture Modes

`OkHttpDebugConfig` defaults to `OkHttpDebugCaptureMode.APPLICATION`, which captures one application-level view per OkHttp call and is the right default for projects without custom encryption interceptors.

Projects that rewrite encrypted requests/responses inside OkHttp can opt into `OkHttpDebugCaptureMode.DUAL`. In dual mode the SDK emits two linked captures with the same `groupId`:

- `plain`: outer application view, usually business request data and decrypted response data.
- `wire`: innermost application view, usually the transformed request and response text passed toward the server.

The release no-op artifact exposes the same mode API but still performs no capture work.

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
./gradlew releaseOkHttpDebugKitPackages
```

Artifacts are written to `build/okhttp-debug-kit-packages/`. Each module
produces a plain release AAR, a release AAR with embedded sources, and a
`sources.jar`.

Use the real artifact for debug/internal diagnostic variants and the no-op
artifact for production release variants.

See [docs/INTEGRATION.md](docs/INTEGRATION.md) for OneNews integration notes.
