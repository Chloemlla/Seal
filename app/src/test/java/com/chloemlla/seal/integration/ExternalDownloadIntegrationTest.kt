package com.chloemlla.seal.integration

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun policy(
        delegateEnabled: Boolean = true,
        autoStartEnabled: Boolean = false,
        whitelistMode: Boolean = false,
        whitelist: Set<String> = emptySet(),
        acceptCookies: Boolean = false,
    ) =
        ExternalDownloadPolicy(
            delegateEnabled = delegateEnabled,
            autoStartEnabled = autoStartEnabled,
            whitelistMode = whitelistMode,
            whitelist = whitelist,
            acceptCookies = acceptCookies,
        )

    @Test
    fun disabledRejects() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest,
                callerPackage = "com.example.app",
                policy = policy(delegateEnabled = false),
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
                    policy(
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
                policy = policy(autoStartEnabled = false),
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
                policy = policy(autoStartEnabled = false),
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
                policy = policy(autoStartEnabled = true),
            )
        assertTrue(decision is ExternalDownloadDecision.AutoStart)
    }

    @Test
    fun rateLimitRejects() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest,
                callerPackage = "com.example.app",
                policy = policy(),
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

    @Test
    fun cookiePayloadDeniedWhenAcceptOffAndRequired() {
        val withCookies =
            sampleRequest.copy(
                protocolVersion = 2,
                cookiesFormat = ExternalDownloadProtocol.COOKIES_FORMAT_JSON_MAP,
                cookiesPayload = """{"DedeUserID":"1","bili_jct":"x"}""",
                cookiesRequired = true,
            )
        val decision =
            ExternalDownloadGate.decide(
                request = withCookies,
                callerPackage = "com.example.app",
                policy = policy(acceptCookies = false),
            )
        assertTrue(decision is ExternalDownloadDecision.Reject)
        assertEquals(
            ExternalDownloadProtocol.ERROR_COOKIE_DENIED,
            (decision as ExternalDownloadDecision.Reject).errorCode,
        )
    }

    @Test
    fun cookiePayloadStrippedWhenAcceptOffAndNotRequired() {
        val withCookies =
            sampleRequest.copy(
                protocolVersion = 2,
                cookiesFormat = ExternalDownloadProtocol.COOKIES_FORMAT_JSON_MAP,
                cookiesPayload = """{"DedeUserID":"1","bili_jct":"x"}""",
                cookiesRequired = false,
                autoStart = true,
            )
        val decision =
            ExternalDownloadGate.decide(
                request = withCookies,
                callerPackage = "com.example.app",
                policy = policy(acceptCookies = false, autoStartEnabled = true),
            )
        assertTrue(decision is ExternalDownloadDecision.AutoStart)
        val req = (decision as ExternalDownloadDecision.AutoStart).request
        assertFalse(req.hasCookiePayload)
    }

    @Test
    fun cookiePayloadAllowedWhenAcceptOn() {
        val withCookies =
            sampleRequest.copy(
                protocolVersion = 2,
                cookiesFormat = ExternalDownloadProtocol.COOKIES_FORMAT_JSON_MAP,
                cookiesPayload = """{"DedeUserID":"1","bili_jct":"x"}""",
                autoStart = true,
            )
        val decision =
            ExternalDownloadGate.decide(
                request = withCookies,
                callerPackage = "com.example.app",
                policy = policy(autoStartEnabled = true, acceptCookies = true),
            )
        assertTrue(decision is ExternalDownloadDecision.AutoStart)
        val req = (decision as ExternalDownloadDecision.AutoStart).request
        assertTrue(req.hasCookiePayload)
    }

    @Test
    fun noCookieRequestUnaffectedWhenAcceptOff() {
        val decision =
            ExternalDownloadGate.decide(
                request = sampleRequest,
                callerPackage = "com.example.app",
                policy = policy(acceptCookies = false, autoStartEnabled = true),
            )
        // openUi path
        assertTrue(decision is ExternalDownloadDecision.NeedsUi)
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
        assertEquals(2, ExternalDownloadProtocol.PROTOCOL_VERSION)
        assertEquals(1, ExternalDownloadProtocol.MIN_SUPPORTED_VERSION)
        assertEquals(2, ExternalDownloadProtocol.MAX_SUPPORTED_VERSION)
        assertEquals("app_busy", ExternalDownloadProtocol.ERROR_APP_BUSY)
        assertEquals("com.chloemlla.seal.action.DOWNLOAD", ExternalDownloadProtocol.ACTION_DOWNLOAD)
        assertEquals(
            "com.chloemlla.seal.action.DOWNLOAD_STATUS",
            ExternalDownloadProtocol.ACTION_DOWNLOAD_STATUS,
        )
    }

    @Test
    fun cookieExtrasKeysAreStable() {
        assertEquals("cookies_format", ExternalDownloadProtocol.EXTRA_COOKIES_FORMAT)
        assertEquals("cookies", ExternalDownloadProtocol.EXTRA_COOKIES)
        assertEquals("cookies_uri", ExternalDownloadProtocol.EXTRA_COOKIES_URI)
        assertEquals("cookies_mid", ExternalDownloadProtocol.EXTRA_COOKIES_MID)
        assertEquals("use_cookies", ExternalDownloadProtocol.EXTRA_USE_COOKIES)
        assertEquals("keep_sections", ExternalDownloadProtocol.EXTRA_KEEP_SECTIONS)
        assertEquals("strip_segments", ExternalDownloadProtocol.EXTRA_STRIP_SEGMENTS)
        assertEquals("cookie_denied", ExternalDownloadProtocol.ERROR_COOKIE_DENIED)
        assertEquals("cookie_invalid", ExternalDownloadProtocol.ERROR_COOKIE_INVALID)
        assertEquals("cookie_too_large", ExternalDownloadProtocol.ERROR_COOKIE_TOO_LARGE)
        assertEquals(256 * 1024, ExternalDownloadProtocol.MAX_COOKIES_PAYLOAD_CHARS)
    }
}

