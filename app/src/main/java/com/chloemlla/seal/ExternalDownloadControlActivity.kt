package com.chloemlla.seal

import android.app.Activity
import android.os.Bundle
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.integration.ExternalDownloadControlAction
import com.chloemlla.seal.integration.ExternalDownloadControlResult
import com.chloemlla.seal.integration.ExternalDownloadOwnershipStore
import com.chloemlla.seal.integration.ExternalDownloadProtocol
import com.chloemlla.seal.integration.ExternalDownloadStatusReporter
import com.chloemlla.seal.integration.ExternalDownloadTaskController
import com.chloemlla.seal.integration.validateExternalDownloadOwnership
import org.koin.core.context.GlobalContext

/** Narrow startActivityForResult surface for controlling caller-owned external tasks. */
class ExternalDownloadControlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleControlRequest()
        finish()
    }

    private fun handleControlRequest() {
        // Never trust caller_package extras: only startActivityForResult establishes this value.
        val caller = callingPackage?.trim().orEmpty()
        if (caller.isEmpty()) {
            finishError(
                ExternalDownloadProtocol.ERROR_CALLER_DENIED,
                "Task control requires startActivityForResult from an identified caller",
            )
            return
        }
        val action =
            ExternalDownloadControlAction.parse(
                intent?.getStringExtra(ExternalDownloadProtocol.EXTRA_CONTROL_ACTION)
            )
        if (action == null) {
            finishError(
                ExternalDownloadProtocol.ERROR_UNSUPPORTED_ACTION,
                "Unsupported external task action",
            )
            return
        }
        val taskId = intent?.getStringExtra(ExternalDownloadProtocol.EXTRA_TASK_ID)?.trim()
        val requestId =
            intent?.getStringExtra(ExternalDownloadProtocol.EXTRA_CALLER_REQUEST_ID)?.trim()
        val ownership =
            taskId
                ?.takeIf { it.isNotEmpty() }
                ?.let { ExternalDownloadOwnershipStore.get(this, it) }
                ?: requestId
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { ExternalDownloadOwnershipStore.findUniqueByRequestId(this, it) }
        if (ownership == null) {
            finishError(
                ExternalDownloadProtocol.ERROR_TASK_NOT_FOUND,
                "External download task was not found",
            )
            return
        }
        val ownershipError =
            validateExternalDownloadOwnership(
                callingPackage = caller,
                requestedRequestId = requestId,
                ownership = ownership,
            )
        if (ownershipError != null) {
            finishError(ownershipError, "Caller does not own this external download task")
            return
        }
        val downloader = runCatching { GlobalContext.get().get<DownloaderV2>() }.getOrNull()
        if (downloader == null) {
            finishError(
                ExternalDownloadProtocol.ERROR_TASK_NOT_FOUND,
                "Seal download queue is unavailable",
            )
            return
        }
        when (
            val result =
                ExternalDownloadTaskController.perform(
                    context = applicationContext,
                    downloader = downloader,
                    ownership = ownership,
                    action = action,
                )
        ) {
            is ExternalDownloadControlResult.Failure ->
                finishError(result.errorCode, result.message, ownership.taskId, requestId)
            is ExternalDownloadControlResult.Success -> {
                if (result.status == ExternalDownloadProtocol.STATUS_CANCELED) {
                    ExternalDownloadStatusReporter.sendStatus(
                        context = applicationContext,
                        targetPackage = result.ownership.callerPackage,
                        status = ExternalDownloadProtocol.STATUS_CANCELED,
                        errorCode = ExternalDownloadProtocol.ERROR_CANCELED,
                        taskId = result.ownership.taskId,
                        callerRequestId = result.ownership.callerRequestId,
                        sourceUrl = result.ownership.sourceUrl,
                        extractAudio = result.ownership.extractAudio,
                    )
                }
                setResult(
                    RESULT_OK,
                    ExternalDownloadStatusReporter.activityResultBundle(
                        status = result.status,
                        errorCode = ExternalDownloadProtocol.ERROR_OK,
                        taskIds = listOf(result.ownership.taskId),
                        callerRequestId = result.ownership.callerRequestId,
                    ),
                )
            }
        }
    }

    private fun finishError(
        errorCode: String,
        message: String,
        taskId: String? = null,
        callerRequestId: String? = null,
    ) {
        setResult(
            RESULT_CANCELED,
            ExternalDownloadStatusReporter.activityResultBundle(
                status = ExternalDownloadProtocol.STATUS_REJECTED,
                errorCode = errorCode,
                errorMessage = message,
                taskIds = taskId?.let(::listOf).orEmpty(),
                callerRequestId = callerRequestId,
            ),
        )
    }
}
