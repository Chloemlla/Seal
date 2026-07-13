# Downloader (V1) → DownloaderV2 Migration Plan

> Status: implemented on `main`
> Related task: `.trellis/tasks/07-12-fix-kotlin-deprecation-warnings-from-pre-release-ci/`
> Last updated: 2026-07-13

## Goal

Retire the deprecated singleton `com.chloemlla.seal.Downloader` so that:

1. Pre-release CI no longer emits `Prefer DownloaderV2 for new code` warnings.
2. All live download / custom-command / notification / yt-dlp-update paths share one task model (`download.Task` + `DownloaderV2`).
3. No behavior regression for main UI downloads, custom commands, notifications, or third-party integration.

## Non-goals

- Rewriting the yt-dlp execution engine (`DownloadUtil` core).
- Changing download preference / directory / format semantics.
- Running Gradle/Flutter locally (CI only per repo policy).
- A single big-bang PR that mixes UI deletion, custom-command migration, and ID renames without intermediate green builds.

---

## Current architecture (implemented)

### Already on V2

| Surface | Evidence |
|---------|----------|
| App home UI | `AppEntry` routes `Route.HOME` → `DownloadPageV2` |
| DI | `App` Koin: `single<DownloaderV2> { DownloaderV2Impl(...) }` |
| External integration | `ExternalDownloadEntry` / `ExternalDownloadCoordinator` take `DownloaderV2` |
| Quick download | `QuickDownloadActivity` injects `DownloaderV2` |
| Main activity wiring | `MainActivity` injects `DownloaderV2` into UI graph |

### Migration result

| Area | Result |
|------|--------|
| V1 singleton | `Downloader.kt` deleted; no product imports remain |
| Dead home UI | V1 `DownloadPage`, `HomePageViewModel`, and exclusive dialogs removed |
| yt-dlp update | `YtDlpUpdateGate` plus V2 active-task checks |
| Custom commands | `Task.TypeInfo.CustomCommand` in the V2 queue |
| Command logs | Bounded `Task.State.outputLog`, serialized in pending-task backup |
| Command UI | List/log/cancel/restart read and mutate the V2 state map |
| Notifications | Cancel action targets `DownloaderV2.cancel(taskId)` |
| Execution utility | `DownloadUtil` exposes the pure custom-command executor only |

### V2 surface (target)

- Interface: `download/DownloaderV2.kt` — `getTaskStateMap`, `enqueue`, `cancel`, `restart`, `remove`, `flushPendingBackup`
- Model: `download/Task.kt` — `TypeInfo.URL | Playlist | CustomCommand`, `DownloadState.*`
- Factory: `download/TaskFactory.kt`
- Impl notes: `DownloaderV2Impl` already has partial custom-command execution and notifications; file header TODO still lists “Notification / Custom commands / States for ViewModels” — treat as **audit half-done work**, do not reimplement blindly.

---

## Pre-migration capability matrix

| Capability | V1 `Downloader` | V2 `DownloaderV2` | Migration difficulty |
|------------|-----------------|-------------------|----------------------|
| Normal download queue | `getInfoAndDownload`, `downloadVideoWithInfo`, … | `enqueue` + `Task` | Low (home already on V2) |
| Global state machine | `Idle / FetchingInfo / Downloading* / Updating` | Per-task `DownloadState` only | Medium |
| Error UI | `errorState` Flow | Task-level `DownloadState.Error` | Medium |
| Custom command list / logs | `mutableTaskList` | `TypeInfo.CustomCommand` + map filter | **High** |
| yt-dlp update mutex | `State.Updating` | Missing | Medium |
| Notification cancel | `onProcessCanceled` | `cancel(taskId)` | Low |
| `DownloadUtil` callbacks | `onTask*`, `updateTaskOutput` | Impl runs execute path internally | Medium |
| V1 home UI | `DownloadPage` + `HomePageViewModel` | N/A if dead | Low (delete) |

---

## Implementation history

The migration was implemented in milestone order so each commit remained independently reviewable.

### Milestone 0 — Inventory and freeze ✅

**Goals**

- Freeze new product code from importing `com.chloemlla.seal.Downloader`.
- Publish this plan; classify every remaining reference as **dead** vs **live residual**.

**Work**

