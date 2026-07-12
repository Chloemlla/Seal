# Seal 第三方集成 TODO（仅委托，不内嵌）

> 状态：设计冻结 / 待实现  
> 日期：2026-07-12  
> 原则：**第三方只能“委托 Seal”完成下载**；不提供 SDK 内核、不暴露 yt-dlp 进程、不做远程下载 API。

---

## 1. 目标与边界

### 1.1 目标
- 让其它本机 App 通过标准 Android 机制把下载任务交给 Seal。
- Seal 始终是下载执行者（队列、yt-dlp、通知、文件落地、历史）。
- 对外协议稳定、可版本化、可鉴权、可关闭。

### 1.2 非目标（明确不做）
- [ ] 任意 yt-dlp 命令字符串对外注入
- [ ] Cookie / 账号凭据导出或跨 App 读取
- [ ] 任意输出目录写入 / 路径穿越
- [ ] 修改 Seal 全局设置（污染用户默认偏好）
- [ ] 无用户感知的无限后台批量静默下载
- [ ] 远程 HTTP / 局域网控制本机 Seal
- [ ] 把媒体直链 URL 当稳定 API 返回给第三方
- [ ] 将下载引擎以 AAR/SDK 形式嵌入第三方进程

### 1.3 开放尺度（产品天花板）

| 级别 | 形态 | 定位 | 状态 |
|------|------|------|------|
| L1 | Share / Open URL | 把链接送进配置 UI | **已有** |
| L2 | 参数化 Intent | 有限 extras + 可选自动开始 | 待做 |
| L3 | Intent + 结果回传 | taskId / 状态 / 文件 URI | **公共版推荐上限** |
| L4 | 受控本地 IPC | 入队/取消/进度/历史查询 | 可选高级 |
| L5+ | 远程/任意命令 | 超出 Seal 形态 | **禁止** |

---

## 2. 现状（代码事实）

### 2.1 已有对外入口
- [x] `QuickDownloadActivity`（exported）
  - `ACTION_SEND` + `text/plain`
  - `ACTION_VIEW` + `http/https`（及 video/audio mime 声明）
- [x] `MainActivity`（exported）
  - 同上 SEND / VIEW
  - `onNewIntent` 处理二次分享
- [x] URL 解析：`matchUrlFromSharedText` / `findURLsFromString`（取分享文本中的 URL）
- [x] 内部队列：`DownloaderV2.enqueue/cancel/restart/remove`
- [x] 偏好模型：`DownloadUtil.DownloadPreferences`
- [x] 完成态：`Task.DownloadState.Completed(filePath)`
- [x] FileProvider：`${applicationId}.provider`（exported=false，仅 grant 外发）

### 2.2 包名
- release：`com.junkfood.seal`
- debug：`com.junkfood.seal.debug`
- preview：`com.junkfood.seal.preview`

### 2.3 当前缺口
- [ ] 无自定义 action / 稳定 extras 协议
- [ ] 无静默/半静默委托开关（用户可控）
- [ ] 无 taskId 回传 / 完成回调
- [ ] 无调用方 package 白名单 / 权限模型
- [ ] 无能力探测（版本、支持的 extras）
- [ ] 文档未对第三方集成面做正式说明

---

## 3. 设计原则（委托模型）

1. **执行权在 Seal**：第三方只提交意图，不拥有 yt-dlp 生命周期。
2. **最小权限**：默认只接受 URL；高级参数需显式 extras + 用户设置允许。
3. **用户可关闭**：设置页提供“允许外部应用委托下载”总开关；可选“允许自动开始”。
4. **结果用 URI，不用裸路径**：完成文件通过 FileProvider grant。
5. **协议版本化**：所有对外字段带 `protocol_version`，避免静默破坏。
6. **失败可解释**：统一 resultCode / errorCode，避免“启动了但没下文”。

---

## 4. 建议对外协议（L2 / L3 草案）

