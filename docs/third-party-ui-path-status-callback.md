# Seal UI 路径终态回传适配说明

> 面向：Seal 维护者 / 第三方集成联调  
> 日期：2026-07-12  
> 关联：[`third-party-call-guide.md`](./third-party-call-guide.md)、[`third-party-delegate-integration.md`](./third-party-delegate-integration.md)  
> 问题：第三方（如 PiliPlus）在 `auto_start=false` 走 Seal 配置 UI 下载时，**完成后无任何状态更新**

---

## 1. 现象

调用方（PiliPlus）已实现 L3：

- 使用 `startActivityForResult` / Activity Result 启动 `com.chloemlla.seal.action.DOWNLOAD`
- 动态注册 `com.chloemlla.seal.action.DOWNLOAD_STATUS` 定向广播
- 完成态设计为 Toast / 居中成功卡片（打开、分享）

实际表现：

| 阶段 | 调用方是否收到反馈 | 说明 |
|------|-------------------|------|
| 启动 Seal / 打开配置 UI | 可能有 | `needs_ui` Activity Result / 启动成功 Toast |
| 用户确认后真正入队 | **通常没有** | UI 路径未再发 `accepted` |
| 下载完成 / 失败 / 取消 | **没有** | UI 入队未 `watchTask`，终态广播不发 |

结论：**不是调用方「没做状态 UI」，而是 Seal 在默认 UI 下载路径没有把终态回传。**

---

## 2. 根因（当前代码路径）

### 2.1 会 watch 的路径：auto-start

`ExternalDownloadEntry` → `ExternalDownloadDecision.AutoStart` →  
`ExternalDownloadCoordinator.enqueue(...)`：

1. `downloader.enqueue(task)`
2. `watchTask(taskId, callerPackage, callerRequestId)`
3. 任务到 `Completed` / `Error` / `Canceled` 时  
   `ExternalDownloadStatusReporter.sendStatus(...)` 定向广播到 `callerPackage`

### 2.2 不会 watch 的路径：默认 UI（`auto_start=false`）

1. `QuickDownloadActivity` 收到委托 → `NeedsUi`
2. `deliverNeedsUiResult()` → 仅 `status=needs_ui`（表示「打开配置页」）
3. 用户在 UI 点下载：
   - `DownloadDialogViewModel.downloadWithPreset` → 直接 `downloader.enqueue(Task(...))`
   - `FormatPage` → 直接 `downloader.enqueue(TaskFactory...)`
   - `runCommand` 等同理
4. **没有 `watchTask`，也没有补发 `accepted` / 终态广播**

因此调用方最多感知「已打开 Seal」，真正下载结束时永远收不到事件。

### 2.3 权威源码位置

| 文件 | 职责 |
|------|------|
| `integration/ExternalDownloadCoordinator.kt` | enqueue + watchTask + 终态广播 |
| `integration/ExternalDownloadEntry.kt` | 解析 Intent、门禁、auto-start 入队 |
| `integration/ExternalDownloadStatusReporter`（同 Coordinator 文件内） | `setPackage(caller)` 定向广播 |
| `QuickDownloadActivity.kt` | UI 入口，`deliverNeedsUiResult` |
| `ui/.../DownloadDialogViewModel.kt` | UI 确认后 `downloadWithPreset` / `runCommand` |
| `ui/.../FormatPage.kt` | 格式页确认后 enqueue |

---

## 3. 目标协议行为（修复后）

| 阶段 | 应发 status | 渠道 |
|------|-------------|------|
| 打开配置 UI | `needs_ui` | Activity Result + 可选广播（可保留现状） |
| 用户确认后真正入队 | `accepted` + `task_id` / `task_ids` + `caller_request_id` | 广播（推荐）；Activity Result 可选 |
| 任务完成 | `completed`（可带 `content_uri` / `display_name` / `mime_type`） | **仅** `DOWNLOAD_STATUS` 广播 |
| 任务失败 | `failed` | 广播 |
| 任务取消 | `canceled` | 广播 |

原则：

> **凡是从外部委托发起、且能解析到 `callerPackage` 的入队，都必须 `watchTask`。**

---

## 4. Seal 适配方案（推荐）

### 4.1 External Session（推荐中枢）

在 `ExternalDownloadCoordinator` 增加「外部委托会话」：

