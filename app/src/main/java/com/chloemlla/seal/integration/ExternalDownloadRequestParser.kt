package com.chloemlla.seal.integration

import android.content.Intent
import com.chloemlla.seal.util.findURLsFromString

data class ExternalDownloadRequest(
    val protocolVersion: Int,
    val urls: List<String>,
    val extractAudio: Boolean?,
    val downloadSubtitle: Boolean?,
    val autoStart: Boolean,
    val openUi: Boolean,
    val callerRequestId: String?,
    val sourceAction: String?,
    val isExplicitDelegateAction: Boolean,
    // v2 cookies (null when absent / v1)
    val cookiesFormat: String? = null,
    val cookiesPayload: String? = null,
    val cookiesUri: String? = null,
    val cookiesMid: Long? = null,
    val cookiesDomainHint: String? = null,
    val useCookies: Boolean? = null,
    val cookiesRequired: Boolean = false,
    /** Absolute path after materialize; not from Intent. */
    val taskCookiesPath: String? = null,
    // v2 strip / sections
    val stripSegments: Boolean = false,
    /** Keep ranges in seconds as VideoClip-compatible pairs after parse. */
    val keepSections: List<com.chloemlla.seal.util.VideoClip> = emptyList(),
    val removeSegmentsJson: String? = null,
) {
    val hasCookiePayload: Boolean
        get() =
            !cookiesPayload.isNullOrBlank() ||
                !cookiesUri.isNullOrBlank()

    fun clearedCookies(): ExternalDownloadRequest =
        copy(
            cookiesFormat = null,
            cookiesPayload = null,
            cookiesUri = null,
            cookiesMid = null,
            cookiesDomainHint = null,
            useCookies = null,
            cookiesRequired = false,
            taskCookiesPath = null,
        )

    fun withTaskCookiesPath(path: String?): ExternalDownloadRequest =
        copy(taskCookiesPath = path)
}

sealed interface ExternalDownloadParseResult {
    data class Success(val request: ExternalDownloadRequest) : ExternalDownloadParseResult

    data class Failure(val errorCode: String, val message: String) : ExternalDownloadParseResult
}

object ExternalDownloadRequestParser {
    fun parse(intent: Intent?): ExternalDownloadParseResult {
        if (intent == null) {
            return ExternalDownloadParseResult.Failure(
                ExternalDownloadProtocol.ERROR_INVALID_URL,
                "Intent is null",
            )
        }

        val action = intent.action
        val isExplicit =
            action == ExternalDownloadProtocol.ACTION_DOWNLOAD ||
                action?.endsWith(".action.DOWNLOAD") == true

        val version =
            if (intent.hasExtra(ExternalDownloadProtocol.EXTRA_PROTOCOL_VERSION)) {
                intent.getIntExtra(
                    ExternalDownloadProtocol.EXTRA_PROTOCOL_VERSION,
                    ExternalDownloadProtocol.PROTOCOL_VERSION,
                )
            } else {
                // Missing version: treat as 1 for backward compatibility.
                1
            }

        if (
            version < ExternalDownloadProtocol.MIN_SUPPORTED_VERSION ||
                version > ExternalDownloadProtocol.MAX_SUPPORTED_VERSION
        ) {
            return ExternalDownloadParseResult.Failure(
                ExternalDownloadProtocol.ERROR_UNSUPPORTED_VERSION,
                "Unsupported protocol_version=$version " +
                    "(supported ${ExternalDownloadProtocol.MIN_SUPPORTED_VERSION}" +
                    "..${ExternalDownloadProtocol.MAX_SUPPORTED_VERSION})",
            )
        }

        val urls = extractUrls(intent)
        if (urls.isEmpty()) {
            return ExternalDownloadParseResult.Failure(
                ExternalDownloadProtocol.ERROR_INVALID_URL,
                "No http(s) URL found in intent",
            )
        }

        val extractAudio =
            if (intent.hasExtra(ExternalDownloadProtocol.EXTRA_EXTRACT_AUDIO)) {
                intent.getBooleanExtra(ExternalDownloadProtocol.EXTRA_EXTRACT_AUDIO, false)
            } else null

        val downloadSubtitle =
            if (intent.hasExtra(ExternalDownloadProtocol.EXTRA_DOWNLOAD_SUBTITLE)) {
                intent.getBooleanExtra(ExternalDownloadProtocol.EXTRA_DOWNLOAD_SUBTITLE, false)
            } else null

        val autoStart =
            intent.getBooleanExtra(ExternalDownloadProtocol.EXTRA_AUTO_START, false)
        val openUi = intent.getBooleanExtra(ExternalDownloadProtocol.EXTRA_OPEN_UI, true)
        val callerRequestId =
            intent.getStringExtra(ExternalDownloadProtocol.EXTRA_CALLER_REQUEST_ID)

        // Cookie extras: only apply when protocol_version >= 2.
        // v1 requests ignore cookie fields (defensive, keeps old docs honest).
        val cookieFields =
            if (version >= 2) {
                parseCookieFields(intent)
            } else {
                CookieFields()
            }

        if (cookieFields.sizeFailure != null) {
            return cookieFields.sizeFailure
        }

        val stripSegments =
            intent.getBooleanExtra(ExternalDownloadProtocol.EXTRA_STRIP_SEGMENTS, false)
        val keepSectionsRaw =
            intent.getStringExtra(ExternalDownloadProtocol.EXTRA_KEEP_SECTIONS)
        val keepSections = parseKeepSectionsJson(keepSectionsRaw)
        val removeSegmentsJson =
            intent.getStringExtra(ExternalDownloadProtocol.EXTRA_REMOVE_SEGMENTS)

        return ExternalDownloadParseResult.Success(
            ExternalDownloadRequest(
                protocolVersion = version,
                urls = urls,
                extractAudio = extractAudio,
                downloadSubtitle = downloadSubtitle,
                autoStart = autoStart,
                openUi = openUi,
                callerRequestId = callerRequestId,
                sourceAction = action,
                isExplicitDelegateAction = isExplicit,
                cookiesFormat = cookieFields.format,
                cookiesPayload = cookieFields.payload,
                cookiesUri = cookieFields.uri,
                cookiesMid = cookieFields.mid,
                cookiesDomainHint = cookieFields.domainHint,
                useCookies = cookieFields.useCookies,
                cookiesRequired = cookieFields.required,
                stripSegments = stripSegments,
                keepSections = keepSections,
                removeSegmentsJson = removeSegmentsJson,
            )
        )
    }

