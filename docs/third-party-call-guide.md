# Seal 第三方调用文档

> 面向：要在自己 App 里把下载任务**委托给 Seal** 的开发者  
> 协议版本：`protocol_version = 1`（兼容）/ `2`（Cookie 注入 + keep_sections）  
> 模型：**只委托，不内嵌** — Seal 负责队列、yt-dlp、通知与文件落地  
> 实现参考：`docs/third-party-delegate-integration.md`、`com.chloemlla.seal.integration.ExternalDownloadProtocol`  
> 源码权威：`app/src/main/java/com/chloemlla/seal/integration/`

---

## 1. 你能做什么 / 不能做什么

### 能做
- 把一个或多个 `http/https` 链接交给 Seal
- 可选：请求仅音频、拉字幕
- 可选：自动开始（需用户在 Seal 设置中允许）
- 接收「已接受 / 需打开 UI / 拒绝」的即时结果（Activity Result + 可选定向广播）
- 接收「完成 / 失败 / 取消」的终态定向广播；完成时可能拿到只读 `content://` 文件 URI

### 不能做
- 静默把 yt-dlp 嵌进你的进程
- 注入任意 yt-dlp 命令
- 读取 Seal Cookie / 账号
- 指定任意输出路径、改 Seal 全局设置
- 通过网络远程控制别人手机上的 Seal
- 把 CDN 直链当稳定 API 使用
- 依赖过宽的 FileProvider 路径（Seal 仅对下载相关路径做只读 grant）

---

## 2. 前置条件

1. 用户设备已安装 Seal（release / debug / preview 包名不同，见下表）
2. Seal 设置路径：  
   **Settings → Interface & interaction → External downloads**
3. 相关开关：

| 设置项 | 默认 | 说明 |
|--------|------|------|
| Allow external apps to delegate downloads | 开 | 总开关；关闭后一律 `disabled` |
| Allow external auto-start | 关 | 为开时才允许 `auto_start=true` 静默入队 |
| Accept cookies from external apps | **关** | 为开时才接受 v2 入站 Cookie（任务级，不写全局） |
| Limit external callers | 关 | 开启后仅白名单 package 可调用 |
| Allowed packages | 空 | 白名单文本；支持换行 / `,` / `;` 分隔，每项一个 package name |

4. 调用方应 `setPackage(...)` 明确指向 Seal，避免隐式 Intent 被其它应用截获。
5. 应用内委托请用 `startActivity` / Activity Result API 启动，以便 Seal 解析 `callingPackage`（终态广播依赖它）。

### 包名

| 构建 | applicationId |
|------|----------------|
| release | `com.chloemlla.seal` |
| debug | `com.chloemlla.seal.debug` |
| preview | `com.chloemlla.seal.preview` |

下文示例默认使用 release 包名 `com.chloemlla.seal`。

FileProvider authority：`${applicationId}.provider`  
（release 示例：`com.chloemlla.seal.provider`）

---

## 3. 调用入口一览

| 级别 | 方式 | 何时用 |
|------|------|--------|
| L1 | 系统分享 `ACTION_SEND` / 打开链接 `ACTION_VIEW` | 用户主动分享、浏览器 Open with |
| L2 | 自定义 `com.chloemlla.seal.action.DOWNLOAD` + extras | 应用内一键委托、带参数 |
| L3 | 在 L2 基础上接 Activity Result + 状态广播 | 需要知道是否入队、是否下完、文件 URI |

推荐新集成优先使用 **L2/L3 自定义 action**。

> **UI 路径终态**：uto_start=false 打开 Seal 配置页后，用户确认下载时 Seal **仍会** watchTask 并发送 ccepted 与终态 completed / ailed / canceled（定向广播到 callingPackage）。调用方不要假设「只有 auto-start 才有 L3 终态」。详见 [third-party-ui-path-status-callback.md](./third-party-ui-path-status-callback.md)。

Manifest 已导出入口：

- `QuickDownloadActivity`（对话框式配置 UI，`singleInstance`）
- `MainActivity`（主界面 cold start / `onNewIntent`）

两者都注册了 `DOWNLOAD` / `SEND` / `VIEW`。