1. Full-repo grep of `\bDownloader\b` (object only; ignore “downloader” locals of type `DownloaderV2`).
2. Mark dead V1 UI after navigation/flavor audit.
3. Optional: Detekt / custom lint rule forbidding new `import com.chloemlla.seal.Downloader` (except legacy package allowlist during transition).

**Acceptance**

- Call graph documented (this file or linked appendix).
- Team agrees no new V1 call sites.

---

### Milestone 1 — Low-risk cleanup ✅ (`6da505d9`)

**Goals**

- Cut warning count without touching custom-command data model.
- Zero user-visible behavior change for main downloads.

**Work**

1. **Remove or stop compiling dead V1 home UI** (`DownloadPage`, `HomePageViewModel`) if audit confirms no entry points (including previews that must stay: move previews or drop).
2. **`NotificationActionReceiver`**
   - Prefer only `DownloaderV2.cancel(taskId)`.
   - Drop `Downloader.onProcessCanceled` once custom-command cancel IDs are known to match V2, **or** keep a narrow bridge only for legacy custom-command keys until Milestone 2.
3. **Extract yt-dlp update gate**
   - New small type, e.g. `YtDlpUpdateGate` (StateFlow busy flag) **or** a method/flag on `DownloaderV2`.
   - Rewrite `YtdlpUpdater` to use the gate instead of `Downloader.downloaderState` / `updateState`.
   - Ensure auto-update still skips when downloads are busy if that was previous semantics (document chosen rule: “skip when any V2 task running” vs “only when V1 global non-Idle”).

**Acceptance**

- No deprecation warnings from: `YtdlpUpdater`, dead UI files (gone), and ideally `NotificationActionReceiver`.
- yt-dlp auto-update still does not stomp an active download (per chosen rule).
- Third-party / home download paths unchanged.

---

### Milestone 2 — Custom commands on V2 ✅ (`6ac6b867`)

This is the bulk of remaining risk and warnings.

#### 2.1 Align IDs and models

| V1 | V2 |
|----|----|
| `CustomCommandTask` + `mutableTaskList[key]` | `Task(type = CustomCommand(template))` + `Task.State` |
| `makeKey(url, templateName)` → `"${templateName}_$url"` | `Task.id` (includes template id/name + url/prefs hashing) |
| `State.Running(progress) / Error / Completed / Canceled` | `DownloadState.Running / Error / Completed / …` |

**Critical:** notification cancel, log page lookup, and MMKV task backup must use **one** id scheme. Provide a one-time compatibility mapping if in-flight backups still store V1 keys.

#### 2.2 UI entry rewrite

- `TaskListPage`: `Downloader.executeCommandWithUrl(url)` → build `Task` via `TaskFactory` / explicit `TypeInfo.CustomCommand` + `downloader.enqueue(...)`.
- List source: filter `getTaskStateMap()` for `CustomCommand` (and any shared “command task” presentation mapping).
- `TaskLogPage`: stop `hashCode()` lookup on V1 tasks; navigate by stable `task.id` (update `Route.TASK_LOG` args if needed).

#### 2.3 Decouple `DownloadUtil`

Today custom-command execution notifies V1 via `onTaskStarted` / `onTaskEnded` / `onTaskError` / `updateTaskOutput` / process count hooks.

Target:

- V2 path owns state updates (reuse / finish `DownloaderV2Impl` private custom-command executor).
- `DownloadUtil` remains a pure executor (build argv, run process, stream lines).
- Remove V1 imports from `DownloadUtil` once no V1 caller remains.

#### 2.4 Notifications and foreground service

- Custom-command `notificationId` must match `cancel(taskId)` identity rules.
- Running custom commands must count toward V2 concurrency / `App.startService` / `stopService` (V2 already tracks running count for normal tasks).

**Acceptance**

- Custom command: start → progress log → cancel → complete notification, all via V2.
- `DownloadUtil`, `TaskListPage`, `TaskLogPage` have zero `com.chloemlla.seal.Downloader` imports.
- No dual queue (users must not see the same job on two lists).

---

### Milestone 3 — Delete V1 singleton ✅

**Goals**

- Zero remaining references to `object Downloader`.
- CI compile free of `Prefer DownloaderV2` deprecations from this object.

**Work**

