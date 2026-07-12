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
)

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
                ExternalDownloadProtocol.PROTOCOL_VERSION
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
            )
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

