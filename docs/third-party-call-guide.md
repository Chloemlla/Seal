# Seal 第三方调用文档

> 面向：要在自己 App 里把下载任务**委托给 Seal** 的开发者  
> 协议版本：`protocol_version = 1`  
> 模型：**只委托，不内嵌** — Seal 负责队列、yt-dlp、通知与文件落地  
> 实现参考：`docs/third-party-delegate-integration.md`、`com.chloemlla.seal.integration.ExternalDownloadProtocol`

---

## 1. 你能做什么 / 不能做什么

### 能做
- 把一个或多个 `http/https` 链接交给 Seal
- 可选：请求仅音频、拉字幕
- 可选：自动开始（需用户在 Seal 设置中允许）
- 接收「已接受 / 需打开 UI / 拒绝」的即时结果
- 接收「完成 / 失败 / 取消」的终态广播，完成时可能拿到只读 `content://` 文件 URI

### 不能做
- 静默把 yt-dlp 嵌进你的进程
- 注入任意 yt-dlp 命令
- 读取 Seal Cookie / 账号
- 指定任意输出路径、改 Seal 全局设置
- 通过网络远程控制别人手机上的 Seal
- 把 CDN 直链当稳定 API 使用

---

## 2. 前置条件

1. 用户设备已安装 Seal（release / debug / preview 包名不同，见下表）
2. Seal 设置路径：  
   **设置 → Interface & interaction → External downloads**
3. 相关开关：

| 设置项 | 默认 | 说明 |
|--------|------|------|
| Allow external apps to delegate downloads | 开 | 总开关；关闭后一律拒绝 |
| Allow external auto-start | 关 | 为开时才允许 `auto_start=true` 静默入队 |
| Limit external callers | 关 | 开启后仅白名单 package 可调用 |
| Allowed packages | 空 | 每行一个 package name |

4. 调用方应 `setPackage(...)` 明确指向 Seal，避免隐式 Intent 被其它应用截获。

### 包名

| 构建 | applicationId |
|------|----------------|
| release | `com.chloemlla.seal` |
| debug | `com.chloemlla.seal.debug` |
| preview | `com.chloemlla.seal.preview` |

下文示例默认使用 release 包名 `com.chloemlla.seal`。

---

## 3. 调用入口一览

| 级别 | 方式 | 何时用 |
|------|------|--------|
| L1 | 系统分享 `ACTION_SEND` / 打开链接 `ACTION_VIEW` | 用户主动分享、浏览器 Open with |
| L2 | 自定义 `com.chloemlla.seal.action.DOWNLOAD` + extras | 应用内一键委托、带参数 |
| L3 | 在 L2 基础上接 Activity Result + 状态广播 | 需要知道是否入队、是否下完、文件 URI |

推荐新集成优先使用 **L2/L3 自定义 action**。

---

## 4. 请求协议

### 4.1 Action

```text
com.chloemlla.seal.action.DOWNLOAD
```

兼容（仍可用）：

- `android.intent.action.SEND` + `text/plain`（`Intent.EXTRA_TEXT`）
- `android.intent.action.VIEW` + `http` / `https`

### 4.2 请求 extras

| Key（字符串原样） | 类型 | 必填 | 说明 |
|-------------------|------|------|------|
| `protocol_version` | Int | 建议填 | 当前仅支持 `1`；缺省按 1 处理 |
| `url` | String | 与 `urls` / EXTRA_TEXT / data 四选一 | 首选单链接 |
| `urls` | String[] | 可选 | 多链接 |
| `extract_audio` | Boolean | 否 | 仅音频；不传则用 Seal 用户偏好 |
| `download_subtitle` | Boolean | 否 | 下载字幕；不传则用用户偏好 |
| `auto_start` | Boolean | 否 | 默认 `false`；`true` 需用户开启自动开始 |
| `open_ui` | Boolean | 否 | 默认 `true`；自动开始被拒且为 `false` 时会直接 reject |
| `caller_request_id` | String | 否 | 调用方自己的业务 id，原样回传 |

