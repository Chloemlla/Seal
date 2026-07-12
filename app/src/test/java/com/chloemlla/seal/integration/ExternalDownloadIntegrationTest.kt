package com.chloemlla.seal.integration

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExternalDownloadGateTest {
    private val sampleRequest =
        ExternalDownloadRequest(
            protocolVersion = ExternalDownloadProtocol.PROTOCOL_VERSION,
            urls = listOf("https://example.com/watch?v=1"),
            extractAudio = null,
            downloadSubtitle = null,
            autoStart = false,
            openUi = true,
            callerRequestId = "req-1",
            sourceAction = ExternalDownloadProtocol.ACTION_DOWNLOAD,
            isExplicitDelegateAction = true,
        )

    @Test
    fun disabledRejects() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest,
                callerPackage = "com.example.app",
                policy =
                    ExternalDownloadPolicy(
                        delegateEnabled = false,
                        autoStartEnabled = false,
                        whitelistMode = false,
                        whitelist = emptySet(),
                    ),
            )
        assertTrue(decision is ExternalDownloadDecision.Reject)
        assertEquals(
            ExternalDownloadProtocol.ERROR_DISABLED,
            (decision as ExternalDownloadDecision.Reject).errorCode,
        )
    }

    @Test
    fun whitelistDeniesUnknownCaller() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest,
                callerPackage = "com.other.app",
                policy =
                    ExternalDownloadPolicy(
                        delegateEnabled = true,
                        autoStartEnabled = true,
                        whitelistMode = true,
                        whitelist = setOf("com.example.app"),
                    ),
            )
        assertTrue(decision is ExternalDownloadDecision.Reject)
        assertEquals(
            ExternalDownloadProtocol.ERROR_CALLER_DENIED,
            (decision as ExternalDownloadDecision.Reject).errorCode,
        )
    }

    @Test
    fun autoStartWithoutPermissionFallsBackToUi() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest.copy(autoStart = true, openUi = true),
                callerPackage = "com.example.app",
                policy =
                    ExternalDownloadPolicy(
                        delegateEnabled = true,
                        autoStartEnabled = false,
                        whitelistMode = false,
                        whitelist = emptySet(),
                    ),
            )
        assertTrue(decision is ExternalDownloadDecision.NeedsUi)
        assertEquals(
            ExternalDownloadProtocol.ERROR_AUTO_START_DENIED,
            (decision as ExternalDownloadDecision.NeedsUi).noteErrorCode,
        )
    }

    @Test
    fun autoStartDeniedWithoutUiRejects() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest.copy(autoStart = true, openUi = false),
                callerPackage = "com.example.app",
                policy =
                    ExternalDownloadPolicy(
                        delegateEnabled = true,
                        autoStartEnabled = false,
                        whitelistMode = false,
                        whitelist = emptySet(),
                    ),
            )
        assertTrue(decision is ExternalDownloadDecision.Reject)
        assertEquals(
            ExternalDownloadProtocol.ERROR_AUTO_START_DENIED,
            (decision as ExternalDownloadDecision.Reject).errorCode,
        )
    }

    @Test
    fun autoStartWithPermissionEnqueues() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest.copy(autoStart = true),
                callerPackage = "com.example.app",
                policy =
                    ExternalDownloadPolicy(
                        delegateEnabled = true,
                        autoStartEnabled = true,
                        whitelistMode = false,
                        whitelist = emptySet(),
                    ),
            )
        assertTrue(decision is ExternalDownloadDecision.AutoStart)
    }

    @Test
    fun rateLimitRejects() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest,
                callerPackage = "com.example.app",
                policy =
                    ExternalDownloadPolicy(
                        delegateEnabled = true,
                        autoStartEnabled = false,
                        whitelistMode = false,
                        whitelist = emptySet(),
                    ),
                rateLimitOk = false,
            )
        assertTrue(decision is ExternalDownloadDecision.Reject)
        assertEquals(
            ExternalDownloadProtocol.ERROR_QUEUE_REJECTED,
            (decision as ExternalDownloadDecision.Reject).errorCode,
        )
    }

    @Test
    fun parseWhitelistSplitsPackages() {
        val parsed = ExternalDownloadGate.parseWhitelist("com.a.app\ncom.b.app, com.c.app;com.d.app")
        assertEquals(setOf("com.a.app", "com.b.app", "com.c.app", "com.d.app"), parsed)
    }
}