### 4.1 Action（待定，实现时冻结）
- `com.junkfood.seal.action.DOWNLOAD`（显式委托）
- 兼容保留：`ACTION_SEND` / `ACTION_VIEW`（L1）

### 4.2 请求 extras（L2）
- [ ] `protocol_version`：Int，从 1 开始
- [ ] `url`：String（优先）；或兼容 `EXTRA_TEXT` / `data`
- [ ] `urls`：StringArray（可选，多链接）
- [ ] `extract_audio`：Boolean
- [ ] `download_subtitle`：Boolean
- [ ] `auto_start`：Boolean（需用户设置允许，否则忽略并弹配置）
- [ ] `template_id`：Long?（仅允许已有模板 id，不允许原始命令）
- [ ] `caller_request_id`：String?（调用方关联 id，原样回传）
- [ ] `open_ui`：Boolean（默认 true；false 仅在授权自动开始时生效）

### 4.3 响应（L3）
- [ ] `task_id`：String
- [ ] `caller_request_id`：String?
- [ ] `status`：`accepted` / `rejected` / `needs_ui` / `completed` / `failed` / `canceled`
- [ ] `error_code` / `error_message`
- [ ] `content_uri`：完成文件（grant 读权限）
- [ ] `display_name` / `mime_type`（可选）

### 4.4 回传通道（择一或组合）
- [ ] `startActivityForResult` / Activity Result API（适合前台一锤子）
- [ ] 显式 broadcast：`com.junkfood.seal.action.DOWNLOAD_STATUS`（仅发给调用方 package）
- [ ] （L4）Bound Service / ContentProvider 查询进度

### 4.5 拒绝策略
- [ ] 总开关关闭 → reject
- [ ] URL 非法/空 → reject
- [ ] `auto_start=true` 但未授权 → 降级为 `needs_ui`（打开配置）
- [ ] 未知 package 且开启白名单模式 → reject
- [ ] 不支持的 protocol_version → reject（附 min/max 支持版本）

---

## 5. 安全与权限 TODO

- [ ] 设置项：`external_delegate_enabled`（默认 true 或 false，需产品拍板）
- [ ] 设置项：`external_auto_start_enabled`（默认 false）
- [ ] 设置项：可选调用方白名单（package list）
- [ ] 自定义 permission（L4 或严格模式）：`com.junkfood.seal.permission.DELEGATE_DOWNLOAD`
- [ ] 校验 `callingPackage` / `Activity.getCallingPackage()`
- [ ] Broadcast 回传必须 `setPackage(caller)`，禁止隐式广播打全域
- [ ] 文件仅 `FLAG_GRANT_READ_URI_PERMISSION`（必要时临时 persist 策略另议）
- [ ] 速率限制：同一 caller 的入队频率 / 并发上限
- [ ] 审计日志（debug）：caller、url hash、结果，避免明文刷敏感信息到生产日志

---

## 6. 实现分期清单

### Phase A — 文档与协议冻结
- [x] 本 TODO 文档落盘
- [ ] 在 README 或 `docs/` 增加第三方集成简版说明（链到本文件）
- [ ] 冻结 action 名、extras 名、error_code 表
- [ ] 确定默认开关策略（总开关 / 自动开始默认值）

### Phase B — L2 参数化委托（最小可用）
- [ ] 新增（或扩展）exported 入口处理 `DOWNLOAD` action
- [ ] 解析 extras → 映射到 `DownloadPreferences` 安全子集
- [ ] `auto_start=false`：打开现有下载配置 UI（QuickDownload 路径）
- [ ] `auto_start=true` 且授权：直接 `DownloaderV2.enqueue`
- [ ] 非法参数降级/拒绝，不崩溃
- [ ] 单元测试：URL 解析、参数映射、拒绝分支

