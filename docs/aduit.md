 # Fuck My Shit Mountain Audit Report

  Project: Seal
  Audit mode: full
  Date: 2026-07-12
  Reviewer: Codex (static audit, no local Flutter/Gradle run)
  Language / output: 中文 / stdout（按你要求不写报告文件）

  ———

  ## 1. Executive Summary

  Seal 是 Android 视频/音频下载客户端（Kotlin + Jetpack Compose + Room + MMKV + yt-dlp-android）。主流程清晰：App 初始化原生引擎与 DI
  → DownloadPageV2 / DownloaderV2 排队下载 → DownloadUtil 组装 yt-dlp 参数 → Room 记历史、通知栏控制任务。UI 与 Material3 完成度较
  高，Room 有 schema 与 autoMigration，Release 开 minify/shrink。

  但核心风险集中在：自定义命令等于任意 yt-dlp 配置注入、备份导入模板逻辑写错导致导入失效、GitHub Token 硬编码、测试几乎为装饰、双下载
  器与巨型 UI 文件并存、备份/Cookie 敏感数据明文与 allowBackup=true。整体更像功能成熟、工程债明显的客户端，而不是“可放心长期扩展的干
  净架构”。

  ### Score Dashboard

  Security        ████░░░░░░  4.5  C   硬编码 token、宽 FileProvider、自定义命令、备份含敏感面
  Stability       █████░░░░░  5.5  B   忙等队列、双下载器、导入 bug、SponsorBlock 软失败
  Performance     ███████░░░  7.0  A   本机下载场景可接受；旧队列 3s 轮询、无系统级热点证明
  Testing         ██░░░░░░░░  2.0  F   仅示例测试；instrumented 包名错误；CI 不跑有效测试
  Maintainability ████░░░░░░  4.5  C   多文件 >500/1000 行；Downloader+DownloadUtil 上帝模块
  Design          ████░░░░░░  4.0  C   SRP/fail-fast/DRY 多处违反；V1/V2 并存
  Release         ██████░░░░  6.0  B   有 flavor/CI；release 用 nightly 签名 action；无完整性校验
  ─────────────────────────────────────
  Overall         █████░░░░░  4.8  C

  （分数 0–10，越高越好。）

  ### Finding Statistics

   Severity    Count    Confirmed    Suspected
  ━━━━━━━━━━  ━━━━━━━  ━━━━━━━━━━━  ━━━━━━━━━━━
   Critical    1        1            0
  ──────────  ───────  ───────────  ───────────
   High        6        6            0
  ──────────  ───────  ───────────  ───────────
   Medium      10       9            1
  ──────────  ───────  ───────────  ───────────
   Low         6        5            1
  ──────────  ───────  ───────────  ───────────
   Info        2        2            0
  ──────────  ───────  ───────────  ───────────
   Total       25       23           2

  ———

  ## 2. Project Map

  - 入口： App → Koin（DownloaderV2、若干 ViewModel）→ MMKV / YoutubeDL / FFmpeg / Aria2c；未捕获异常进 CrashReportActivity
  - UI： Compose；主页 DownloadPageV2；历史 VideoListPage；设置树；自定义命令 TaskListPage；Cookie WebView
  - 下载： DownloaderV2（队列，MAX_CONCURRENCY=3）+ 遗留 Downloader object（忙等队列、自定义命令）
  - 核心工具： DownloadUtil（~1k 行，请求组装/cookie/代理/归档/自定义命令）
  - 持久化： Room AppDatabase v5（历史、模板、CookieProfile、快捷选项）+ MMKV 偏好与任务备份
  - 外部： 网络下载、GitHub API 更新/赞助者、WebView 登录取 Cookie、FileProvider 分享/安装 APK、前台服务 + 通知
  - 风险热点： DownloadUtil / Downloader / DownloaderV2 / Cookie+WebView / SponsorUtil / UpdateUtil / DatabaseUtil.importBackup /
    Manifest 权限与备份

  ### Coverage Matrix（摘要）

   Dimension                                  Coverage        主要证据                                       限制
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━
   Architecture / Maintainability / Design    High            包结构、最大文件、V1/V2 路径                   未跑时序剖析
  ─────────────────────────────────────────  ──────────────  ─────────────────────────────────────────────  ────────────────────────
   Security / Privacy / Supply-chain          High            Manifest、token、provider、自定义命令、备份    未动态抓包/渗透
  ─────────────────────────────────────────  ──────────────  ─────────────────────────────────────────────  ────────────────────────
   Stability / Fallback / Data-integrity      High            队列、导入、错误处理                           未真机压力
  ─────────────────────────────────────────  ──────────────  ─────────────────────────────────────────────  ────────────────────────
   Testing / Authenticity                     High            测试目录 + CI                                  按 AGENTS 本地不跑构建
  ─────────────────────────────────────────  ──────────────  ─────────────────────────────────────────────  ────────────────────────
   Performance / Cost                         Medium          并发常量、忙等、大文件                         无 benchmark
  ─────────────────────────────────────────  ──────────────  ─────────────────────────────────────────────  ────────────────────────
   Frontend-state                             Medium          大 Composable、双下载页                        未 UI 自动化
  ─────────────────────────────────────────  ──────────────  ─────────────────────────────────────────────  ────────────────────────
   Backend-API / AI-Safety                    Not assessed    无服务端/LLM 产品面                            —
  ─────────────────────────────────────────  ──────────────  ─────────────────────────────────────────────  ────────────────────────
   Accessibility                              Low             Compose 语义抽样                               无 a11y 扫描

  ———

  ## 3. Top Risks（问题优先）

  1. [Critical] 自定义命令模板经 --config-locations 执行 → 任意 yt-dlp 选项/写盘/外联风险
  2. [High] SponsorUtil Base64 拆分硬编码 GitHub token
  3. [High] DatabaseUtil.importBackup 导入模板时用本地列表过滤自身 → 模板导入恒空
  4. [High] allowBackup=true + Cookie/任务/偏好明文 → 备份面过大
  5. [High] FileProvider external-path path="." 过宽
  6. [High] APK 更新下载无哈希/签名校验
  7. [High] 有效测试几乎为零 + instrumented 包名错误
  8. [Medium] 双下载器（Downloader / DownloaderV2）行为不一致
  9. [Medium] 旧队列 while + delay(3000) 忙等
  10. [Medium] WebView 全开 JS + 第三方 Cookie，读系统 Cookie SQLite
  11. [Medium] MANAGE_EXTERNAL_STORAGE + legacy 存储
  12. [Medium] 多个 500–1100 行 UI/工具类，SRP 崩坏

  ———

  ## 4. Detailed Findings（核心问题）

  ### Finding: 自定义命令等于任意 yt-dlp 配置执行

  - Severity: Critical
  - Confidence: High
  - Category: Security
  - Status: Confirmed
  - Affected area: DownloadUtil.executeCustomCommandTask / executeCommandInBackground
  - Evidence:
      - File: app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt ~876–984
      - Behavior: 把 CommandTemplate.template 写入 config 文件后 --config-locations 交给 YoutubeDL.execute

  - Problem: 模板是用户可控长文本，等价于注入任意 yt-dlp 选项（输出路径、外部命令相关选项、网络等，取决于后端能力）。
  - Why it matters: 恶意/被污染模板可写任意可写路径、异常外联或破坏下载环境；导入备份可放大。
  - Realistic failure scenario: 用户导入第三方模板 → 后台执行 → 文件落到意外目录或敏感操作。
  - Minimal fix: 白名单选项；禁止危险 flag；导入模板二次确认与 diff。
  - Better long-term fix: 结构化命令构建器，而不是自由文本 config。
  - Regression test: 危险选项应被拒绝；合法模板仍可跑。
  - Estimated effort: 2–4 天

  ———

  ### Finding: GitHub Token 硬编码在客户端

  - Severity: High
  - Confidence: High
  - Category: Security
  - Status: Confirmed
  - Affected area: SponsorUtil
  - Evidence:
      - File: app/src/main/java/com/junkfood/seal/util/SponsorUtil.kt:14-34
      - MAGIC_STRING_* Base64 拼接后 Authorization: bearer ... 调 GitHub GraphQL

  - Problem: 所谓混淆可秒解；token 会进 APK/反编译/流量。
  - Why it matters: 配额滥用、权限范围内的仓库/账号风险、密钥轮换困难。
  - Realistic failure scenario: 抓包或反编译 → 刷 GraphQL → token 失效/限流。
  - Minimal fix: 立刻轮换并吊销；改为后端代理或公开接口/静态名单。
  - Better long-term fix: 服务端聚合赞助者数据。
  - Regression test: 客户端二进制扫描不应出现 ghp_ 类串。
  - Estimated effort: 0.5–1 天（含轮换）

  ———

  ### Finding: 命令模板备份导入逻辑写错（导入无效）

  - Severity: High
  - Confidence: High
  - Category: Stability / Data Integrity
  - Status: Confirmed
  - Affected area: DatabaseUtil.importBackup
  - Evidence:

  // DatabaseUtil.kt:93-101
  if (types.contains(BackupType.CommandTemplate)) {
      if (templates != null) {
          val templateList = getTemplateList()
          dao.importTemplates(
              templateList                    // ← 应为 backup.templates
                  .filterNot { templateList.contains(it) }
                  .map { it.copy(id = 0) }
          )
      }
  }

  - Problem: 用本地 templateList 对自身 filterNot contains，结果恒为空；备份里的 templates 从未插入。快捷方式分支写法正确，更显这是复
    制粘贴缺陷。

  - Why it matters: 用户以为模板已恢复，实际丢失；与数据完整性直接冲突。
  - Realistic failure scenario: 换机导入 JSON → 计数可能误导/为 0 → 模板全丢。
  - Minimal fix: templates.filterNot { templateList.contains(it) }.map { it.copy(id=0) }
  - Better long-term fix: 导入结果报告 + 单测覆盖三种 BackupType。
  - Regression test: 给定 backup 含 2 模板，导入后 DB 增加 2。
  - Estimated effort: 1–2 小时

  ———

  ### Finding: allowBackup=true，敏感状态可进系统备份

  - Severity: High
  - Confidence: High
  - Category: Privacy / Security
  - Status: Confirmed
  - Affected area: Manifest + MMKV/Room/Cookie
  - Evidence:
      - AndroidManifest.xml: android:allowBackup="true"
      - Cookie：WebView DB + 文件；CookieProfile.content；任务队列 MMKV 序列化

  - Problem: 未见精细 fullBackupContent 排除规则；认证材料与任务状态可能进备份。
  - Why it matters: 设备备份/迁移可能带走登录 Cookie 与历史。
  - Realistic failure scenario: 备份恢复到他人设备 → 会话复用。
  - Minimal fix: allowBackup="false" 或排除 cookies/MMKV/DB。
  - Better long-term fix: 敏感字段加密 + 导出单独授权。
  - Regression test: backup 规则清单断言。
  - Estimated effort: 2–4 小时

  ———

  ### Finding: FileProvider 暴露整个外部存储树

  - Severity: High
  - Confidence: High
  - Category: Security
  - Status: Confirmed
  - Affected area: provider_paths.xml + FileUtil/UpdateUtil
  - Evidence:

  <external-path name="external_files" path="." />
  <cache-path name="cache" path="." />

  - Problem: 任意授予 URI 权限时，可指向外部存储广泛路径，而非仅下载目录/APK 目录。
  - Why it matters: 路径遍历/错误拼接时分享面过大。
  - Minimal fix: 收窄到应用下载目录与 apk 子目录。
  - Better long-term fix: 分 provider 路径 + 强制 canonical path 校验。
  - Regression test: 越界路径 getUriForFile 失败。
  - Estimated effort: 2–4 小时

  ———

  ### Finding: 应用内 APK 更新无完整性校验

  - Severity: High
  - Confidence: High
  - Category: Security / Supply Chain
  - Status: Confirmed
  - Affected area: UpdateUtil.downloadApk / installLatestApk
  - Evidence: 按 asset 名匹配 ABI 后直接落盘安装；无 SHA/签名校验（UpdateUtil.kt ~142–219, 107–125）
  - Problem: 依赖 HTTPS + GitHub 信任链，中间错误 asset/供应链问题时客户端无二次校验。
  - Why it matters: 错误或被替换的 APK 仍可能走到安装界面。
  - Minimal fix: 发布元数据附哈希，下载后校验再安装。
  - Better long-term fix: 签名校验 + 仅官方 channel。
  - Regression test: 篡改字节流应失败。
  - Estimated effort: 1 天

  ———

  ### Finding: 测试几乎不提供真实信心

  - Severity: High
  - Confidence: High
  - Category: Testing
  - Status: Confirmed
  - Affected area: app/src/test, androidTest, CI
  - Evidence:
      - 单元：2+2 + 一个 delimiter 小测
      - Instrumented：assertEquals("com.junkfood.Seal", ...) 但 applicationId 为 com.junkfood.seal → 必失败
      - CI：buildGenericRelease / 手动 release，无有效 test stage

  - Problem: 下载、导入、队列、cookie、格式排序等核心路径零覆盖。
  - Why it matters: 像模板导入 bug 这类缺陷可长期存活。
  - Minimal fix: 修包名；为 importBackup/TextUtil/format sorter 加单测。
  - Better long-term fix: 关键关键路径 JVM 单测 + 少量 instrumentation。
  - Regression test: CI 必须跑 test 且失败阻断。
  - Estimated effort: 2–3 天起

  ———

  ### Finding: 双下载器架构并存

  - Severity: Medium
  - Confidence: High
  - Category: Maintainability / Architecture
  - Status: Confirmed
  - Affected area: Downloader vs download/DownloaderV2
  - Evidence:
      - 主 UI：AppEntry → DownloadPageV2 + Koin DownloaderV2
      - 自定义命令/部分通知：仍走 Downloader + YoutubeDL.destroyProcessById
      - DownloadPage.kt 仍在仓库但主路由未用 → 死代码/双实现

  - Problem: 取消、队列、前台服务生命周期两套语义。
  - Why it matters: 修 bug 要改两处；状态不一致难复现。
  - Minimal fix: 文档标明边界；通知取消统一入口。
  - Better long-term fix: 删 V1 或完全下线自定义命令旧路径。
  - Regression test: V2 任务与自定义命令取消契约测试。
  - Estimated effort: 3–7 天（收敛）

  ———

  ### Finding: 旧下载队列用 delay 忙等

  - Severity: Medium
  - Confidence: High
  - Category: Stability / Performance
  - Status: Resolved (2026-07-13)
  - Affected area: legacy `Downloader.addToDownloadQueue` (removed)
  - Evidence: V1 singleton and its delay-based queue were deleted; all live paths use `DownloaderV2`.
  - Problem: 非真正队列；无取消/优先级；进程被杀任务丢；最多 3s 调度延迟。
  - Why it matters: 多任务分享场景丢任务或乱序。
  - Minimal fix: 已由 `DownloaderV2` 显式任务队列与状态机实现。
  - Better long-term fix: 已完成全部 live path 迁移并删除 V1。
  - Regression test: 连续入队 5 个任务按序完成。
  - Estimated effort: 1–2 天

  ———

  ### Finding: Cookie 采集链安全边界弱

  - Severity: Medium
  - Confidence: High
  - Category: Security / Privacy
  - Status: Confirmed
  - Affected area: WebViewPage, DownloadUtil.getCookieListFromDatabase
  - Evidence:
      - WebView：javaScriptEnabled=true、第三方 Cookie 接受
      - 直接 SQLiteDatabase.openDatabase(.../app_webview/Default/Cookies)
      - hostKey[0] 无空串保护（~405）

  - Problem: 登录态 Cookie 落文件给 yt-dlp；WebView 面大；异常 cookie 行可能崩溃。
  - Why it matters: Cookie 等价账户凭证。
  - Minimal fix: 空 host 跳过；导出文件权限最小化；提示风险。
  - Better long-term fix: 限定域、加密存储、用完清理。
  - Regression test: 空 host 行不崩溃。
  - Estimated effort: 0.5–1 天

  ———

  ### Finding: 过宽存储权限

  - Severity: Medium
  - Confidence: High
  - Category: Privacy / Security
  - Status: Confirmed
  - Affected area: Manifest
  - Evidence: MANAGE_EXTERNAL_STORAGE、requestLegacyExternalStorage、WRITE_EXTERNAL_STORAGE maxSdk 29
  - Problem: 对下载器常见但权限面积极大，商店/用户审查不友好。
  - Minimal fix: 优先 SAF / 应用目录；MANAGE 仅高级选项。
  - Estimated effort: 2–5 天

  ———

  ### Finding: SponsorBlock 失败被吞并当成功收尾

  - Severity: Medium
  - Confidence: High
  - Category: Fallback / Stability
  - Status: Confirmed
  - Affected area: DownloadUtil.downloadVideo ~803–816
  - Evidence: 消息含 Unable to communicate with SponsorBlock API 时 printStackTrace 后仍 onFinishDownloading
  - Problem: 可能未按预期去广告片段却显示成功。
  - Minimal fix: 明确警告状态，而不是静默当成功。
  - Estimated effort: 2–4 小时

  ———

  ### Finding: 全局 App.context 与单例可变状态

  - Severity: Medium
  - Confidence: High
  - Category: Design / Maintainability
  - Status: Confirmed
  - Affected area: App companion：context、clipboard、目录字符串、Downloader 全局 StateFlow
  - Problem: 静态 Context + 全局可变状态，测试困难、泄漏/错误生命周期风险。
  - Minimal fix: 新代码经 DI 注入 Context/仓库。
  - Estimated effort: 渐进，数天

  ———

  ### Finding: 巨型文件 / SRP 破坏

  - Severity: Medium
  - Confidence: High
  - Category: Maintainability / Design
  - Status: Confirmed
  - Evidence（行数约）:
      - DownloadDialogV2.kt ~1126
      - FormatPage.kt ~1019
      - DownloadUtil.kt ~937–1004
      - FormatSettingDialogs.kt ~950
      - 另有十余个 ≥500 行

  - Problem: UI+业务+IO 挤在同一文件，评审与测试成本高。
  - Minimal fix: 先拆 DownloadUtil（request builder / cookie / history / custom command）。
  - Estimated effort: 1–2 周分批

  ———

  ### Finding: Release CI 使用 @nightly 签名 Action

  - Severity: Medium
  - Confidence: Medium
  - Category: Supply Chain / Release
  - Status: Suspected（配置已确认，未验证 action 供应链）
  - Evidence: .github/workflows/android.yml → ilharp/sign-android-release@nightly
  - Problem: 浮动 nightly 引用供应链不可复现。
  - Minimal fix: 钉死 commit SHA。
  - Estimated effort: 30 分钟

  ———

  ### Finding: ProGuard -dontobfuscate

  - Severity: Low
  - Confidence: High
  - Category: Security
  - Status: Confirmed
  - Evidence: app/proguard-rules.pro:25
  - Problem: 有 shrink 无混淆，逆向成本低（对已有硬编码 token 更糟）。
  - Minimal fix: 允许混淆并 keep 原生/序列化必要面。
  - Estimated effort: 0.5–1 天

  ———

  ### Finding: NotificationActionReceiver 未显式 exported

  - Severity: Low
  - Confidence: Medium
  - Category: Security
  - Status: Suspected
  - Evidence: Manifest 仅 <receiver android:name=".NotificationActionReceiver" />
  - Problem: 依赖默认导出规则；显式 exported=false 更安全（若仅应用内 PendingIntent）。
  - Minimal fix: android:exported="false"。
  - Estimated effort: 15 分钟

  ———

  ### Finding: DownloaderV2 对缺失 task 使用 !!

  - Severity: Low
  - Confidence: High
  - Category: Stability / Type Safety
  - Status: Confirmed
  - Evidence: taskStateMap[this]!!（~188–189）
  - Problem: 并发 remove/cancel 下可能 NPE。
  - Minimal fix: 可空 + 早退。
  - Estimated effort: 1 小时

  ———

  ### Finding: 错误处理大量 printStackTrace / 弱可观测性

  - Severity: Low
  - Confidence: High
  - Category: Observability / Fallback
  - Status: Confirmed
  - Evidence: 约 19 处 printStackTrace；用户可见性依赖 Toast/通知
  - Problem: 现场排障缺结构化日志与错误码。
  - Minimal fix: 统一 logger + 用户可读错误分类。
  - Estimated effort: 1–2 天

  ———

  ### Finding: CookieProfile.content 与真实 Cookie 源可能双轨

  - Severity: Low
  - Confidence: Medium
  - Category: Data Integrity
  - Status: Confirmed（结构）
  - Evidence: Room 存 content；实际 yt-dlp 多用 WebView Cookie 文件导出
  - Problem: 用户以为资料已存 DB，运行时却读另一来源，排障混乱。
  - Minimal fix: 单一数据源文档化/代码化。
  - Estimated effort: 0.5–1 天

  ———

  ### Finding: okhttp 5.0.0-alpha.10

  - Severity: Info
  - Confidence: High
  - Category: Dependency Weight / Release
  - Status: Confirmed
  - Evidence: gradle/libs.versions.toml
  - Problem: alpha 网络栈进生产通道。
  - Minimal fix: 迁稳定版。
  - Estimated effort: 数小时

  ———

  ### Finding: 无服务端 API / 无应用内 LLM 产品面

  - Severity: Info
  - Category: Backend-API / AI-Safety
  - Status: Confirmed
  - Problem: 这两维不适用；不扣分，不代表客户端无风险。

  ———

  ## 5–29. 分维结论（极简）

   维                      结论
  ━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   Architecture            UI/设置清晰；下载核心双轨 + 上帝 object
  ──────────────────────  ─────────────────────────────────────────────────────
   Security                最严重：自定义命令、token、provider、备份、更新校验
  ──────────────────────  ─────────────────────────────────────────────────────
   Stability               导入 bug + 忙等 + 软失败
  ──────────────────────  ─────────────────────────────────────────────────────
   Performance             本机够用；旧队列与大重组有隐患
  ──────────────────────  ─────────────────────────────────────────────────────
   Testing                 接近 F
  ──────────────────────  ─────────────────────────────────────────────────────
   Maintainability         大文件 + 双实现
  ──────────────────────  ─────────────────────────────────────────────────────
   Design                  SRP/fail-fast/CQS 多处违规
  ──────────────────────  ─────────────────────────────────────────────────────
   Release                 有 CI/flavor；供应链钉扎不足
  ──────────────────────  ─────────────────────────────────────────────────────
   Documentation           README/多语言好；架构/威胁模型缺
  ──────────────────────  ─────────────────────────────────────────────────────
   Configuration           MMKV 键值无 schema/校验
  ──────────────────────  ─────────────────────────────────────────────────────
   Observability           logcat 级
  ──────────────────────  ─────────────────────────────────────────────────────
   Data Integrity          Room 迁移有；导入逻辑有致命 bug
  ──────────────────────  ─────────────────────────────────────────────────────
   Privacy                 Cookie/备份/存储权限
  ──────────────────────  ─────────────────────────────────────────────────────
   Accessibility           未系统评估
  ──────────────────────  ─────────────────────────────────────────────────────
   Supply Chain            yt-dlp/ffmpeg/aria 二进制 + 更新链路
  ──────────────────────  ─────────────────────────────────────────────────────
   Cost                    本机为主；赞助者 API/更新流量次要
  ──────────────────────  ─────────────────────────────────────────────────────
   AI Safety               N/A
  ──────────────────────  ─────────────────────────────────────────────────────
   Fallback                SponsorBlock 等静默成功
  ──────────────────────  ─────────────────────────────────────────────────────
   Testing Authenticity    示例测试制造假信心
  ──────────────────────  ─────────────────────────────────────────────────────
   Type Safety             Kotlin 较好；!!/静态 Context
  ──────────────────────  ─────────────────────────────────────────────────────
   Frontend State          超大 Composable，业务耦合
  ──────────────────────  ─────────────────────────────────────────────────────
   Backend API             N/A
  ──────────────────────  ─────────────────────────────────────────────────────
   Dependency Weight       Compose/yt-dlp 合理；okhttp alpha 扎眼
  ──────────────────────  ─────────────────────────────────────────────────────
   Code Consistency        整体 Kotlin 风格尚可；V1/V2 API 不一致
  ──────────────────────  ─────────────────────────────────────────────────────
   Comment Coverage        少量模块注释；公共工具边界说明不足

  ———

  ## Principles（原则违反摘要）

   原则                   证据
  ━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   1.1 SRP                DownloadUtil / 超大 Dialog
  ─────────────────────  ──────────────────────────────────────────
   1.2 文件体积           多个 >500，3 个 >1000
  ─────────────────────  ──────────────────────────────────────────
   4.4 Fail-fast          SponsorBlock 软成功；导入静默无效
  ─────────────────────  ──────────────────────────────────────────
   4.1 DRY                双下载器、重复下载页
  ─────────────────────  ──────────────────────────────────────────
   5.1 共享可变状态       App/Downloader 全局
  ─────────────────────  ──────────────────────────────────────────
   4.6 Least privilege    MANAGE_EXTERNAL_STORAGE、宽 FileProvider

  ———

  ## Recommended Fix Order

  ### 立刻修

  1. 吊销并移除硬编码 GitHub token
  2. 修 importBackup 模板导入
  3. 收紧 FileProvider
  4. 自定义命令危险选项拦截或强警告

  ### 稳定版前

  5. 备份/allowBackup 与 Cookie 治理
  6. APK 校验
  7. 真实单测 + 修 instrumented 包名 + CI 跑测试
  8. 队列迁 V2，标废弃 V1
  9. CI 签名 action 钉 SHA

  ### 稍后

  10. 拆大文件
  11. 存储权限模型现代化
  12. 混淆策略
  13. 可观测性统一

  ### 可暂缓

  14. 纯 UI 风格/次要 a11y（无证据前）
  15. 后端/LLM 维（N/A）

  ———

  ## Quick Wins

   项                                     收益                    估时
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━
   修模板导入 filter 对象                 修复数据丢失级 bug      ~1h
  ─────────────────────────────────────  ──────────────────────  ────────
   吊销 token + 禁用客户端 GraphQL        去 Critical 密钥面      ~2h
  ─────────────────────────────────────  ──────────────────────  ────────
   provider_paths 收窄                    降分享攻击面            ~1h
  ─────────────────────────────────────  ──────────────────────  ────────
   instrumented 包名 com.junkfood.seal    让唯一仪器测试有意义    ~10min
  ─────────────────────────────────────  ──────────────────────  ────────
   receiver exported=false                降导出面                ~15min
  ─────────────────────────────────────  ──────────────────────  ────────
   导入/备份单测                          锁住回归                ~2h

  ———

  ## Long-term Refactor

  1. 单一下载引擎： 只保留 DownloaderV2，自定义命令也进同一状态机。
  2. DownloadUtil 拆分： RequestBuilder / CookieRepository / HistoryWriter / CommandRunner。
  3. 安全模型： 模板白名单 + 备份加密 + 更新校验。
  4. 测试金字塔： 纯 Kotlin 域逻辑单测优先，UI 少而精。

  ———

  ## 说明

  - 按你的要求：未写入 audit-report-*.md，仅在对话输出。
  - 未本地执行 Gradle/Flutter（遵循仓库 AGENTS）。
  - 未在报告中写出解码后的完整 token；请立刻当作已泄露并轮换。