class ExternalDownloadRequestParserTest {
    @Test
    fun looksLikeHttpUrlAcceptsHttps() {
        assertTrue(ExternalDownloadRequestParser.looksLikeHttpUrl("https://youtu.be/abc"))
        assertTrue(ExternalDownloadRequestParser.looksLikeHttpUrl("http://example.com/x"))
        assertFalse(ExternalDownloadRequestParser.looksLikeHttpUrl("file:///tmp/a"))
        assertFalse(ExternalDownloadRequestParser.looksLikeHttpUrl("not-a-url"))
        assertFalse(ExternalDownloadRequestParser.looksLikeHttpUrl("https://"))
    }

    @Test
    fun protocolVersionBoundsMatchConstants() {
        assertEquals(1, ExternalDownloadProtocol.PROTOCOL_VERSION)
        assertEquals(1, ExternalDownloadProtocol.MIN_SUPPORTED_VERSION)
        assertEquals(1, ExternalDownloadProtocol.MAX_SUPPORTED_VERSION)
        assertEquals("com.chloemlla.seal.action.DOWNLOAD", ExternalDownloadProtocol.ACTION_DOWNLOAD)
        assertEquals(
            "com.chloemlla.seal.action.DOWNLOAD_STATUS",
            ExternalDownloadProtocol.ACTION_DOWNLOAD_STATUS,
        )
    }
}

class ExternalDownloadSessionTest {
    @Before
    fun clearSession() {
        ExternalDownloadCoordinator.endExternalSession()
    }

    @After
    fun tearDown() {
        ExternalDownloadCoordinator.endExternalSession()
    }

    @Test
    fun beginSessionStoresCallerAndRequestId() {
        ExternalDownloadCoordinator.beginExternalSession(
            callerPackage = "com.example.caller",
            callerRequestId = "req-42",
        )
        val session = ExternalDownloadCoordinator.currentSession()
        assertTrue(session != null)
        assertEquals("com.example.caller", session!!.callerPackage)
        assertEquals("req-42", session.callerRequestId)
        assertFalse(session.enqueuedDuringSession)
    }

    @Test
    fun beginSessionWithBlankPackageIsNoOp() {
        ExternalDownloadCoordinator.beginExternalSession(
            callerPackage = "  ",
            callerRequestId = "req",
        )
        assertTrue(ExternalDownloadCoordinator.currentSession() == null)
    }

    @Test
    fun beginSessionWithNullPackageIsNoOp() {
        ExternalDownloadCoordinator.beginExternalSession(
            callerPackage = null,
            callerRequestId = "req",
        )
        assertTrue(ExternalDownloadCoordinator.currentSession() == null)
    }

    @Test
    fun endSessionClearsCurrentSession() {
        ExternalDownloadCoordinator.beginExternalSession(
            callerPackage = "com.example.caller",
            callerRequestId = "req",
        )
        ExternalDownloadCoordinator.endExternalSession()
        assertTrue(ExternalDownloadCoordinator.currentSession() == null)
    }

    @Test
    fun replaceSessionUpdatesCaller() {
        ExternalDownloadCoordinator.beginExternalSession(
            callerPackage = "com.a",
            callerRequestId = "1",
        )
        ExternalDownloadCoordinator.beginExternalSession(
            callerPackage = "com.b",
            callerRequestId = "2",
        )
        val session = ExternalDownloadCoordinator.currentSession()
        assertEquals("com.b", session!!.callerPackage)
        assertEquals("2", session.callerRequestId)
    }
}