也接受：

- `Intent.EXTRA_TEXT`：从文本中提取 URL
- `intent.data`：`http(s)://...`

URL 规则：必须是 `http://` 或 `https://`，且带 host。

### 4.3 行为矩阵

| auto_start | Seal「允许自动开始」 | open_ui | 结果 |
|------------|----------------------|---------|------|
| false | * | * | 打开 Seal 配置 UI（`needs_ui`） |
| true | 开 | * | 直接入队（`accepted` + task_id） |
| true | 关 | true | 降级打开配置 UI（`needs_ui`，error 可带 `auto_start_denied`） |
| true | 关 | false | 拒绝（`rejected` / `auto_start_denied`） |
| * | 总开关关 | * | 拒绝（`disabled`） |
| * | 白名单模式且 caller 不在名单 | * | 拒绝（`caller_denied`） |

限流：同一 caller 约 **60 秒内最多 20 次**，超出 `queue_rejected`。

---

## 5. 即时返回（Activity Result）

使用 `startActivityForResult` / Activity Result API 时，Seal 会在接受/拒绝/需要 UI 时 `setResult`。

### resultCode
- `RESULT_OK`：`accepted` 或 `needs_ui`
- `RESULT_CANCELED`：`rejected`

### 返回 extras

| Key | 说明 |
|-----|------|
| `protocol_version` | 1 |
| `status` | `accepted` / `rejected` / `needs_ui` |
| `error_code` | 见第 7 节 |
| `error_message` | 可选人类可读信息 |
| `task_id` | 单任务 id（accepted 时） |
| `task_ids` | String[] 多任务 id |
| `caller_request_id` | 回显 |

> 注意：`needs_ui` / `accepted` 只代表「Seal 已接住请求」。**下载真正完成**请听第 6 节广播。

---

## 6. 终态广播（L3）

### Action

```text
com.chloemlla.seal.action.DOWNLOAD_STATUS
```

Seal 会 `intent.setPackage(你的包名)`，只打给你，不是全局隐式广播。

### 你需要做的

1. 在自己的 App 注册 Receiver（Android 8+ 建议清单导出或按需动态注册，并匹配该 action）
2. 从 extras 读状态与 URI
3. 对 `content_uri` 只用**读权限**打开（Seal 已 `grantUriPermission`）

### 广播 extras

| Key | 说明 |
|-----|------|
| `status` | `accepted` / `rejected` / `needs_ui` / `completed` / `failed` / `canceled` |
| `error_code` | 见第 7 节 |
| `error_message` | 可选 |
| `task_id` / `task_ids` | 任务 id |
| `caller_request_id` | 回显 |
| `content_uri` | 完成时可能有，形如 `content://com.chloemlla.seal.provider/...` |
| `display_name` / `mime_type` | 可选（当前实现可能为空） |
| `caller_package` | 目标 package（你的包名） |

### 终态

| status | 含义 |
|--------|------|
| `completed` | 该 task 下载完成，可尝试读 `content_uri` |
| `failed` | 下载失败 |
| `canceled` | 取消 |

---

## 7. error_code 表

| code | 含义 | 调用方建议 |
|------|------|------------|
| `ok` | 正常 | — |
| `disabled` | 用户关闭外部委托 | 引导用户打开 Seal 设置 |
| `auto_start_denied` | 未允许自动开始 | 改 `auto_start=false` 走 UI，或引导开启设置 |
| `invalid_url` | 没有合法 http(s) URL | 检查传入内容 |
| `unsupported_version` | 协议版本不支持 | 降级或提示更新 Seal |
| `caller_denied` | 白名单拒绝 | 让用户把你的 package 加入白名单 |
| `queue_rejected` | 触发限流 | 退避重试 |
| `internal_error` | Seal 内部错误 | 稍后重试 / 提示用户 |
| `download_failed` | 任务执行失败 | 展示 error_message |
| `canceled` | 任务取消 | 按业务处理 |

