package com.chloemlla.seal.integration

/**
 * Frozen L2/L3 protocol for third-party **delegate-only** downloads.
 *
 * Third parties submit URLs to Seal; Seal owns the queue, yt-dlp process, and files.
 * Raw yt-dlp commands, cookie **export**, and remote control are intentionally unsupported.
 *
 * Protocol v2 (additive): optional inbound cookies (task-scoped) and keep_sections / strip_segments.
 */
object ExternalDownloadProtocol {
    const val ACTION_DOWNLOAD = "com.chloemlla.seal.action.DOWNLOAD"
    const val ACTION_DOWNLOAD_STATUS = "com.chloemlla.seal.action.DOWNLOAD_STATUS"

    const val PROTOCOL_VERSION = 2
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_SUPPORTED_VERSION = 2

    // Request extras
    const val EXTRA_PROTOCOL_VERSION = "protocol_version"
    const val EXTRA_URL = "url"
    const val EXTRA_URLS = "urls"
    const val EXTRA_EXTRACT_AUDIO = "extract_audio"
    const val EXTRA_DOWNLOAD_SUBTITLE = "download_subtitle"
    const val EXTRA_AUTO_START = "auto_start"
    const val EXTRA_OPEN_UI = "open_ui"
    const val EXTRA_CALLER_REQUEST_ID = "caller_request_id"

    // v2 cookie extras (inbound only; never exported)
    const val EXTRA_COOKIES_FORMAT = "cookies_format"
    const val EXTRA_COOKIES = "cookies"
    const val EXTRA_COOKIES_URI = "cookies_uri"
    const val EXTRA_COOKIES_MID = "cookies_mid"
    const val EXTRA_COOKIES_DOMAIN_HINT = "cookies_domain_hint"
    const val EXTRA_USE_COOKIES = "use_cookies"
    const val EXTRA_COOKIES_REQUIRED = "cookies_required"

    // v2 strip / sections (seconds; additive for strip-ads clients)
    const val EXTRA_STRIP_SEGMENTS = "strip_segments"
    const val EXTRA_KEEP_SECTIONS = "keep_sections"
    const val EXTRA_REMOVE_SEGMENTS = "remove_segments"

    // Response / status extras
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TASK_IDS = "task_ids"
    const val EXTRA_STATUS = "status"
    const val EXTRA_ERROR_CODE = "error_code"
    const val EXTRA_ERROR_MESSAGE = "error_message"
    const val EXTRA_CONTENT_URI = "content_uri"
    const val EXTRA_DISPLAY_NAME = "display_name"
    const val EXTRA_MIME_TYPE = "mime_type"
    const val EXTRA_CALLER_PACKAGE = "caller_package"

    // Status values
    const val STATUS_ACCEPTED = "accepted"
    const val STATUS_REJECTED = "rejected"
    const val STATUS_NEEDS_UI = "needs_ui"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_FAILED = "failed"
    const val STATUS_CANCELED = "canceled"

    // error_code values
    const val ERROR_OK = "ok"
    const val ERROR_DISABLED = "disabled"
    const val ERROR_AUTO_START_DENIED = "auto_start_denied"
    const val ERROR_INVALID_URL = "invalid_url"
    const val ERROR_UNSUPPORTED_VERSION = "unsupported_version"
    const val ERROR_CALLER_DENIED = "caller_denied"
    const val ERROR_QUEUE_REJECTED = "queue_rejected"
    const val ERROR_INTERNAL = "internal_error"
    const val ERROR_DOWNLOAD_FAILED = "download_failed"
    const val ERROR_CANCELED = "canceled"

    // v2 cookie error codes (task implement + research aliases)
    const val ERROR_COOKIE_DENIED = "cookie_denied"
    const val ERROR_COOKIE_INVALID = "cookie_invalid"
    const val ERROR_COOKIE_TOO_LARGE = "cookie_too_large"
    const val ERROR_COOKIES_DISABLED = "cookies_disabled"
    const val ERROR_COOKIES_INVALID = "cookies_invalid"
    const val ERROR_COOKIES_TOO_LARGE = "cookies_too_large"
    const val ERROR_COOKIES_URI_DENIED = "cookies_uri_denied"
    const val ERROR_COOKIES_UNSUPPORTED = "cookies_unsupported"

    // Cookie formats
    const val COOKIES_FORMAT_JSON_MAP = "json_map"
    const val COOKIES_FORMAT_NETSCAPE = "netscape"
    const val COOKIES_FORMAT_NAME_VALUE = "name_value"

    /** Max raw `cookies` string length (256 KiB). */
    const val MAX_COOKIES_PAYLOAD_CHARS = 256 * 1024

    /** Soft default domain when converting json_map / name_value. */
    const val DEFAULT_COOKIES_DOMAIN = ".bilibili.com"
}