class ExternalCookieMaterializerTest {
    @Test
    fun jsonMapToNetscapeUsesBilibiliDomain() {
        val body =
            ExternalCookieMaterializer.jsonMapToNetscape(
                """{"DedeUserID":"12345","bili_jct":"token","SESSDATA":"sess"}""",
                ".bilibili.com",
            )
        assertTrue(body != null)
        assertTrue(body!!.contains("DedeUserID"))
        assertTrue(body.contains("12345"))
        assertTrue(body.contains(".bilibili.com"))
        assertTrue(body.contains("bili_jct"))
        // Tab-separated Netscape
        assertTrue(body.lines().any { it.split('\t').size >= 7 })
    }

    @Test
    fun jsonMapRejectsTabInValue() {
        val body =
            ExternalCookieMaterializer.jsonMapToNetscape(
                """{"bad":"a\tb"}""",
                ".bilibili.com",
            )
        assertNull(body)
    }

    @Test
    fun nameValueToNetscapeParsesHeader() {
        val body =
            ExternalCookieMaterializer.nameValueToNetscape(
                "DedeUserID=1; bili_jct=x",
                ".bilibili.com",
            )
        assertTrue(body.contains("DedeUserID"))
        assertTrue(body.contains("bili_jct"))
    }

    @Test
    fun keepSectionsJsonParsesSeconds() {
        val clips =
            parseKeepSectionsJson(
                """[{"start":10.5,"end":20},{"start":30,"end":40}]""",
            )
        assertEquals(2, clips.size)
        // start floors, end ceils
        assertEquals(10, clips[0].start)
        assertEquals(20, clips[0].end)
        assertEquals(30, clips[1].start)
        assertEquals(40, clips[1].end)
    }

    @Test
    fun keepSectionsJsonCeilEndFloorStart() {
        val clips =
            parseKeepSectionsJson("""[{"start":1.2,"end":10.1}]""")
        assertEquals(1, clips.size)
        assertEquals(1, clips[0].start)
        assertEquals(11, clips[0].end)
    }

    @Test
    fun keepSectionsJsonSkipsInvalid() {
        val clips = parseKeepSectionsJson("""[{"start":50,"end":10},{"foo":1}]""")
        assertTrue(clips.isEmpty())
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

    @Test
    fun beginSessionStoresTaskCookiesPath() {
        ExternalDownloadCoordinator.beginExternalSession(
            callerPackage = "com.example.caller",
            callerRequestId = "req",
            taskCookiesPath = "/cache/external_cookies/req.txt",
            cookiesMid = 42L,
        )
        val session = ExternalDownloadCoordinator.currentSession()
        assertEquals("/cache/external_cookies/req.txt", session!!.taskCookiesPath)
        assertEquals(42L, session.cookiesMid)
    }
}