---

## 8. 代码示例

### 8.1 最简单：打开配置 UI 下载（Kotlin）

```kotlin
fun delegateToSeal(url: String) {
    val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
        setPackage("com.chloemlla.seal")
        type = "text/plain"
        putExtra("protocol_version", 1)
        putExtra("url", url)
        putExtra("auto_start", false)
        putExtra("caller_request_id", "ui-${System.currentTimeMillis()}")
    }
    startActivity(intent)
}
```

### 8.2 分享文本兼容（L1）

```kotlin
fun shareTextToSeal(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        setPackage("com.chloemlla.seal")
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(intent)
}
```

### 8.3 打开链接兼容（L1）

```kotlin
fun openUrlWithSeal(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage("com.chloemlla.seal")
    }
    startActivity(intent)
}
```

### 8.4 自动开始 + Activity Result（Kotlin）

```kotlin
private val sealDownloadLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val status = data?.getStringExtra("status")
        val errorCode = data?.getStringExtra("error_code")
        val taskId = data?.getStringExtra("task_id")
        val requestId = data?.getStringExtra("caller_request_id")
        when (status) {
            "accepted" -> { /* 已入队，等待 DOWNLOAD_STATUS 终态 */ }
            "needs_ui" -> { /* Seal 已弹出配置页 */ }
            "rejected" -> { /* 根据 errorCode 提示用户 */ }
            else -> { /* 无结果或旧版 Seal */ }
        }
    }

fun enqueueOnSeal(url: String, requestId: String) {
    val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
        setPackage("com.chloemlla.seal")
        putExtra("protocol_version", 1)
        putExtra("url", url)
        putExtra("extract_audio", false)
        putExtra("download_subtitle", false)
        putExtra("auto_start", true)
        putExtra("open_ui", false)
        putExtra("caller_request_id", requestId)
    }
    sealDownloadLauncher.launch(intent)
}
```

### 8.5 多链接

```kotlin
fun delegateMany(urls: Array<String>) {
    val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
        setPackage("com.chloemlla.seal")
        putExtra("protocol_version", 1)
        putExtra("urls", urls)
        putExtra("auto_start", false)
    }
    startActivity(intent)
}
```

### 8.6 注册终态 Receiver（AndroidManifest 示例）

```xml
<!-- 在你的 App 的 AndroidManifest.xml -->
<receiver
    android:name=".SealDownloadStatusReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.chloemlla.seal.action.DOWNLOAD_STATUS" />
    </intent-filter>
</receiver>
```

```kotlin
class SealDownloadStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.chloemlla.seal.action.DOWNLOAD_STATUS") return

        val status = intent.getStringExtra("status") ?: return
        val taskId = intent.getStringExtra("task_id")
        val requestId = intent.getStringExtra("caller_request_id")
        val errorCode = intent.getStringExtra("error_code")
        val errorMessage = intent.getStringExtra("error_message")
        val contentUri = intent.getStringExtra("content_uri")

        when (status) {
            "completed" -> {
                // contentUri 可能为 content://...
                // 使用 ContentResolver.openInputStream(Uri.parse(contentUri))
            }
            "failed" -> {
                // errorCode == download_failed
            }
            "canceled" -> {
                // 用户或系统取消
            }
            // accepted / rejected / needs_ui 也可能通过广播送达
        }
    }
}
```

### 8.7 读取完成文件（示意）

```kotlin
fun openDelegatedFile(context: Context, contentUri: String) {
    val uri = Uri.parse(contentUri)
    context.contentResolver.openInputStream(uri)?.use { input ->
        // 复制到你的缓存目录或交给播放器
    }
}
```

### 8.8 检测 Seal 是否安装 / 是否支持协议