---

## 4. 请求协议

### 4.1 Action

```text
com.chloemlla.seal.action.DOWNLOAD
```

兼容（仍可用）：

- `android.intent.action.SEND` + `text/plain`（`Intent.EXTRA_TEXT`）
- `android.intent.action.VIEW` + `http` / `https`（亦可带 `video/*` / `audio/*`）

`DOWNLOAD` 的 intent-filter 要求下列之一：

- `type = "text/plain"`，或
- `data` 为 `http` / `https` URI

因此 L2 示例建议至少设置 `type = "text/plain"`，或直接 `setData(Uri.parse(url))`。

### 4.2 请求 extras

| Key（字符串原样） | 类型 | 必填 | 说明 |
|-------------------|------|------|------|
| `protocol_version` | Int | 建议填 | `1` 或 `2`；缺省按 **1**；超出 `1..2` → `unsupported_version` |
| `url` | String | 与 `urls` / EXTRA_TEXT / data 四选一 | 首选单链接 |
| `urls` | String[] | 可选 | 多链接；与 `url` 等合并去重 |
| `extract_audio` | Boolean | 否 | 仅音频；**不传**则用 Seal 用户偏好（不是默认 false） |
| `download_subtitle` | Boolean | 否 | 下载字幕；**不传**则用用户偏好 |
| `auto_start` | Boolean | 否 | 默认 `false`；`true` 需用户开启自动开始 |
| `open_ui` | Boolean | 否 | 默认 `true`；仅在「自动开始被拒」时影响是否可降级 UI |
| `caller_request_id` | String | 否 | 调用方业务 id，原样回传 |
| `caller_package` | String | 否 | **一般不要填**；仅当 `Activity.callingPackage` 为空时的兜底（Seal 优先用系统 callingPackage） |
| `cookies_format` | String | Cookie 时 | v2：`json_map`（推荐）/ `netscape` / `name_value` |
| `cookies` | String | Cookie 时 | v2：Cookie 载荷；**最大 256 KiB**；超限 `cookie_too_large` |
| `cookies_uri` | String | 可选 | v2：`content://` Netscape 文件 URI（备选） |
| `cookies_mid` | Long/String | 否 | v2：账号 mid（非 secret，仅诊断） |
| `cookies_domain_hint` | String | 否 | v2：map 转换域名，默认 `.bilibili.com` |
| `use_cookies` | Boolean | 否 | v2：有载荷时默认启用任务 Cookie |
| `cookies_required` | Boolean | 否 | v2：默认 false；true 时 materialize 失败则整请求失败 |
| `strip_segments` | Boolean | 否 | v2：调用方已计算剥离（报告在调用方） |
| `keep_sections` | String (JSON) | 否 | v2：`[{"start":s,"end":e},…]`，**秒**；映射到 yt-dlp `--download-sections` |
| `remove_segments` | String (JSON) | 否 | v2：移除段元数据（可选，Seal 不强制使用） |

也接受：

- `Intent.EXTRA_TEXT`：从文本中提取 URL
- `intent.data`：`http(s)://...`

#### Cookie 规则（v2）

- **仅** `protocol_version >= 2` 时解析 Cookie 字段；v1 忽略 Cookie extras。
- Seal 设置「允许外部应用提供 Cookies」默认 **关**：
  - `cookies_required=true` → 拒绝 `cookie_denied` / `cookies_disabled`
  - `cookies_required=false`（推荐）→ **剥离 Cookie 后继续** 匿名/Seal 侧登录下载
- Cookie 写入 **任务级** `cache/external_cookies/<id>.txt`，**不覆盖** 全局 `cookies.txt`，不写入 CookieProfile。
- 任务终态（completed/failed/canceled）或 UI 取消时删除临时文件。
- **禁止** 反向导出 Seal Cookie；状态广播永不含 Cookie 明文。

URL 规则（`ExternalDownloadRequestParser.looksLikeHttpUrl`）：

- 必须以 `http://` 或 `https://` 开头
- `://` 后的 host 非空，且 **host 中包含 `.`**（例如 `https://youtu.be/x` 可以；`https://localhost/x` 当前会被拒绝）

