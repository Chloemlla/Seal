# Seal 第三方集成 TODO（仅委托，不内嵌）

> 状态：**L1–L3 已落地** / L4 未做  
> 日期：2026-07-12  
> 原则：**第三方只能“委托 Seal”完成下载**；不提供 SDK 内核、不暴露 yt-dlp 进程、不做远程下载 API。

正式集成说明：[`third-party-delegate-integration.md`](./third-party-delegate-integration.md)

---

## 1. 目标与边界

### 1.1 目标
- [x] 让其它本机 App 通过标准 Android 机制把下载任务交给 Seal
- [x] Seal 始终是下载执行者（队列、yt-dlp、通知、文件落地、历史）
- [x] 对外协议稳定、可版本化、可鉴权、可关闭

### 1.2 非目标（明确不做）
- [x] 任意 yt-dlp 命令字符串对外注入 — **保持禁止**
- [x] Cookie / 账号凭据导出或跨 App 读取 — **保持禁止**
- [x] 任意输出目录写入 / 路径穿越 — **保持禁止**
- [x] 修改 Seal 全局设置（污染用户默认偏好） — **保持禁止**
- [x] 无用户感知的无限后台批量静默下载 — **自动开始默认关 + 限流**
- [x] 远程 HTTP / 局域网控制本机 Seal — **保持禁止**
- [x] 把媒体直链 URL 当稳定 API 返回给第三方 — **保持禁止**
- [x] 将下载引擎以 AAR/SDK 形式嵌入第三方进程 — **保持禁止**

### 1.3 开放尺度

| 级别 | 形态 | 状态 |
|------|------|------|
| L1 | Share / Open URL | **已有** |
| L2 | 参数化 Intent | **已落地** |
| L3 | Intent + 结果回传 | **已落地** |
| L4 | 受控本地 IPC | 未做（可选） |
| L5+ | 远程/任意命令 | **禁止** |

---

## 2. 实现落点

### 2.1 协议与核心
- [x] `com.chloemlla.seal.integration.ExternalDownloadProtocol`
- [x] `ExternalDownloadRequestParser`
- [x] `ExternalDownloadGate`（开关 / 白名单 / 自动开始 / 限流入口）
- [x] `ExternalDownloadEntry`（Activity 共用入口）
- [x] `ExternalDownloadCoordinator` + `ExternalDownloadStatusReporter`

### 2.2 组件接线
- [x] `QuickDownloadActivity` — DOWNLOAD / SEND / VIEW
- [x] `MainActivity` — cold start + `onNewIntent`
- [x] `AndroidManifest.xml` — action + meta-data
- [x] Preference keys + Interaction settings UI
- [x] strings（en）

### 2.3 文档与测试
- [x] `docs/third-party-delegate-integration.md`
- [x] README 链接
- [x] 单元测试：gate / URL / protocol constants
- [x] 本 TODO 状态更新

---

## 3. 冻结协议摘要

- Action: `com.chloemlla.seal.action.DOWNLOAD`
- Status broadcast: `com.chloemlla.seal.action.DOWNLOAD_STATUS`（`setPackage(caller)`）
- `protocol_version = 1`
- Defaults: delegate ON, auto-start OFF, whitelist OFF

---

## 4. 决策（已拍板）

- [x] 公共版默认做到 **L3**
- [x] `external_delegate_enabled` 默认 **开**
- [x] 白名单默认 **关**（允许所有本机 App；可选手动开启）
- [x] 多 URL：支持 `urls`；UI 整表展示；auto-start 逐条入队
- [x] 回传：**Activity Result** + **定向 Broadcast**

---

## 5. 仍未做 / 后续可选

### Phase E — L4（可选）
- [ ] Binder / ContentProvider 查询进度与历史
- [ ] 自定义 permission 双重门禁
- [ ] cancel/restart by taskId 对外
- [ ] 多 caller 压力与进程恢复专项测试

### 体验增强
- [ ] 设置页白名单的包名选择器（当前为文本框）
- [ ] 更多语言翻译（目前 en strings）
- [ ] 外部任务在任务列表中的来源标记
- [ ] CI 中跑 `ExternalDownloadIntegrationTest`

---

## 6. 验收对照

- [x] 第三方仅用 Intent 即可委托下载
- [x] 未授权不能静默自动下载
- [x] 授权自动开始时进入 Seal 队列
- [x] 完成后可对 caller grant `content://`（broadcast）
- [x] 关闭总开关后拒绝外部委托
- [x] 无 raw command / cookie / 设置写入通道
- [x] 包名差异写在集成文档

---

## 7. 参考代码

- `app/src/main/java/com/junkfood/seal/integration/*`
- `QuickDownloadActivity.kt` / `MainActivity.kt`
- `InteractionPreferencePage.kt`
- `PreferenceUtil.kt`（`EXTERNAL_*` keys）
- `AndroidManifest.xml`
- `app/src/test/java/com/junkfood/seal/integration/ExternalDownloadIntegrationTest.kt`