```kotlin
fun isSealInstalled(context: Context, packageName: String = "com.chloemlla.seal"): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

fun sealProtocolVersion(context: Context, packageName: String = "com.chloemlla.seal"): Int? {
    return try {
        val ai = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        ai.metaData?.getInt("com.chloemlla.seal.external_download_protocol_version")
    } catch (_: Exception) {
        null
    }
}
```

Application meta-data：

- `com.chloemlla.seal.external_download_protocol_version` = `1`
- `com.chloemlla.seal.external_download_max_protocol_version` = `1`

### 8.9 adb 快速自测

```bash
# 打开配置 UI
adb shell am start -a com.chloemlla.seal.action.DOWNLOAD \
  -n com.chloemlla.seal/.QuickDownloadActivity \
  --es url "https://www.youtube.com/watch?v=dQw4w9WgXcQ" \
  --ei protocol_version 1

# 分享文本
adb shell am start -a android.intent.action.SEND \
  -n com.chloemlla.seal/.QuickDownloadActivity \
  -t text/plain \
  --es android.intent.extra.TEXT "看看这个 https://example.com/video"
```

> `auto_start` 路径依赖用户设置与 calling package；用 adb 时 calling package 可能为空，白名单模式或自动开始场景下行为可能与真实 App 调用不同。

---

## 9. 推荐集成流程

```
1. 检测 Seal 是否安装（可选读 meta-data 协议版本）
2. 构造 DOWNLOAD Intent（setPackage + url + protocol_version）
3. 默认 auto_start=false → 用户在 Seal 里确认（最稳）
4. 若产品需要无 UI：引导用户打开「Allow external auto-start」，再 auto_start=true
5. 用 Activity Result 处理 accepted / rejected / needs_ui
6. 用 DOWNLOAD_STATUS 广播处理 completed / failed / canceled
7. completed 时用 content_uri 只读访问文件
```

---

## 10. 常见问题

**Q: 为什么 auto_start 没静默下？**  
A: 默认关。用户需在 Seal 中开启 **Allow external auto-start**。否则会降级 UI 或 reject。

**Q: 为什么被 rejected / caller_denied？**  
A: 用户开了白名单且没把你的 package 写进去。

**Q: 为什么只有 needs_ui 没有 completed？**  
A: 用户可能还在配置页，或你没注册 `DOWNLOAD_STATUS` Receiver。完成态只走广播（且需要 Seal 能解析到 callingPackage）。

**Q: callingPackage 为空会怎样？**  
A: 即时 result 仍可能 setResult；**终态广播可能发不出去**（无目标 package）。请用 Activity 启动（`startActivity` / `startActivityForResult`），不要纯后台瞎发隐式 Intent。

**Q: 能指定清晰度 / 输出目录吗？**  
A: 当前协议不支持。只暴露 `extract_audio`、`download_subtitle` 等安全子集；其余用用户在 Seal 里的预设。

**Q: debug 包怎么调？**  
A: `setPackage("com.chloemlla.seal.debug")`，并确保装的是 debug 构建。

---

## 11. 常量速查（与源码一致）

```text
ACTION_DOWNLOAD         = com.chloemlla.seal.action.DOWNLOAD
ACTION_DOWNLOAD_STATUS  = com.chloemlla.seal.action.DOWNLOAD_STATUS

protocol_version        = 1

# request
url / urls / extract_audio / download_subtitle
auto_start / open_ui / caller_request_id

# response
status / error_code / error_message
task_id / task_ids / content_uri
```

源码权威定义：

`app/src/main/java/com/junkfood/seal/integration/ExternalDownloadProtocol.kt`

---

## 12. 相关文档

| 文件 | 内容 |
|------|------|
| 本文件 | 第三方调用手册（怎么调） |
| [`third-party-delegate-integration.md`](./third-party-delegate-integration.md) | 协议与能力说明（英文技术摘要） |
| [`third-party-delegate-integration-TODO.md`](./third-party-delegate-integration-TODO.md) | 设计 TODO / 落地状态 |