1. Full-repo grep; fix stragglers.
2. Delete `Downloader.kt` (or shrink to typealiases only if a transitional re-export is required — prefer full delete).
3. Remove dead V1-only helpers in `DownloadUtil` / notifications.
4. Update docs that mention legacy Downloader.
5. Confirm Koin, integration, and backup restore paths.

**Acceptance**

- Grep clean for deprecated object.
- Regression: main download, playlist, custom command, notification cancel, yt-dlp auto-update, external `enqueue` + `watchTask`.

---

## Suggested task / PR breakdown

```text
P0  Inventory + freeze new V1 usage (this doc)
P1  Remove dead DownloadPage / HomePageViewModel (if safe)
P1  YtDlpUpdateGate + NotificationActionReceiver cleanup
P2  TaskFactory / helpers for CustomCommand from Task UI
P2  Migrate TaskListPage + TaskLogPage to V2 state map
P2  Stop DownloadUtil → Downloader callbacks
P3  Delete object Downloader; CI warnings green
```

Rough effort: **~1–2 engineer-weeks** including CI round-trips and id/backup compatibility. Milestone 1 alone is enough for a partial warning drop but **not** full clearance.

---

## Risks

| Risk | Mitigation |
|------|------------|
| Task id rule change breaks cancel / backup / log deep links | Compatibility mapper; migrate backup decode once; update nav args in same PR as log page |
| Dual queue during half-migration | Milestone 2 must be single-writer: enqueue only V2, list only V2 |
| V1 `isDownloaderAvailable()` vs V2 `MAX_CONCURRENCY = 3` | Document concurrency policy for custom commands (share pool vs dedicated slot) |
| Half-finished V2 custom-command code diverges from V1 UX | Side-by-side checklist (log streaming, error report, notification actions) before cutover |
| Large PR review load | One milestone per PR; keep warning-count delta in PR body |

---

## Optional bridge (short-lived only)

Add a thin facade on `DownloaderV2`:

- `customCommandTasks` observation API
- `executeCommandWithUrl(...)`

Point UI at the facade first, then delete V1. **Not** an end state — still requires model mapping and id unification.

Do **not** “fix” warnings by `@file:Suppress` on call sites as a long-term strategy.

---

## Per-PR acceptance checklist

- [x] Only files for the current milestone (no unrelated BOM/lint churn)
- [x] Repo-wide count of `import com.chloemlla.seal.Downloader` reaches zero
- [ ] CI `compile*Kotlin` deprecation lines for Downloader compared before/after
- [ ] Manual: custom command start → log → cancel → finish notification (from Milestone 2+)
- [ ] Manual: auto-update does not corrupt in-flight download
- [ ] Manual/external: integration `enqueue` + `watchTask` still reports terminal states

---

## Key code anchors

| Path | Role |
|------|------|
| `app/src/main/java/com/chloemlla/seal/download/DownloaderV2.kt` | V2 interface + `DownloaderV2Impl` |
| `app/src/main/java/com/chloemlla/seal/download/Task.kt` | Unified task model |
| `app/src/main/java/com/chloemlla/seal/download/TaskFactory.kt` | Task construction helpers |
| `app/src/main/java/com/chloemlla/seal/ui/page/downloadv2/` | Current home download UI |
| `app/src/main/java/com/chloemlla/seal/ui/page/command/` | V2 custom-command list and logs |
| `app/src/main/java/com/chloemlla/seal/util/DownloadUtil.kt` | Stateless download/custom-command executor |
| `app/src/main/java/com/chloemlla/seal/ui/page/AppEntry.kt` | Navigation: HOME → V2 |

---

## Decision log

| Date | Decision |
|------|----------|
| 2026-07-13 | Full V1→V2 migration is **out of scope** for the small Kotlin deprecation-warning task; tracked here as a dedicated plan. |
| 2026-07-13 | Prefer multi-milestone PRs over one rewrite; home path already V2; custom command is the critical path. |
| 2026-07-13 | Migration implemented: dead V1 UI removed, custom commands moved to V2, and `Downloader.kt` deleted. |

---

## Next actions

1. Confirm the GitHub workflow compiles without `Prefer DownloaderV2` warnings.
2. Manually exercise custom command start → log → cancel/restart → completion notification.
3. Confirm external integration `enqueue` + `watchTask` terminal states remain green.