```kotlin
object ExternalDownloadCoordinator {
    data class ExternalSession(
        val callerPackage: String,
        val callerRequestId: String?,
    )

    @Volatile
    private var externalSession: ExternalSession? = null

    fun beginExternalSession(callerPackage: String?, callerRequestId: String?) {
        val pkg = callerPackage?.trim().orEmpty()
        externalSession =
            if (pkg.isEmpty()) null
            else ExternalSession(callerPackage = pkg, callerRequestId = callerRequestId)
    }

    fun endExternalSession() {
        externalSession = null
    }

    fun currentSession(): ExternalSession? = externalSession

    /**
     * UI / ViewModel 在 downloader.enqueue(task) 之后调用。
     * session 为空时 no-op（普通站内下载不受影响）。
     */
    fun watchEnqueuedTaskIfExternal(
        context: Context,
        downloader: DownloaderV2,
        task: Task,
        alsoNotifyAccepted: Boolean = true,
    ) {
        val session = externalSession ?: return
        watchTask(
            context = context.applicationContext,
            downloader = downloader,
            taskId = task.id,
            callerPackage = session.callerPackage,
            callerRequestId = session.callerRequestId,
        )
        if (alsoNotifyAccepted) {
            ExternalDownloadStatusReporter.sendStatus(
                context = context.applicationContext,
                targetPackage = session.callerPackage,
                status = ExternalDownloadProtocol.STATUS_ACCEPTED,
                errorCode = ExternalDownloadProtocol.ERROR_OK,
                taskId = task.id,
                taskIds = listOf(task.id),
                callerRequestId = session.callerRequestId,
            )
        }
    }
}
```

说明：

- **不要**在 `DownloaderV2.enqueue` 全局挂钩外部语义，避免污染站内下载。
- `endExternalSession()` 只停止「新任务」绑定；**已 watch 的任务必须继续监听终态**（现有 `watchedTasks` ConcurrentHashMap 可保留）。

### 4.2 QuickDownloadActivity：进入 UI 时建立会话

`ShowUi` 分支：

```kotlin
is ExternalDownloadEntry.HandleResult.ShowUi -> {
    callerPackage = handleResult.accepted.callerPackage
    callerRequestId = handleResult.accepted.request.callerRequestId

    ExternalDownloadCoordinator.beginExternalSession(
        callerPackage = callerPackage,
        callerRequestId = callerRequestId,
    )

    deliverNeedsUiResult() // 仍表示 needs_ui
    // ... ShowSheet / setContent 保持
}
```

`onDestroy`：

```kotlin
override fun onDestroy() {
    ExternalDownloadCoordinator.endExternalSession()
    super.onDestroy()
}
```

若用户取消关闭 sheet 未入队，可发 `canceled`（可选，P1）。

### 4.3 所有 UI 入队点补 watch

| 入口 | 现状 | 改法 |
|------|------|------|
| `DownloadDialogViewModel.downloadWithPreset` | 直接 `downloader.enqueue` | enqueue 后 `watchEnqueuedTaskIfExternal`；批量可一次 `accepted` + 多个 `task_ids` |
| `DownloadDialogViewModel.runCommand` | 同上 | 同上 |
| `FormatPage` 确认 | `downloader.enqueue(TaskFactory...)` | 同上 |
| Playlist 确认入队（如有） | 直接 enqueue | 同上 |

`downloadWithPreset` 示意：

```kotlin
private fun downloadWithPreset(
    urlList: List<String>,
    preferences: DownloadUtil.DownloadPreferences,
) {
    val taskIds = mutableListOf<String>()
    urlList.forEach { url ->
        val task = Task(url = url, preferences = preferences)
        downloader.enqueue(task)
        taskIds += task.id
        ExternalDownloadCoordinator.watchEnqueuedTaskIfExternal(
            context = App.context,
            downloader = downloader,
            task = task,
            alsoNotifyAccepted = false, // 批量统一发一次
        )
    }
    ExternalDownloadCoordinator.currentSession()?.let { session ->
        if (taskIds.isNotEmpty()) {
            ExternalDownloadStatusReporter.sendStatus(
                context = App.context,
                targetPackage = session.callerPackage,
                status = ExternalDownloadProtocol.STATUS_ACCEPTED,
                errorCode = ExternalDownloadProtocol.ERROR_OK,
                taskId = taskIds.first(),
                taskIds = taskIds,
                callerRequestId = session.callerRequestId,
            )
        }
    }
    hideDialog()
}
```

### 4.4 `needs_ui` 与 `accepted` 语义分离

