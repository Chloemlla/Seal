package com.chloemlla.seal.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoFetchErrorMapperTest {

    @Test
    fun detectsUnableToDownloadJsonMetadata() {
        assertTrue(
            InfoFetchErrorMapper.isJsonMetadataTransportError(
                "[BiliBili] BV1xx: Unable to download JSON metadata: [Errno 103] Software caused connection abort (TransportError)"
            )
        )
    }

    @Test
    fun detectsTransportErrorAndErrno103() {
        assertTrue(InfoFetchErrorMapper.isJsonMetadataTransportError("TransportError"))
        assertTrue(InfoFetchErrorMapper.isJsonMetadataTransportError("Errno 103"))
        assertTrue(
            InfoFetchErrorMapper.isJsonMetadataTransportError("Software caused connection abort")
        )
        assertTrue(InfoFetchErrorMapper.isJsonMetadataTransportError("connection abort"))
    }

    @Test
    fun isCaseInsensitive() {
        assertTrue(
            InfoFetchErrorMapper.isJsonMetadataTransportError(
                "unable to download json metadata"
            )
        )
        assertTrue(InfoFetchErrorMapper.isJsonMetadataTransportError("transporterror"))
    }

    @Test
    fun ignoresUnrelatedErrors() {
        assertFalse(InfoFetchErrorMapper.isJsonMetadataTransportError(null as String?))
        assertFalse(InfoFetchErrorMapper.isJsonMetadataTransportError(""))
        assertFalse(InfoFetchErrorMapper.isJsonMetadataTransportError("   "))
        assertFalse(
            InfoFetchErrorMapper.isJsonMetadataTransportError(
                "ERROR: [youtube] Sign in to confirm you're not a bot"
            )
        )
        assertFalse(
            InfoFetchErrorMapper.isJsonMetadataTransportError(
                "Unable to communicate with SponsorBlock API"
            )
        )
        assertFalse(InfoFetchErrorMapper.isJsonMetadataTransportError("HTTP Error 404: Not Found"))
    }

    @Test
    fun detectsMarkerOnNestedCause() {
        val root =
            RuntimeException(
                "Unable to download JSON metadata: [Errno 103] Software caused connection abort (TransportError)"
            )
        val wrapped = IllegalStateException("info fetch failed", root)
        assertTrue(InfoFetchErrorMapper.isJsonMetadataTransportError(wrapped))
        assertFalse(
            InfoFetchErrorMapper.isJsonMetadataTransportError(
                IllegalStateException("info fetch failed")
            )
        )
        assertFalse(InfoFetchErrorMapper.isJsonMetadataTransportError(null as Throwable?))
    }
}
