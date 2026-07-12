package com.chloemlla.seal.integration

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
)

object ExternalDownloadGate {
    fun currentPolicy(): ExternalDownloadPolicy =
        ExternalDownloadPolicy(
            delegateEnabled = EXTERNAL_DELEGATE_ENABLED.getBoolean(),
            autoStartEnabled = EXTERNAL_AUTO_START_ENABLED.getBoolean(),
            whitelistMode = EXTERNAL_WHITELIST_MODE.getBoolean(),
            whitelist = parseWhitelist(EXTERNAL_CALLER_WHITELIST.getString()),
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

        if (request.autoStart) {
            return if (policy.autoStartEnabled) {
                ExternalDownloadDecision.AutoStart(request)
            } else if (request.openUi) {
                ExternalDownloadDecision.NeedsUi(
                    request = request,
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
        return ExternalDownloadDecision.NeedsUi(request)
    }

    fun parseWhitelist(raw: String): Set<String> =
        raw
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