    private data class CookieFields(
        val format: String? = null,
        val payload: String? = null,
        val uri: String? = null,
        val mid: Long? = null,
        val domainHint: String? = null,
        val useCookies: Boolean? = null,
        val required: Boolean = false,
        val sizeFailure: ExternalDownloadParseResult.Failure? = null,
    )

    private fun parseCookieFields(intent: Intent): CookieFields {
        val format = intent.getStringExtra(ExternalDownloadProtocol.EXTRA_COOKIES_FORMAT)
        val payload = intent.getStringExtra(ExternalDownloadProtocol.EXTRA_COOKIES)
        val uri = intent.getStringExtra(ExternalDownloadProtocol.EXTRA_COOKIES_URI)
        val domainHint =
            intent.getStringExtra(ExternalDownloadProtocol.EXTRA_COOKIES_DOMAIN_HINT)
        val useCookies =
            if (intent.hasExtra(ExternalDownloadProtocol.EXTRA_USE_COOKIES)) {
                intent.getBooleanExtra(ExternalDownloadProtocol.EXTRA_USE_COOKIES, true)
            } else null
        val required =
            intent.getBooleanExtra(ExternalDownloadProtocol.EXTRA_COOKIES_REQUIRED, false)

        val mid: Long? =
            when {
                intent.hasExtra(ExternalDownloadProtocol.EXTRA_COOKIES_MID) -> {
                    // Accept Long or String
                    val asLong =
                        runCatching {
                                intent.getLongExtra(
                                    ExternalDownloadProtocol.EXTRA_COOKIES_MID,
                                    Long.MIN_VALUE,
                                )
                            }
                            .getOrDefault(Long.MIN_VALUE)
                    if (asLong != Long.MIN_VALUE && asLong != 0L) {
                        asLong
                    } else {
                        intent
                            .getStringExtra(ExternalDownloadProtocol.EXTRA_COOKIES_MID)
                            ?.toLongOrNull()
                    }
                }
                else -> null
            }

        if (!payload.isNullOrEmpty() &&
            payload.length > ExternalDownloadProtocol.MAX_COOKIES_PAYLOAD_CHARS
        ) {
            return CookieFields(
                sizeFailure =
                    ExternalDownloadParseResult.Failure(
                        ExternalDownloadProtocol.ERROR_COOKIE_TOO_LARGE,
                        "cookies payload exceeds size limit",
                    )
            )
        }

        val hasPayload = !payload.isNullOrBlank() || !uri.isNullOrBlank()
        if (!hasPayload) {
            return CookieFields()
        }

        return CookieFields(
            format = format,
            payload = payload,
            uri = uri,
            mid = mid,
            domainHint = domainHint,
            useCookies = useCookies,
            required = required,
        )
    }

    fun extractUrls(intent: Intent): List<String> {
        val collected = linkedSetOf<String>()

        intent.getStringExtra(ExternalDownloadProtocol.EXTRA_URL)?.let { raw ->
            collected += normalizeUrlCandidates(raw)
        }

        intent.getStringArrayExtra(ExternalDownloadProtocol.EXTRA_URLS)?.forEach { raw ->
            collected += normalizeUrlCandidates(raw)
        }

        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            collected += findURLsFromString(text, firstMatchOnly = false)
        }

        intent.dataString?.let { data ->
            if (looksLikeHttpUrl(data)) collected += data.trim()
            else collected += findURLsFromString(data, firstMatchOnly = false)
        }

        return collected.map { it.trim() }.filter { looksLikeHttpUrl(it) }
    }

    private fun normalizeUrlCandidates(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (looksLikeHttpUrl(trimmed)) listOf(trimmed)
        else findURLsFromString(trimmed, firstMatchOnly = false)
    }

    fun looksLikeHttpUrl(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        // host must exist after scheme://
        val withoutScheme = trimmed.substringAfter("://")
        val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotBlank() && host.contains('.')
    }
}