### 4.3 行为矩阵

| auto_start | Seal「允许自动开始」 | open_ui | 结果 |
|------------|----------------------|---------|------|
| false | * | * | 打开 Seal 配置 UI（`needs_ui`）；用户确认下载后发 `accepted` 并 watch 终态 |
| true | 开 | * | 直接入队（`accepted` + `task_id`/`task_ids`） |
| true | 关 | true | 降级打开配置 UI（Activity Result 为 `needs_ui` + `error_code=ok`；另可能先发一条带 `auto_start_denied` 的状态广播） |
| true | 关 | false | 拒绝（`rejected` / `auto_start_denied`） |
| * | 总开关关 | * | 拒绝（`disabled`） |
| * | 白名单模式且 caller 不在名单（或 caller 为空） | * | 拒绝（`caller_denied`） |
| * | 限流触发 | * | 拒绝（`queue_rejected`） |
| * | 无合法 URL | * | 拒绝（`invalid_url`） |

限流：同一 caller package 约 **60 秒内最多 20 次** 决策尝试；caller 为空时计入 `"unknown"` 桶。超出 → `queue_rejected`。

多 URL：

- UI 路径：整表交给配置页
- 自动开始：每个 URL 各入一队任务，返回多个 `task_ids`

---

## 5. 即时返回（Activity Result）

使用 `startActivityForResult` / Activity Result API 时，Seal 会在接受 / 拒绝 / 需要 UI 时 `setResult`，并**同时**向 caller package 发一条定向状态广播（若 `callingPackage` 可解析）。

### resultCode
- `RESULT_OK`：`accepted` 或 `needs_ui`
- `RESULT_CANCELED`：`rejected`

### 返回 extras

| Key | 说明 |
|-----|------|
| `protocol_version` | `1` |
| `status` | `accepted` / `rejected` / `needs_ui` |
| `error_code` | 见第 7 节；成功路径多为 `ok` |
| `error_message` | 可选人类可读信息（拒绝时常有） |
| `task_id` | 单任务 id（accepted 时取第一个） |
| `task_ids` | `String[]` 多任务 id（accepted 时） |
| `caller_request_id` | 回显 |

> 注意：
> - `needs_ui` 只表示「Seal 已打开配置 UI」，**不是**入队。
> - `accepted` 表示任务已进入 Seal 队列（`auto_start` 静默入队，或用户在 UI 里确认下载后都会发；含 `task_id` / `task_ids`）。
> - 真正的完成 / 失败 / 取消只走 `DOWNLOAD_STATUS` 终态广播。
> - **`auto_start=false` 的 UI 路径同样支持 L3**：用户确认下载后 Seal 会 watch 任务并发送 `accepted` 与终态 `completed` / `failed` / `canceled`。第三方不应假设「只有 auto-start 才有终态」。
> - `needs_ui` 的 Activity Result 里 `error_code` 通常是 `ok`；若因 auto-start 被拒而降级 UI，可能另有一条广播带 `error_code=auto_start_denied`。
> - UI 路径上 `needs_ui` 往往已 `setResult`；后续 `accepted` / 终态以定向广播为准。

---

## 6. 终态广播（L3）

### Action

```text
com.chloemlla.seal.action.DOWNLOAD_STATUS
```

Seal 使用 `Intent.setPackage(callerPackage)` **定向发送**，不会全局广播。

### 调用方需要做的

1. 在自己的 App 注册 Receiver（Android 8+ 建议清单 `exported` 或按需动态注册，并匹配该 action）
2. 用 `task_id` / `caller_request_id` 关联业务
3. 对 `content_uri` 只用**读权限**打开（Seal 已 `grantUriPermission` + `FLAG_GRANT_READ_URI_PERMISSION`）

### 广播 extras

| Key | 说明 |
|-----|------|
| `protocol_version` | `1` |
| `status` | `accepted` / `rejected` / `needs_ui` / `completed` / `failed` / `canceled` |
| `error_code` | 见第 7 节 |
| `error_message` | 可选 |
| `task_id` | 当前任务 id |
| `task_ids` | 可选，批量场景 |
| `caller_request_id` | 回显 |
| `caller_package` | 目标 package（Seal 填入的 target） |
| `content_uri` | **仅 `completed` 时可能有**，形如 `content://com.chloemlla.seal.provider/...` |
| `display_name` | 完成时可选，展示名 |
| `mime_type` | 完成时可选，MIME |

