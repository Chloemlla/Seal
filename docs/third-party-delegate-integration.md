Chinese caller guide: [`third-party-call-guide.md`](./third-party-call-guide.md)

# Seal third-party download delegation (L1–L3)

Third-party apps may **only delegate** downloads to Seal. Seal always owns the queue, yt-dlp process, notifications, and saved files. There is no embeddable download SDK and no remote control API.

Related checklist: [`third-party-delegate-integration-TODO.md`](./third-party-delegate-integration-TODO.md)

## Package IDs

| Build | applicationId |
|-------|----------------|
| release | `com.chloemlla.seal` |
| debug | `com.chloemlla.seal.debug` |
| preview | `com.chloemlla.seal.preview` |

FileProvider authority: `${applicationId}.provider`

## Capability levels

| Level | What it does | Status |
|------|----------------|--------|
| L1 | Share / open URL → configure UI | Available |
| L2 | Parameterized `DOWNLOAD` intent + optional auto-start | Available |
| L3 | Activity result + directed status broadcast + content URI | Available |
| L4 | Bound service / provider query API | Not implemented |

## User settings

Path: **Settings → Interface & interaction → External downloads**

| Setting | Default | Meaning |
|---------|---------|---------|
| Allow external apps to delegate downloads | ON | Master switch |
| Allow external auto-start | OFF | Permit `auto_start=true` without configure sheet |
| Accept cookies from external apps | **OFF** | Permit protocol v2 inbound task-scoped cookies |
| Limit external callers | OFF | When ON, only whitelisted packages may call |
| Allowed packages | empty | One package name per line |

## Request: `com.chloemlla.seal.action.DOWNLOAD`

Compatible legacy surfaces (still supported):

- `Intent.ACTION_SEND` + `text/plain` (`EXTRA_TEXT`)
- `Intent.ACTION_VIEW` + `http`/`https`

### Request extras

| Extra | Type | Notes |
|-------|------|------|
| `protocol_version` | Int | `1` or `2` (latest = 2); missing → 1 |
| `url` | String | Preferred single URL |
| `urls` | String[] | Optional multi URL |
| `extract_audio` | Boolean | Optional override |
| `download_subtitle` | Boolean | Optional override |
| `auto_start` | Boolean | Requires user setting |
| `open_ui` | Boolean | Default `true` |
| `caller_request_id` | String | Echoed in responses |
| `cookies_format` / `cookies` / `cookies_uri` / `cookies_mid` / `cookies_domain_hint` / `use_cookies` | … | **v2 only**; inbound task-scoped cookies (see call guide) |
| `strip_segments` / `keep_sections` / `remove_segments` | … | **v2**; keep_sections JSON seconds → `--download-sections` |

Also accepted: `Intent.EXTRA_TEXT`, `intent.data` URL.

**Inbound cookies (v2):** require External downloads → Accept cookies from external apps. Materialized under `cache/external_cookies/`; never export Seal cookies outbound.

### Kotlin example (delegate with UI)

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

### Kotlin example (auto-start, for result)

```kotlin
val intent = Intent("com.chloemlla.seal.action.DOWNLOAD").apply {
    setPackage("com.chloemlla.seal")
    putExtra("protocol_version", 1)
    putExtra("url", videoUrl)
    putExtra("auto_start", true)
    putExtra("open_ui", false)
    putExtra("caller_request_id", requestId)
}
// Prefer Activity Result API; RESULT_OK + status=accepted means queued.
startActivityForResult(intent, REQUEST_SEAL_DOWNLOAD)
```

## Immediate activity result

Returned on accept / reject / needs_ui:

| Extra | Meaning |
|-------|---------|
| `status` | `accepted` / `rejected` / `needs_ui` |
| `error_code` | see table below |
| `task_id` / `task_ids` | present when accepted |
| `caller_request_id` | echo |

## Terminal status broadcast (L3)

Action: `com.chloemlla.seal.action.DOWNLOAD_STATUS`  
Seal sets `Intent.setPackage(callerPackage)` (directed only).

Terminal statuses: `completed`, `failed`, `canceled`.

On `completed`, `content_uri` may be granted read-only via FileProvider.

Caller must register a receiver for `com.chloemlla.seal.action.DOWNLOAD_STATUS` (exported as needed for their app) and should verify extras.

### Receiver sketch

```kotlin
class SealDownloadStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.chloemlla.seal.action.DOWNLOAD_STATUS") return
        val status = intent.getStringExtra("status")
        val taskId = intent.getStringExtra("task_id")
        val contentUri = intent.getStringExtra("content_uri")
        // handle completed / failed / canceled
    }
}
```

## error_code

| code | meaning |
|------|---------|
| `ok` | success / accepted / needs_ui ok |
| `disabled` | user disabled external delegation |
| `auto_start_denied` | auto-start not allowed |
| `invalid_url` | no usable http(s) URL |
| `unsupported_version` | protocol_version outside 1..1 |
| `caller_denied` | whitelist rejection |
| `queue_rejected` | rate limit |
| `internal_error` | Seal-side failure accepting task |
| `download_failed` | terminal task failure |
| `canceled` | terminal cancel |

## Non-goals (will not be exposed)

- Raw yt-dlp command strings
- Cookie / account export
- Arbitrary filesystem write paths
- Mutation of Seal global settings by callers
- Remote HTTP control of the device
- Returning unstable media CDN direct links as API

## Discovery

Application meta-data:

- `com.chloemlla.seal.external_download_protocol_version` = `1`
- `com.chloemlla.seal.external_download_max_protocol_version` = `1`

## Implementation map

- `com.chloemlla.seal.integration.ExternalDownloadProtocol`
- `ExternalDownloadRequestParser` / `ExternalDownloadGate`
- `ExternalDownloadEntry` / `ExternalDownloadCoordinator`
- `QuickDownloadActivity`, `MainActivity`
- Settings: `InteractionPreferencePage`


