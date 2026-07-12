<div align="center">

<img width="" src="fastlane/metadata/android/en-US/images/icon.png"  width=160 height=160  align="center">

# Seal

### Video/Audio Downloader for Android

**Package ID:** `com.chloemlla.seal`

English
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-zh_Hans.md">简体中文</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-zh_Hant.md">繁體中文</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-ar.md">العربية</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-pt.md">Portuguese</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-ua.md">Українська</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-th.md">ภาษาไทย</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-fa.md">فارسی</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-it.md">Italiano</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-az.md">Azərbaycanca</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-ru.md">Русский</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-sr.md">Српски</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-ja.md">日本語</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-id.md">Indonesia</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-hi.md">हिंदी</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="translations/README-bn.md">বাংলা</a>



[![GitHub release (latest by date)](https://img.shields.io/github/v/release/Chloemlla/Seal?color=black&label=Stable&logo=github)](https://github.com/Chloemlla/Seal/releases/latest/)
[![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/Chloemlla/Seal?include_prereleases&label=Preview&logo=Github)](https://github.com/Chloemlla/Seal/releases/)
[![Keep a Changelog](https://img.shields.io/badge/Changelog-lightgray?style=flat&color=gray&logo=keep-a-changelog)](https://github.com/Chloemlla/Seal/blob/main/CHANGELOG.md)
[![GitHub all releases](https://img.shields.io/github/downloads/Chloemlla/Seal/total?label=Downloads&logo=github)](https://github.com/Chloemlla/Seal/releases/)
[![GitHub Repo stars](https://img.shields.io/github/stars/Chloemlla/Seal?style=flat&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPHN2ZyBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIC05NjAgOTYwIDk2MCIgd2lkdGg9IjI0IiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPgogIDxwYXRoIGQ9Im0zNTQtMjQ3IDEyNi03NiAxMjYgNzctMzMtMTQ0IDExMS05Ni0xNDYtMTMtNTgtMTM2LTU4IDEzNS0xNDYgMTMgMTExIDk3LTMzIDE0M1pNMjMzLTgwbDY1LTI4MUw4MC01NTBsMjg4LTI1IDExMi0yNjUgMTEyIDI2NSAyODggMjUtMjE4IDE4OSA2NSAyODEtMjQ3LTE0OUwyMzMtODBabTI0Ny0zNTBaIiBzdHlsZT0iZmlsbDogcmdiKDI0NSwgMjI3LCA2Nik7Ii8%2BCjwvc3ZnPg%3D%3D&color=%23f8e444)](https://github.com/Chloemlla/Seal/stargazers)
[![Supported-Sites](https://img.shields.io/badge/Sites-9cf?style=flat&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPHN2ZyBoZWlnaHQ9IjI0cHgiIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjI0cHgiIGZpbGw9IiNGRkZGRkYiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZD0iTTAgMGgyNHYyNEgwVjB6IiBmaWxsPSJub25lIi8+CiAgPHBhdGggZD0iTTExLjk5IDJDNi40NyAyIDIgNi40OCAyIDEyczQuNDcgMTAgOS45OSAxMEMxNy41MiAyMiAyMiAxNy41MiAyMiAxMlMxNy41MiAyIDExLjk5IDJ6bTYuOTMgNmgtMi45NWMtLjMyLTEuMjUtLjc4LTIuNDUtMS4zOC0zLjU2IDEuODQuNjMgMy4zNyAxLjkxIDQuMzMgMy41NnpNMTIgNC4wNGMuODMgMS4yIDEuNDggMi41MyAxLjkxIDMuOTZoLTMuODJjLjQzLTEuNDMgMS4wOC0yLjc2IDEuOTEtMy45NnpNNC4yNiAxNEM0LjEgMTMuMzYgNCAxMi42OSA0IDEycy4xLTEuMzYuMjYtMmgzLjM4Yy0uMDguNjYtLjE0IDEuMzItLjE0IDJzLjA2IDEuMzQuMTQgMkg0LjI2em0uODIgMmgyLjk1Yy4zMiAxLjI1Ljc4IDIuNDUgMS4zOCAzLjU2LTEuODQtLjYzLTMuMzctMS45LTQuMzMtMy41NnptMi45NS04SDUuMDhjLjk2LTEuNjYgMi40OS0yLjkzIDQuMzMtMy41NkM4LjgxIDUuNTUgOC4zNSA2Ljc1IDguMDMgOHpNMTIgMTkuOTZjLS44My0xLjItMS40OC0yLjUzLTEuOTEtMy45NmgzLjgyYy0uNDMgMS40My0xLjA4IDIuNzYtMS45MSAzLjk2ek0xNC4zNCAxNEg5LjY2Yy0uMDktLjY2LS4xNi0xLjMyLS4xNi0ycy4wNy0xLjM1LjE2LTJoNC42OGMuMDkuNjUuMTYgMS4zMi4xNiAycy0uMDcgMS4zNC0uMTYgMnptLjI1IDUuNTZjLjYtMS4xMSAxLjA2LTIuMzEgMS4zOC0zLjU2aDIuOTVjLS45NiAxLjY1LTIuNDkgMi45My00LjMzIDMuNTZ6TTE2LjM2IDE0Yy4wOC0uNjYuMTQtMS4zMi4xNC0ycy0uMDYtMS4zNC0uMTQtMmgzLjM4Yy4xNi42NC4yNiAxLjMxLjI2IDJzLS4xIDEuMzYtLjI2IDJoLTMuMzh6IiBzdHlsZT0iZmlsbDogcmdiKDE2MiwgMTk4LCAyMzQpOyIvPgo8L3N2Zz4=&label=Supported)](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)
[![Telegram Channel](https://img.shields.io/badge/Telegram-Seal-blue?style=flat&logo=telegram)](https://t.me/seal_app)
[![Matrix](https://img.shields.io/matrix/seal-space%3Amatrix.org?server_fqdn=matrix.org&style=flat&logo=element&label=Matrix&color=%230DBD8B)
](https://matrix.to/#/#seal-space:matrix.org)


</div>


## 📱 Screenshots

<div align="center">
<div>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.jpg" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.jpg" width="30%" />
</div>
</div>

<br>

## 📖 Features

- Download videos and audio files from video platforms supported by [yt-dlp](https://github.com/yt-dlp/yt-dlp) (formerly youtube-dl).

- Embed metadata and video thumbnail into extracted audio files supported by [mutagen](https://github.com/quodlibet/mutagen).

- Download all videos in the playlist with one click.

- Use embedded [aria2c](https://github.com/aria2/aria2) as external downloader for all your downloads.

- Embed subtitles into the downloaded videos.

- Execute custom yt-dlp commands with templates.

- Manage in-app downloads and custom command templates.

- Easy to use and user-friendly.

- Third-party apps can **delegate** downloads to Seal via Intents (share / open URL / `com.chloemlla.seal.action.DOWNLOAD`) with L2/L3 status feedback. See [docs/third-party-delegate-integration.md](docs/third-party-delegate-integration.md); Chinese call guide: [docs/third-party-call-guide.md](docs/third-party-call-guide.md).
- Targets **Android API 37**, uses scoped storage by default (no `MANAGE_EXTERNAL_STORAGE` dependency for normal downloads), and ships the latest **Stable** yt-dlp binary at build time.

- [Material Design 3](https://m3.material.io/) style UI, with dynamic color theme.

- MAD: UI and logic written with pure Kotlin. Single activity, no fragments, only composable destinations.

## 📦 Application ID

| Build | applicationId |
|-------|----------------|
| release | `com.chloemlla.seal` |
| debug | `com.chloemlla.seal.debug` |
| preview | `com.chloemlla.seal.preview` |

FileProvider authority: `${applicationId}.provider`

## ⬇️ Download

For most devices, it is recommended to install the **arm64-v8a** version of the APKs.

- Download the latest stable version from [GitHub releases](https://github.com/Chloemlla/Seal/releases/latest)
- Install [pre-release](https://github.com/Chloemlla/Seal/releases/) builds to help test new features and changes

> This fork uses package id `com.chloemlla.seal` and is **not** published under the upstream F-Droid listing (`com.junkfood.seal`).  
> Upstream project: [JunkFood02/Seal](https://github.com/JunkFood02/Seal)

## 🚀 Chloemlla fork improvements

This `main` branch is the **Chloemlla** fork of Seal. Beyond retargeting the package id and release channels, it includes a large set of platform, security, integration, CI, and UX changes relative to the upstream project.

### Identity and packaging

- Application package renamed to **`com.chloemlla.seal`** (debug `*.debug`, preview `*.preview`).
- FileProvider authority is **`${applicationId}.provider`**.
- README, badges, release links, and docs retargeted to [Chloemlla/Seal](https://github.com/Chloemlla/Seal).
- **Not** published as the upstream F-Droid package (`com.junkfood.seal`).

### Android platform

- **Compile / target SDK 37** (Android 16 line), with minSdk kept at 24.
- AGP / Gradle / Kotlin toolchain modernized for this target:
  - Android Gradle Plugin **9.2.x**
  - Gradle wrapper **9.4.1**
  - Kotlin **2.3.x** + KSP **2.3.x**
  - Built-in Kotlin support (legacy `kotlin-android` plugin removed)
  - JVM targets aligned to **21**
- Major dependency BOM / library stack upgrade (Compose, lifecycle, Room, OkHttp, etc.), with pins for packages that lag the BOM.
- AGP 9 compatibility work:
  - `buildFeatures.resValues` enabled where `resValue` is used
  - legacy `applicationVariants` APK-rename APIs removed
  - `extractNativeLibs` moved out of the manifest into packaging options
  - freeCompilerArgs migrated to the `compilerOptions` DSL

### Storage and permissions

- **MMKV multi-store layout** for faster, safer preference IO:
  - `seal_prefs` — stable settings / theme / download preferences
  - `seal_runtime` — high-churn data (queue backup JSON, saved links, version timestamps)
  - one-time migration from the legacy `defaultMMKV` instance
- In-memory **download preference snapshot** (`createFromPreferences`) avoids re-decoding dozens of keys on every configure UI open; invalidated on related writes.
- Task queue MMKV backup is **debounced (~750ms)** and **structure-fingerprinted** (~5% progress buckets) so progress updates do not rewrite large JSON every tick; queue is flushed when the app backgrounds.
- Dropped reliance on **`MANAGE_EXTERNAL_STORAGE`** for default download paths.
- Scoped-storage oriented defaults with safer path resolution / writable-directory checks.
- Download history “delete local file” path hardened:
  - absolute paths, `file://`, and `content://` document URIs
  - missing file treated as already deleted
  - same-basename sidecar cleanup (subtitles, thumbnails, `.info.json`, etc.)
  - MediaStore refresh after filesystem deletes
  - history row is always removed first; partial file failures surface a toast
  - clearer checkbox copy (“Also delete local media file” / “同时删除本地源文件”)

### Security and privacy hardening

- Remediated critical/high findings from the internal audit notes (`docs/aduit.md`):
  - reduced risky surfaces around token handling and overly broad external control
  - safer third-party entry validation
  - custom-command / external-intent boundaries clarified (Seal remains the only yt-dlp owner)
- External callers are governed by explicit user settings (master switch, auto-start, optional package whitelist).

### Third-party download delegation (L1–L3)

Other apps may **only delegate** work to Seal. There is no embeddable download SDK and no remote control API.

| Level | Capability | Status |
|------|------------|--------|
| **L1** | Share / open URL → configure UI | Available |
| **L2** | Parameterized `DOWNLOAD` intent + optional auto-start | Available |
| **L3** | Activity result + directed status broadcast + content URI | Available |
| **L4** | Bound service / provider query API | Not implemented |

Highlights:

- Action: `com.chloemlla.seal.action.DOWNLOAD`
- Status: `com.chloemlla.seal.action.DOWNLOAD_STATUS`
- Protocol versioning (`protocol_version`, currently `1`)
- User-controlled settings under **Settings → Interface & interaction → External downloads**
- UI-path external downloads are watched so L3 status still reports completion/failure/cancel
- `extract_audio=true` opens the Quick Download sheet with **Audio** type (not the global video default)
- Reference integration: [Chloemlla/PiliPlus](https://github.com/Chloemlla/PiliPlus) video menu “下载视频 / 下载音频”
- Docs:
  - [docs/third-party-delegate-integration.md](docs/third-party-delegate-integration.md)
  - [docs/third-party-call-guide.md](docs/third-party-call-guide.md) (中文调用说明)
  - [docs/third-party-delegate-integration-TODO.md](docs/third-party-delegate-integration-TODO.md)

### yt-dlp packaging and defaults

- Build-time task **`downloadStableYtDlp`** fetches the latest **Stable** yt-dlp release and packages it as `res/raw/ytdlp`, overriding the older binary shipped inside `youtubedl-android`.
- Version stamp lives at `app/ytdlp.version` (**outside** `res/raw`) so Android resource merging does not treat `ytdlp` / `ytdlp.version` as duplicate `R.raw.ytdlp` names.
- Download temps are kept outside `res/raw`; stray collision files under raw are purged before packaging.
- Default in-app yt-dlp update channel set to **Stable** (was Nightly).
- Bundled version exposed via `BuildConfig.YT_DLP_BUNDLED_VERSION` for diagnostics.

### CI / pre-release workflow

Workflow: [`.github/workflows/build-pre-release.yaml`](.github/workflows/build-pre-release.yaml)

- Android Lint extracted into a **parallel job** alongside the release build.
- Publish is gated with `needs: [Lint, BuildPreRelease]` so a lint failure cannot race past a release.
- Signed APKs transferred via artifacts into the Publish job.
- Full build-error report packaging + artifact upload on failure (logs, lint/test reports, environment diagnostics, failure summary).
- Movable **`latest`** git tag force-updated for consumers that pin that ref.
- Gradle console noise reduced (e.g. dropped overly verbose `--info` cache spam).
- Local drop folder for CI reports ignored (`1/`).

### UI / product polish

- Portrait ActionSheet overflow: less-important actions fold into a **More** menu so primary actions stay reachable.
- Lint cleanups around locale observation (`LocalConfiguration`) and other Compose/resource correctness issues.
- Assorted compile fixes after the stack upgrade (clipboard Context usage, Material M2 / DocumentFile deps restoration where still required, etc.).

### Developer notes

- Local full Flutter/Gradle builds are not required for every documentation change; release validation is expected through GitHub Actions.
- Generated yt-dlp artifacts (`app/src/main/res/raw/ytdlp`, `app/ytdlp.version`, temps) are gitignored.
- For agents/contributors working in this repo, see [`AGENTS.md`](AGENTS.md).

---

## 🔌 Third-party integration

Other apps may only **delegate** downloads to Seal (Seal always runs yt-dlp and owns the queue/files). See the full **Chloemlla fork improvements** section above for L1–L3 capability details.

- Integration overview: [docs/third-party-delegate-integration.md](docs/third-party-delegate-integration.md)
- Chinese caller guide: [docs/third-party-call-guide.md](docs/third-party-call-guide.md)
- Action: `com.chloemlla.seal.action.DOWNLOAD`
- Status broadcast: `com.chloemlla.seal.action.DOWNLOAD_STATUS`
- Protocol version: `1` (send `protocol_version`)

## 💬 Contact

Join the community [Telegram Channel](https://t.me/seal_app) or [Matrix Space](https://matrix.to/#/#seal-space:matrix.org) for discussion around Seal.

For this fork, prefer GitHub issues/PRs on [Chloemlla/Seal](https://github.com/Chloemlla/Seal).

## 🤝 Contributing

Contributions are welcome!

You can help translate Seal on [Hosted Weblate](https://hosted.weblate.org/projects/seal/).

[![Translate status](https://hosted.weblate.org/widgets/seal/-/strings/multi-auto.svg)](https://hosted.weblate.org/engage/seal/)

>[!Note]
>
>For submitting bug reports, feature requests, questions, or any other ideas to improve, please read [CONTRIBUTING.md](CONTRIBUTING.md) for instructions and guidelines first.

## ⭐️ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Chloemlla/Seal&type=Timeline)](https://star-history.com/#Chloemlla/Seal&Timeline)

## 🧱 Credits

This repository is a fork of [JunkFood02/Seal](https://github.com/JunkFood02/Seal).

Seal is a simple GUI of [yt-dlp](https://github.com/yt-dlp/yt-dlp), based on [youtubedl-android](https://github.com/yausername/youtubedl-android).

Some of the UI designs and codes are borrowed from [Read You](https://github.com/Ashinch/ReadYou) and [Music You](https://github.com/Kyant0/MusicYou).

- [dvd](https://github.com/yausername/dvd)
- [Material color utilities](https://github.com/material-foundation/material-color-utilities)
- [Monet](https://github.com/Kyant0/Monet)

## 📃 License

[![GitHub](https://img.shields.io/github/license/Chloemlla/Seal?style=for-the-badge)](https://github.com/Chloemlla/Seal/blob/main/LICENSE)

>[!Warning]
>
>Except for the source code licensed under the GPLv3 license,
>all other parties are prohibited from using Seal's name as a downloader app,
>and the same is true for Seal's derivatives.
>Derivatives include but are not limited to forks and unofficial builds.

<div align="right">
<table><td>
<a href="#start-of-content">👆 Scroll to top</a>
</td></table>
</div>