### 终态语义

| status | 含义 |
|--------|------|
| `completed` | 该 task 下载完成；可尝试读 `content_uri`（文件缺失或路径不可映射时可能没有 URI） |
| `failed` | 下载失败，`error_code` 多为 `download_failed` |
| `canceled` | 用户或系统取消，`error_code` 多为 `canceled` |

> 若 `callingPackage` 为空：即时 `setResult` 仍可能成功，但**终态广播与 URI grant 都不会发出**（无目标 package / 无法 watch 任务）。

> **网络 / JSON 元数据类失败**：当 yt-dlp 在 info 阶段出现连接中断（如 B 站 `Unable to download JSON metadata` / `TransportError` / `Errno 103`），`error_message` 会使用本地化提示（重试；B 站请启用 Network → Cookies 并登录），而不是原始 errno 堆栈。非传输类错误仍透传 yt-dlp 原文。

### content_uri 与 FileProvider

- Authority：`${applicationId}.provider`
- 只读 grant 给调用方 package
- 路径白名单（`res/xml/provider_paths.xml`）收紧为：
  - 公共 `Download/` 树
  - App 私有 external files
  - cache / internal files  
  **不再**暴露整棵外部存储根路径。
- 不要缓存长期 `content://`；按需打开或复制到自己的缓存。

---

## 7. error_code

| code | 含义 |
|------|------|
| `ok` | 成功接受 / needs_ui 正常打开 / 完成 |
| `disabled` | 用户关闭了外部委托总开关 |
| `auto_start_denied` | 未允许自动开始（且可能 `open_ui=false` 导致硬拒绝） |
| `invalid_url` | 没有可用的 http(s) URL |
| `unsupported_version` | `protocol_version` 不在 `1..2` |
| `caller_denied` | 白名单模式拒绝（含 caller 为空） |
| `queue_rejected` | 限流（约 60s / 20 次） |
| `app_busy` | Seal 正忙（如待处理崩溃报告），需先打开 Seal 处理后再试 |
| `internal_error` | Seal 侧接受任务失败（如下载器不可用） |
| `download_failed` | 终态任务失败 |
| `canceled` | 终态取消 |

---

## 8. 代码示例

### 8.1 最简：打开 Seal 配置 UI（推荐默认）

```kotlin
val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
    setPackage("com.chloemlla.seal")
    type = "text/plain"
    putExtra("protocol_version", 1)
    putExtra("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    putExtra("extract_audio", false)
    putExtra("auto_start", false)
    putExtra("caller_request_id", "my-req-1")
}
startActivity(intent)
```

### 8.2 多链接

```kotlin
val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
    setPackage("com.chloemlla.seal")
    type = "text/plain"
    putExtra("protocol_version", 1)
    putExtra(
        "urls",
        arrayOf(
            "https://www.youtube.com/watch?v=aaa",
            "https://www.youtube.com/watch?v=bbb",
        ),
    )
}
startActivity(intent)
```

### 8.3 仅音频 + 字幕

```kotlin
val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
    setPackage("com.chloemlla.seal")
    type = "text/plain"
    putExtra("protocol_version", 1)
    putExtra("url", videoUrl)
    putExtra("extract_audio", true)
    putExtra("download_subtitle", true)
}
startActivity(intent)
```

### 8.4 系统分享文本（L1）

```kotlin
val intent = Intent(Intent.ACTION_SEND).apply {
    setPackage("com.chloemlla.seal")
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, "看看这个 https://example.com/video")
}
startActivity(intent)
```

### 8.5 Activity Result（L3 即时结果）

