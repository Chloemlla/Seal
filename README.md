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

- Third-party apps can **delegate** downloads to Seal via Intents (share / open URL / `com.chloemlla.seal.action.DOWNLOAD`). See [docs/third-party-delegate-integration.md](docs/third-party-delegate-integration.md); Chinese call guide: [docs/third-party-call-guide.md](docs/third-party-call-guide.md).

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

## 🔌 Third-party integration

Other apps may only **delegate** downloads to Seal (Seal always runs yt-dlp and owns the queue/files).

- Integration overview: [docs/third-party-delegate-integration.md](docs/third-party-delegate-integration.md)
- Chinese caller guide: [docs/third-party-call-guide.md](docs/third-party-call-guide.md)
- Action: `com.chloemlla.seal.action.DOWNLOAD`
- Status broadcast: `com.chloemlla.seal.action.DOWNLOAD_STATUS`

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
