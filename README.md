# OkHttpDebugKit

## 中文

OkHttpDebugKit 是一个仅建议在 debug、internal 或诊断包中使用的 Android OkHttp 捕获 SDK。它通过 OkHttp `Interceptor` 和可选的 `EventListener` 包装器，把请求和响应数据发送到桌面端调试工具。

这个项目不能单独完成抓包展示。它必须和桌面端 [OkHttp Debug Desktop](https://github.com/yifanfengshun930115-afk/OkHttpDebugDesktop) 一起使用：Android 插件负责捕获并发送数据，桌面端负责监听、展示、过滤、导出和问题诊断。

默认连接地址：

```text
ws://127.0.0.1:19090/session
```

USB 调试时，桌面端会自动或手动执行 `adb reverse tcp:19090 tcp:19090`，这样 Android 应用连接 `127.0.0.1:19090` 就能连到电脑上的桌面端。局域网模式下，请把 endpoint 改成桌面端电脑的 IP，例如 `ws://192.168.1.10:19090/session`。

### 捕获能力

- 默认 body 限制为 1 MiB。
- 文本、JSON、XML、`application/x-www-form-urlencoded` 会按字符串捕获。
- 二进制内容只记录元信息，不记录完整 body。
- 自动跳过 duplex 和 one-shot request body。
- 响应体通过 `Response.peekBody` 捕获，不会影响业务代码继续读取原始响应体。

### 捕获模式

`OkHttpDebugConfig` 默认使用 `OkHttpDebugCaptureMode.APPLICATION`，适合没有自定义加密/解密拦截器的普通项目。

如果项目在 OkHttp 拦截器中对接口做加密、签名、解密或响应改写，可以使用 `OkHttpDebugCaptureMode.DUAL`。双阶段模式会发送同一个 `groupId` 下的两条捕获：

- `plain`: 外层应用视图，通常是业务请求数据和解密后的响应数据。
- `wire`: 内层传输视图，通常是实际发往服务器或从服务器收到的加密/变换后文本。

桌面端会把这两条数据配对展示，方便同时查看加密文本和解密文本。

### 公开 API

- `OkHttpDebugKit.install(context, config)`
- `OkHttpDebugConfig.builder()`
- `DebugConnectionManager`
- `OkHttpDebugInterceptor`
- `OkHttpDebugEventListener`
- `OkHttpClient.Builder.debugWithOkHttpDebugKit(...)`

### AAR 产物

执行下面的命令打出真实 debug AAR 和 release-safe no-op AAR：

```bash
./gradlew releaseOkHttpDebugKitPackages
```

产物会输出到：

```text
build/okhttp-debug-kit-packages/
```

每个模块都会生成普通 release AAR、内嵌源码的 release AAR 和 `sources.jar`。

建议：

- debug/internal/diagnostic 变体依赖真实 `okhttp-debug-kit` AAR。
- production release 变体依赖 `okhttp-debug-kit-noop` AAR。

no-op AAR 保持相同公开 API，但不会建立 WebSocket、不会添加有效捕获逻辑，也不会发送接口数据。

OneNews 接入示例见：[docs/INTEGRATION.md](docs/INTEGRATION.md)

## English

OkHttpDebugKit is an Android OkHttp capture SDK intended for debug, internal, or diagnostic builds. It adds an OkHttp `Interceptor` and an optional `EventListener` wrapper that send request and response captures to the desktop debugging tool.

This project is not useful by itself. It must be used together with [OkHttp Debug Desktop](https://github.com/yifanfengshun930115-afk/OkHttpDebugDesktop): the Android plugin captures and sends data, while the desktop app listens, displays, filters, exports, and helps diagnose issues.

Default endpoint:

```text
ws://127.0.0.1:19090/session
```

For USB debugging, the desktop app can automatically or manually run `adb reverse tcp:19090 tcp:19090`, so an Android app connecting to `127.0.0.1:19090` reaches the desktop app on the computer. For LAN mode, set the endpoint to the desktop machine's IP, for example `ws://192.168.1.10:19090/session`.

### Capture Behavior

- Default body limit is 1 MiB.
- Text, JSON, XML, and `application/x-www-form-urlencoded` payloads are captured as strings.
- Binary payloads record metadata only instead of full bodies.
- Duplex and one-shot request bodies are skipped.
- Response bodies are captured with `Response.peekBody`, so application code still receives the original body.

### Capture Modes

`OkHttpDebugConfig` defaults to `OkHttpDebugCaptureMode.APPLICATION`, which is the right default for projects without custom encryption or decryption interceptors.

Projects that encrypt, sign, decrypt, or rewrite traffic inside OkHttp interceptors can use `OkHttpDebugCaptureMode.DUAL`. Dual mode emits two linked captures with the same `groupId`:

- `plain`: outer application view, usually business request data and decrypted response data.
- `wire`: inner transport view, usually the transformed text sent to or received from the server.

The desktop app pairs those captures so encrypted and decrypted data can be inspected together.

### Public API

- `OkHttpDebugKit.install(context, config)`
- `OkHttpDebugConfig.builder()`
- `DebugConnectionManager`
- `OkHttpDebugInterceptor`
- `OkHttpDebugEventListener`
- `OkHttpClient.Builder.debugWithOkHttpDebugKit(...)`

### AAR Artifacts

Build the real debug AAR and release-safe no-op AAR with:

```bash
./gradlew releaseOkHttpDebugKitPackages
```

Artifacts are written to:

```text
build/okhttp-debug-kit-packages/
```

Each module produces a plain release AAR, a release AAR with embedded sources, and a `sources.jar`.

Recommended usage:

- Use the real `okhttp-debug-kit` AAR for debug, internal, or diagnostic variants.
- Use the `okhttp-debug-kit-noop` AAR for production release variants.

The no-op AAR keeps the same public API but does not open WebSockets, add active capture logic, or send traffic data.

See [docs/INTEGRATION.md](docs/INTEGRATION.md) for OneNews integration notes.
