package com.chloemlla.seal.integration

import android.util.Log
import com.chloemlla.seal.util.EXTERNAL_ACCEPT_COOKIES
import com.chloemlla.seal.util.EXTERNAL_AUTO_START_ENABLED
import com.chloemlla.seal.util.EXTERNAL_CALLER_WHITELIST
import com.chloemlla.seal.util.EXTERNAL_DELEGATE_ENABLED
import com.chloemlla.seal.util.EXTERNAL_WHITELIST_MODE
import com.chloemlla.seal.util.PreferenceUtil.getBoolean
import com.chloemlla.seal.util.PreferenceUtil.getString

sealed interface ExternalDownloadDecision {
    data class Reject(val errorCode: String, val message: String) : ExternalDownloadDecision

    data class NeedsUi(
        val request: ExternalDownloadRequest,
        val noteErrorCode: String? = null,
    ) : ExternalDownloadDecision

    data class AutoStart(val request: ExternalDownloadRequest) : ExternalDownloadDecision
}

data class ExternalDownloadPolicy(
    val delegateEnabled: Boolean,
    val autoStartEnabled: Boolean,
    val whitelistMode: Boolean,
    val whitelist: Set<String>,
    /** When false, inbound cookie payload is rejected (or stripped if not required). */
    val acceptCookies: Boolean = false,
)

object ExternalDownloadGate {
    private const val TAG = "ExternalDownloadGate"

    fun currentPolicy(): ExternalDownloadPolicy =
        ExternalDownloadPolicy(
            delegateEnabled = EXTERNAL_DELEGATE_ENABLED.getBoolean(),
            autoStartEnabled = EXTERNAL_AUTO_START_ENABLED.getBoolean(),
            whitelistMode = EXTERNAL_WHITELIST_MODE.getBoolean(),
            whitelist = parseWhitelist(EXTERNAL_CALLER_WHITELIST.getString()),
            acceptCookies = EXTERNAL_ACCEPT_COOKIES.getBoolean(),
        )

    fun decide(
        request: ExternalDownloadRequest,
        callerPackage: String?,
        policy: ExternalDownloadPolicy = currentPolicy(),
        rateLimitOk: Boolean = true,
    ): ExternalDownloadDecision {
        if (!policy.delegateEnabled) {
            return ExternalDownloadDecision.Reject(
                ExternalDownloadProtocol.ERROR_DISABLED,
                "External download delegation is disabled in Seal settings",
            )
        }

        if (policy.whitelistMode) {
            val caller = callerPackage?.trim().orEmpty()
            if (caller.isEmpty() || caller !in policy.whitelist) {
                return ExternalDownloadDecision.Reject(
                    ExternalDownloadProtocol.ERROR_CALLER_DENIED,
                    "Caller is not on the external download whitelist",
                )
            }
        }

        if (request.urls.isEmpty()) {
            return ExternalDownloadDecision.Reject(
                ExternalDownloadProtocol.ERROR_INVALID_URL,
                "No valid URL to download",
            )
        }

        if (!rateLimitOk) {
            return ExternalDownloadDecision.Reject(
                ExternalDownloadProtocol.ERROR_QUEUE_REJECTED,
                "Caller exceeded delegate rate limit",
            )
        }

        // Cookie gate: payload present but accept off.
        val cookieHandled = applyCookiePolicy(request, policy)
        if (cookieHandled is CookiePolicyResult.Reject) {
            return ExternalDownloadDecision.Reject(
                cookieHandled.errorCode,
                cookieHandled.message,
            )
        }
        val effectiveRequest =
            (cookieHandled as CookiePolicyResult.Continue).request

        if (effectiveRequest.autoStart) {
            return if (policy.autoStartEnabled) {
                ExternalDownloadDecision.AutoStart(effectiveRequest)
            } else if (effectiveRequest.openUi) {
                ExternalDownloadDecision.NeedsUi(
                    request = effectiveRequest,
                    noteErrorCode = ExternalDownloadProtocol.ERROR_AUTO_START_DENIED,
                )
            } else {
                ExternalDownloadDecision.Reject(
                    ExternalDownloadProtocol.ERROR_AUTO_START_DENIED,
                    "Auto-start is disabled; open_ui=false so UI fallback is unavailable",
                )
            }
        }

        // open_ui=false without auto_start cannot run headless; fall back to UI when possible
        return ExternalDownloadDecision.NeedsUi(effectiveRequest)
    }

    private sealed interface CookiePolicyResult {
        data class Continue(val request: ExternalDownloadRequest) : CookiePolicyResult

        data class Reject(val errorCode: String, val message: String) : CookiePolicyResult
    }

    /**
     * If request has cookie payload and acceptCookies is false:
     * - cookiesRequired → hard reject cookie_denied
     * - else strip cookies and continue (anonymous / Seal-native cookies)
     *
     * Implement brief mentioned hard reject; research/product soft path is preferred so
     * PiliPlus default cookies_required=false still downloads when Seal accept is off.
     */
    private fun applyCookiePolicy(
        request: ExternalDownloadRequest,
        policy: ExternalDownloadPolicy,
    ): CookiePolicyResult {
        if (!request.hasCookiePayload) {
            return CookiePolicyResult.Continue(request)
        }
        if (request.protocolVersion < 2) {
            // Parser already strips for v1; defensive.
            return CookiePolicyResult.Continue(request.clearedCookies())
        }
        if (!policy.acceptCookies) {
            Log.i(
                TAG,
                "cookie payload present but EXTERNAL_ACCEPT_COOKIES=false " +
                    "required=${request.cookiesRequired} mid=${request.cookiesMid}",
            )
            return if (request.cookiesRequired) {
                CookiePolicyResult.Reject(
                    ExternalDownloadProtocol.ERROR_COOKIE_DENIED,
                    "External cookies are disabled in Seal settings",
                )
            } else {
                CookiePolicyResult.Continue(request.clearedCookies())
            }
        }
        return CookiePolicyResult.Continue(request)
    }

    fun parseWhitelist(raw: String): Set<String> =
        raw
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