- `needs_ui`：仅表示「Seal 已打开配置 UI」，**不是**入队。
- `accepted`：用户确认后任务已进入 Seal 队列，带 `task_id(s)`。
- 终态：只走 `DOWNLOAD_STATUS` 广播（与现有文档一致）。

二次 `setResult(accepted)` 在 UI 路径上不一定可靠（`needs_ui` 往往已 setResult）；**终态以广播为准**。

### 4.5 包名与 callingPackage

- 广播目标必须是真实 `callingPackage`（含 debug/dev 后缀）。
- 示例：
  - `com.chloemlla.piliplus`
  - `com.chloemlla.piliplus.debug`
  - `com.chloemlla.piliplus.dev`
- `callingPackage == null` 时按现有设计不发终态；文档已说明须用 Activity 启动。
- `EXTRA_CALLER_PACKAGE` 仅作兜底，优先系统 `Activity.callingPackage`。

### 4.6 文档同步

更新 [`third-party-call-guide.md`](./third-party-call-guide.md)：

> `auto_start=false` 打开配置 UI 后，用户在 Seal 内确认下载时，Seal **仍会** watch 任务，并发送 `accepted` 与终态 `completed` / `failed` / `canceled`。  
> 第三方不应假设「只有 auto-start 才有 L3 终态」。

---

## 5. 最小改动清单（优先级）

### P0（必须）

1. `ShowUi` 时 `beginExternalSession(callerPackage, callerRequestId)`
2. UI 所有外部相关 `enqueue` 后 `watchTask`（至少 preset + format + command）
3. 用户确认入队后补发 `accepted`（含 `task_ids`、`caller_request_id`）

### P1

4. `onDestroy` / 取消关闭时 `endExternalSession`（不影响已 watch 任务）
5. 可选：用户未入队直接关闭时广播 `canceled`
6. 单测：UI 路径 enqueue → mock Completed → 断言定向广播到 target package

### P2

7. 更新 call-guide / TODO 状态
8. logcat 标签统一，便于联调

---

## 6. 与调用方分工

| 侧 | 负责 |
|----|------|
| **Seal** | UI 入队后 watch + 发 `accepted` / 终态广播（当前缺口） |
| **第三方（PiliPlus）** | 收广播并做 Toast / 成功卡片 / 打开分享（已实现） |

PiliPlus 关键实现（参考，不在本仓库保证同步）：

- `SealDownloadChannel`：MethodChannel + Activity Result + `DOWNLOAD_STATUS` Receiver
- `SealDownloadUtils`：构造 bilibili 页 URL、订阅状态、完成卡片
- 未安装：Toast + 打开 Seal Releases

---

## 7. 自测清单

```text
前置：
- 安装 Seal + PiliPlus（注意 debug 包名变体）
- Seal：Allow external apps to delegate downloads = 开
- Seal：Allow external auto-start = 关（强制走 UI 路径）

步骤：
1. PiliPlus 视频三点菜单 → 下载视频 / 下载音频
2. 跳转 Seal 配置 UI，确认下载
3. logcat 过滤：ExternalDownloadCoord / ExternalDownloadStatus / SealDownload
4. 期望：
   - 入队后有 accepted（或至少 watch 建立）
   - 完成后有 completed + 可选 content_uri
   - Intent.setPackage 等于 PiliPlus applicationId
5. PiliPlus 出现完成提示 / 成功卡片

adb 补充（仅验证广播 action；真实 callingPackage 仍以 App 启动为准）：
# 第三方真实路径请用 App 内 startActivityForResult，不要只靠 adb
```

---

## 8. 不在本适配范围

- 让第三方嵌入 yt-dlp / 读 Cookie / 指定任意输出路径
- 远程控制 Seal
- 把 CDN 直链当稳定 API
- 修改 PiliPlus 离线缓存（应用内缓存）逻辑

---

## 9. 验收标准

- [x] `auto_start=false` UI 确认下载后，调用方能收到 `accepted`（或等价入队信号）
- [x] 同一任务完成后调用方能收到 `completed`（失败/取消同理）
- [x] `completed` 在可映射文件时尽量带 `content_uri`（只读 grant）
- [x] 站内非外部下载行为不变
- [x] `callerPackage` 为空时不崩溃，且不误发给错误 package
- [x] call-guide 已写明 UI 路径同样支持 L3 终态

---

## 10. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-12 | 初版：记录 UI 路径缺 watchTask 根因与 Seal 适配方案；联调对象 PiliPlus |
| 2026-07-12 | 代码落地：ExternalSession + UI 入队 watch + accepted；文档与 TODO 同步 |