```kotlin
private val sealDownloadLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val status = data?.getStringExtra("status")
        val errorCode = data?.getStringExtra("error_code")
        val taskId = data?.getStringExtra("task_id")
        val taskIds = data?.getStringArrayExtra("task_ids")
        val requestId = data?.getStringExtra("caller_request_id")

        when {
            result.resultCode == Activity.RESULT_OK && status == "accepted" -> {
                // 已入队；等 DOWNLOAD_STATUS 终态
            }
            result.resultCode == Activity.RESULT_OK && status == "needs_ui" -> {
                // 用户正在 Seal 配置；仍要等终态广播（若后续用户确认下载）
            }
            else -> {
                // rejected：看 error_code
            }
        }
    }

fun delegateToSeal(videoUrl: String, requestId: String) {
    val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
        setPackage("com.chloemlla.seal")
        type = "text/plain"
        putExtra("protocol_version", 1)
        putExtra("url", videoUrl)
        putExtra("auto_start", true)   // 需用户已开 auto-start
        putExtra("open_ui", true)      // 未允许自动开始时降级 UI
        putExtra("caller_request_id", requestId)
    }
    sealDownloadLauncher.launch(intent)
}
```

### 8.6 无 UI 自动开始（需用户设置）

```kotlin
val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
    setPackage("com.chloemlla.seal")
    type = "text/plain"
    putExtra("protocol_version", 1)
    putExtra("url", videoUrl)
    putExtra("auto_start", true)
    putExtra("open_ui", false) // 不允许自动开始时直接 rejected
    putExtra("caller_request_id", requestId)
}
// 请确保用户已开启 Allow external auto-start
startActivityForResult(intent, REQUEST_SEAL_DOWNLOAD)
```

### 8.7 注册终态 Receiver（AndroidManifest 示例）

```xml
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

        val status = intent.getStringExtra("status")
        val taskId = intent.getStringExtra("task_id")
        val requestId = intent.getStringExtra("caller_request_id")
        val errorCode = intent.getStringExtra("error_code")
        val errorMessage = intent.getStringExtra("error_message")
        val contentUri = intent.getStringExtra("content_uri")
        val displayName = intent.getStringExtra("display_name")
        val mimeType = intent.getStringExtra("mime_type")

        when (status) {
            "completed" -> {
                // contentUri 可能为 content://com.chloemlla.seal.provider/...
                // ContentResolver.openInputStream(Uri.parse(contentUri))
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

> 安全建议：校验 extras 来源与 `task_id` / `caller_request_id` 是否属于你发起的委托；不要盲目信任任意广播内容。

### 8.8 读取完成文件（示意）

```kotlin
fun openDelegatedFile(context: Context, contentUri: String) {
    val uri = Uri.parse(contentUri)
    context.contentResolver.openInputStream(uri)?.use { input ->
        // 复制到你的缓存目录或交给播放器
    }
}
```

### 8.9 检测 Seal 是否安装 / 是否支持协议

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

- `com.chloemlla.seal.external_download_protocol_version` = `2`
- `com.chloemlla.seal.external_download_max_protocol_version` = `2`

### 8.10 adb 快速自测

```bash
# 打开配置 UI（component 明确指向 QuickDownloadActivity）
adb shell am start -a com.chloemlla.seal.action.DOWNLOAD \
  -n com.chloemlla.seal/.QuickDownloadActivity \
  -t text/plain \
  --es url "https://www.youtube.com/watch?v=dQw4w9WgXcQ" \
  --ei protocol_version 1

# 分享文本
adb shell am start -a android.intent.action.SEND \
  -n com.chloemlla.seal/.QuickDownloadActivity \
  -t text/plain \
  --es android.intent.extra.TEXT "看看这个 https://example.com/video"
