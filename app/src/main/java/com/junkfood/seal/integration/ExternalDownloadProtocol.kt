package com.junkfood.seal.integration

/**
 * Frozen L2/L3 protocol for third-party **delegate-only** downloads.
 *
 * Third parties submit URLs to Seal; Seal owns the queue, yt-dlp process, and files.
 * Raw yt-dlp commands, cookie export, and remote control are intentionally unsupported.
 */
object ExternalDownloadProtocol {
    const val ACTION_DOWNLOAD = "com.junkfood.seal.action.DOWNLOAD"
    const val ACTION_DOWNLOAD_STATUS = "com.junkfood.seal.action.DOWNLOAD_STATUS"

    const val PROTOCOL_VERSION = 1
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_SUPPORTED_VERSION = 1

    // Request extras
    const val EXTRA_PROTOCOL_VERSION = "protocol_version"
    const val EXTRA_URL = "url"
    const val EXTRA_URLS = "urls"
    const val EXTRA_EXTRACT_AUDIO = "extract_audio"
    const val EXTRA_DOWNLOAD_SUBTITLE = "download_subtitle"
    const val EXTRA_AUTO_START = "auto_start"
    const val EXTRA_OPEN_UI = "open_ui"
    const val EXTRA_CALLER_REQUEST_ID = "caller_request_id"

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
}
