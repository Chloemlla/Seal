# MMKV storage layout (Seal)

> Date: 2026-07-12  
> Goal: stronger MMKV usage for UX (faster configure path, less IO jank) without changing preference semantics.

## Stores

| mmap id | Purpose | Examples |
|---------|---------|----------|
| `seal_prefs` | Stable user settings | theme, download format, external delegate switches, directories |
| `seal_runtime` | High-churn runtime | `task_list` JSON, `saved_links`, yt-dlp version/time, welcome counter |
| `defaultMMKV` (legacy) | Pre-split store | read fallback + one-time migration source |

Migration flag: `mmkv_storage_migrated_v1` (in `seal_prefs`).

## Download preference snapshot

- `DownloadUtil.DownloadPreferences.createFromPreferences()` returns a cached snapshot.
- Rebuild: `buildFromPreferenceStore()`.
- Invalidation: any write to keys classified by `PreferenceStorageKeys.isDownloadPreferenceKey`.

## Task list backup

- Written by `DownloaderV2` via `PreferenceUtil.encodeTaskListBackup`.
- Debounced ~750ms (`TASK_BACKUP_DEBOUNCE_MS`) to avoid progress-tick thrash.
- Structural fingerprint with ~5% progress buckets skips pure progress noise.
- Immediate flush on process background (`ProcessLifecycleOwner.onStop`) + `runtime.sync()`.

## Download preference warm-up

- `PreferenceUtil.warmDownloadPreferencesSnapshot()` runs on cold start (IO) so the first configure/download path hits a warm cache.

## Compatibility

- Public `PreferenceUtil` extension APIs unchanged.
- External delegate preference keys unchanged.
