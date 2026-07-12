package com.junkfood.seal.integration

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.junkfood.seal.download.DownloaderV2
import org.koin.core.context.GlobalContext

/**
 * Shared entry for exported activities receiving external download intents.
 */
object ExternalDownloadEntry {
    private const val TAG = "ExternalDownloadEntry"

    data class AcceptedUi(
        val request: ExternalDownloadRequest,
        val callerPackage: String?,
        val noteErrorCode: String? = null,
    )

    sealed interface HandleResult {
        data object RejectedAndFinished : HandleResult

        data class ShowUi(val accepted: AcceptedUi) : HandleResult

        data object AutoStartedAndFinished : HandleResult

        data object NotExternal : HandleResult
    }

    fun resolveCallerPackage(activity: Activity, intent: Intent?): String? {
        return activity.callingPackage
            ?: intent?.getStringExtra(ExternalDownloadProtocol.EXTRA_CALLER_PACKAGE)
            ?: intent?.`package`
    }

    fun handle(
        activity: Activity,
        intent: Intent?,
        downloader: DownloaderV2? = GlobalContext.getOrNull()?.get<DownloaderV2>(),
        finishOnReject: Boolean = true,
        finishOnAutoStart: Boolean = true,
    ): HandleResult {
        if (intent == null) return HandleResult.NotExternal

        val isDelegateSurface =
            intent.action == ExternalDownloadProtocol.ACTION_DOWNLOAD ||
                intent.action?.endsWith(".action.DOWNLOAD") == true ||
                intent.action == Intent.ACTION_SEND ||
                intent.action == Intent.ACTION_VIEW ||
                intent.hasExtra(ExternalDownloadProtocol.EXTRA_URL) ||
                intent.hasExtra(ExternalDownloadProtocol.EXTRA_URLS)

        if (!isDelegateSurface) return HandleResult.NotExternal

        val callerPackage = resolveCallerPackage(activity, intent)
        val parseResult = ExternalDownloadRequestParser.parse(intent)
        if (parseResult is ExternalDownloadParseResult.Failure) {
            reject(
                activity = activity,
                callerPackage = callerPackage,
                callerRequestId = intent.getStringExtra(ExternalDownloadProtocol.EXTRA_CALLER_REQUEST_ID),
                errorCode = parseResult.errorCode,
                message = parseResult.message,
                finish = finishOnReject,
            )
            return HandleResult.RejectedAndFinished
        }

        val request = (parseResult as ExternalDownloadParseResult.Success).request
        val rateLimitOk = ExternalDownloadCoordinator.isRateLimitOk(callerPackage)
        when (
            val decision =
                ExternalDownloadGate.decide(
                    request = request,
                    callerPackage = callerPackage,
                    rateLimitOk = rateLimitOk,
                )
        ) {
            is ExternalDownloadDecision.Reject -> {
                reject(
                    activity = activity,
                    callerPackage = callerPackage,
                    callerRequestId = request.callerRequestId,
                    errorCode = decision.errorCode,
                    message = decision.message,
                    finish = finishOnReject,
                )
                return HandleResult.RejectedAndFinished
            }
            is ExternalDownloadDecision.NeedsUi -> {
                // Soft notice for auto_start denied — still open configure UI.
                if (decision.noteErrorCode != null && !callerPackage.isNullOrBlank()) {
                    ExternalDownloadStatusReporter.sendStatus(
                        context = activity,
                        targetPackage = callerPackage,
                        status = ExternalDownloadProtocol.STATUS_NEEDS_UI,
                        errorCode = decision.noteErrorCode,
                        taskIds = emptyList(),
                        callerRequestId = request.callerRequestId,
                    )
                }
                return HandleResult.ShowUi(
                    AcceptedUi(
                        request = decision.request,
                        callerPackage = callerPackage,
                        noteErrorCode = decision.noteErrorCode,
                    )
                )
            }
            is ExternalDownloadDecision.AutoStart -> {
                val dl =
                    downloader
                        ?: run {
                            reject(
                                activity = activity,
                                callerPackage = callerPackage,
                                callerRequestId = request.callerRequestId,
                                errorCode = ExternalDownloadProtocol.ERROR_INTERNAL,
                                message = "Downloader unavailable",
                                finish = finishOnReject,
                            )
                            return HandleResult.RejectedAndFinished
                        }
                when (
                    val enqueueResult =
                        ExternalDownloadCoordinator.enqueue(
                            context = activity,
                            downloader = dl,
                            request = decision.request,
                            callerPackage = callerPackage,
                        )
                ) {
                    is ExternalDownloadCoordinator.EnqueueResult.Failure -> {
                        reject(
                            activity = activity,
                            callerPackage = callerPackage,
                            callerRequestId = request.callerRequestId,
                            errorCode = enqueueResult.errorCode,
                            message = enqueueResult.message,
                            finish = finishOnReject,
                        )
                        return HandleResult.RejectedAndFinished
                    }
                    is ExternalDownloadCoordinator.EnqueueResult.Success -> {
                        ExternalDownloadStatusReporter.finishWithResult(
                            activity = activity,
                            resultCode = Activity.RESULT_OK,
                            status = ExternalDownloadProtocol.STATUS_ACCEPTED,
                            errorCode = ExternalDownloadProtocol.ERROR_OK,
                            taskIds = enqueueResult.taskIds,
                            callerRequestId = request.callerRequestId,
                            callerPackage = callerPackage,
                        )
                        if (finishOnAutoStart) {
                            activity.finish()
                        }
                        return HandleResult.AutoStartedAndFinished
                    }
                }
            }
        }
    }

    private fun reject(
        activity: Activity,
        callerPackage: String?,
        callerRequestId: String?,
        errorCode: String,
        message: String,
        finish: Boolean,
    ) {
        Log.w(TAG, "reject code=$errorCode msg=$message caller=$callerPackage")
        ExternalDownloadStatusReporter.finishWithResult(
            activity = activity,
            resultCode = Activity.RESULT_CANCELED,
            status = ExternalDownloadProtocol.STATUS_REJECTED,
            errorCode = errorCode,
            errorMessage = message,
            callerRequestId = callerRequestId,
            callerPackage = callerPackage,
        )
        if (finish) activity.finish()
    }
}