```

> `auto_start` 路径依赖用户设置与 calling package；用 adb 时 calling package 可能为空，白名单模式或自动开始 / 终态广播场景下行为可能与真实 App 调用不同。

---

## 9. 推荐集成流程

```
1. 检测 Seal 是否安装（可选读 meta-data 协议版本）
2. 构造 DOWNLOAD Intent（setPackage + type/text/plain 或 data + url + protocol_version）
3. 默认 auto_start=false → 用户在 Seal 里确认（最稳）
4. 若产品需要无 UI：引导用户打开「Allow external auto-start」，再 auto_start=true
5. 用 Activity Result 处理 accepted / rejected / needs_ui
6. 用 DOWNLOAD_STATUS 定向广播处理 completed / failed / canceled
7. completed 时用 content_uri 只读访问文件（可结合 display_name / mime_type）
```

---

## 10. 常见问题

**Q: 为什么 auto_start 没静默下？**  
A: 默认关。用户需在 Seal 中开启 **Allow external auto-start**。否则会降级 UI（`open_ui=true`）或 reject（`open_ui=false`）。

**Q: 为什么被 rejected / caller_denied？**  
A: 用户开了白名单且没把你的 package 写进去；或 Seal 解析不到 calling package（白名单开启时 caller 为空也会拒）。

**Q: 为什么只有 needs_ui 没有 completed？**  
A: 常见原因：用户还在配置页、取消了下载、未注册 `DOWNLOAD_STATUS` Receiver，或 Seal 解析不到 `callingPackage`（无法定向广播）。从 2026-07-12 起，UI 路径在用户确认入队后也会 `watchTask` 并回传 `accepted` + 终态；若仍无 `completed`，优先核对 Receiver 与包名变体（debug/dev）。

**Q: callingPackage 为空会怎样？**  
A: 即时 result 仍可能 `setResult`；**终态广播与 content URI grant 不会发出**。请用 Activity 启动（`startActivity` / Activity Result），不要纯后台瞎发隐式 Intent。

**Q: 能指定清晰度 / 输出目录吗？**  
A: 当前协议不支持。只暴露 `extract_audio`、`download_subtitle` 等安全子集；其余用用户在 Seal 里的预设。外部路径**不会**注入自定义 yt-dlp 命令模板。

**Q: debug 包怎么调？**  
A: `setPackage("com.chloemlla.seal.debug")`，并确保装的是 debug 构建。FileProvider 为 `com.chloemlla.seal.debug.provider`。

**Q: 白名单怎么写？**  
A: Settings 里 Allowed packages 支持每行一个，或用 `,` / `;` 分隔，例如：
```text
com.example.app
com.other.app, com.third.app
```

**Q: Intent 没被 Seal 接住？**  
A: 检查是否 `setPackage`、是否设置了 `type=text/plain` 或 `http(s)` data、包名是否与构建变体一致。

**Q: content_uri 打不开？**  
A: 确认是 `completed` 广播里的 URI、仍持有临时读权限、文件未被用户移动删除。请立刻读或复制，不要依赖永久路径。

---

## 11. 常量速查（与源码一致）

```text
ACTION_DOWNLOAD         = com.chloemlla.seal.action.DOWNLOAD
ACTION_DOWNLOAD_STATUS  = com.chloemlla.seal.action.DOWNLOAD_STATUS

protocol_version        = 1  (MIN=1, MAX=1)

# request
url / urls / extract_audio / download_subtitle
auto_start / open_ui / caller_request_id
caller_package          # 兜底，优先系统 callingPackage

# response / status
status / error_code / error_message
task_id / task_ids
content_uri / display_name / mime_type
caller_request_id / caller_package

# status values
accepted / rejected / needs_ui / completed / failed / canceled

# error_code values
ok / disabled / auto_start_denied / invalid_url
unsupported_version / caller_denied / queue_rejected
internal_error / download_failed / canceled
```

源码权威定义：

| 文件 | 职责 |
|------|------|
| `app/src/main/java/com/chloemlla/seal/integration/ExternalDownloadProtocol.kt` | 常量 |
| `ExternalDownloadRequestParser.kt` | 解析 URL / extras |
| `ExternalDownloadGate.kt` | 开关 / 白名单 / auto-start 决策 |
| `ExternalDownloadEntry.kt` | Activity 共用入口 |
| `ExternalDownloadCoordinator.kt` | 入队、限流、FileProvider grant、状态广播 |

---

## 12. 相关文档

| 文件 | 内容 |
|------|------|
| 本文件 | 第三方调用手册（怎么调） |
| [`third-party-delegate-integration.md`](./third-party-delegate-integration.md) | 协议与能力说明（英文技术摘要） |
| [`third-party-delegate-integration-TODO.md`](./third-party-delegate-integration-TODO.md) | 设计 TODO / 落地状态 |
