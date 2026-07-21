package com.chloemlla.seal.util

/**
 * Pure helper that detects yt-dlp JSON metadata / transport abort failures so callers can
 * surface a localized, actionable message (retry + cookies for Bilibili) without Android
 * resources in unit tests.
 */
object InfoFetchErrorMapper {

    private val TRANSPORT_MARKERS =
        listOf(
            "Unable to download JSON metadata",
            "TransportError",
            "Errno 103",
            "connection abort",
            "Software caused connection abort",
        )

    /**
     * Returns true when [message] looks like a transient JSON-metadata transport failure
     * (e.g. BiliBili errno 103 / TransportError).
     */
    fun isJsonMetadataTransportError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return TRANSPORT_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
    }

    /**
     * Walks [throwable] and its cause chain (cycle-safe) so nested wrapper exceptions still match.
     */
    fun isJsonMetadataTransportError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        var current: Throwable? = throwable
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (isJsonMetadataTransportError(current.message)) return true
            current = current.cause
        }
        return false
    }
}