### Phase C — L3 结果回传
- [ ] 接受任务后生成稳定 `task_id` 并返回
- [ ] 监听任务终态（Completed / Error / Canceled）
- [ ] 完成时 FileProvider 生成 content URI 并 grant 给 caller
- [ ] Activity Result 与/或定向 broadcast
- [ ] 调用方样例（Kotlin snippet）写入 docs

### Phase D — 用户设置与可发现性
- [ ] 设置页：外部委托开关、自动开始开关、白名单管理
- [ ] 能力探测：`meta-data` 或 query intent 返回支持版本
- [ ] 错误文案与通知（可选：外部任务来源标记）

### Phase E — L4 受控 IPC（可选，非公共默认）
- [ ] 定义 Binder / ContentProvider 查询契约（只读进度与历史）
- [ ] permission + 白名单双重门禁
- [ ] cancel/restart by taskId
- [ ] 明确不提供 raw command / cookie / settings 写入
- [ ] 压力测试：多 caller 并发、进程被杀恢复（与现有 task backup 对齐）

---

## 7. 与现有模块的挂载点

| 模块 | 用途 |
|------|------|
| `QuickDownloadActivity` | 有 UI 的委托入口（推荐承载 L2 needs_ui） |
| `MainActivity` | 兼容 SEND/VIEW；二次 intent |
| `DownloaderV2` | 唯一入队/取消/状态源 |
| `DownloadUtil.DownloadPreferences` | 参数映射目标（仅安全子集） |
| `Task` / `Task.State` | taskId 与进度状态 |
| `FileUtil` + FileProvider | 结果 URI |
| `PreferenceUtil` | 外部委托开关持久化 |
| `NotificationActionReceiver` | 保持内部；不直接 exported 给第三方 |

---

## 8. error_code 初稿

| code | 含义 |
|------|------|
| `ok` | 已接受 |
| `disabled` | 用户关闭外部委托 |
| `auto_start_denied` | 未允许自动开始（可降级 needs_ui） |
| `invalid_url` | URL 缺失或非法 |
| `unsupported_version` | protocol_version 不支持 |
| `caller_denied` | 调用方不在白名单 / 无权限 |
| `queue_rejected` | 限流或队列拒绝 |
| `internal_error` | Seal 内部错误 |
| `download_failed` | 任务执行失败（L3 终态） |
| `canceled` | 用户或系统取消 |

---

## 9. 验收标准（实现后）

- [ ] 第三方仅用 Intent 即可把 URL 委托给 Seal，无需嵌入引擎
- [ ] 未授权时不能静默自动下载
- [ ] 授权自动开始时，任务进入 Seal 队列并显示通知/任务列表
- [ ] 完成后 caller 能拿到可读 `content://` URI（L3）
- [ ] 关闭设置总开关后，所有外部委托被拒绝
- [ ] 不存在 raw command / cookie / 全局设置写入通道
- [ ] debug/preview/release 包名差异在文档中写清

---

## 10. 决策待拍板

- [ ] 公共版默认做到 L2 还是 L3？
- [ ] `external_delegate_enabled` 默认开还是关？
- [ ] 是否默认启用 package 白名单，还是默认允许所有本机 App？
- [ ] 多 URL 是否进入 MVP，还是先单 URL？
- [ ] 结果回传优先 Activity Result 还是定向 Broadcast？

---

## 11. 参考代码位置

- `app/src/main/AndroidManifest.xml` — exported 组件与 intent-filter
- `app/src/main/java/com/junkfood/seal/QuickDownloadActivity.kt`
- `app/src/main/java/com/junkfood/seal/MainActivity.kt`
- `app/src/main/java/com/junkfood/seal/download/DownloaderV2.kt`
- `app/src/main/java/com/junkfood/seal/download/Task.kt`
- `app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt`
- `app/src/main/java/com/junkfood/seal/util/TextUtil.kt`
- `app/src/main/res/xml/provider_paths.xml`

---

## 12. 下一步（本任务）

- [x] 创建本地 TODO 文档
- [ ] 用户确认第 10 节决策后，可开实现任务（建议先做 Phase B）
