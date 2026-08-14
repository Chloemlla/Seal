package com.chloemlla.seal.integration

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Encodes directed L3 broadcasts and Activity results for external callers. */
object ExternalDownloadStatusReporter {
    private const val TAG = "ExternalDownloadStatus"

    fun sendStatus(
        context: Context,
        targetPackage: String?,
        status: String,
        errorCode: String,
        errorMessage: String? = null,
        taskId: String? = null,
        taskIds: List<String>? = null,
        callerRequestId: String? = null,
        contentUri: Uri? = null,
        displayName: String? = null,
        mimeType: String? = null,
        stripResult: String? = null,
        stripMessage: String? = null,
        progress: Double? = null,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        title: String? = null,
        quality: String? = null,
        sourceUrl: String? = null,
        extractAudio: Boolean? = null,
    ) {
        if (targetPackage.isNullOrBlank()) {
            Log.w(TAG, "sendStatus skipped: blank target status=$status")
            return
        }
        val intent =
            Intent(ExternalDownloadProtocol.ACTION_DOWNLOAD_STATUS).apply {
                setPackage(targetPackage)
                putExtra(
                    ExternalDownloadProtocol.EXTRA_PROTOCOL_VERSION,
                    ExternalDownloadProtocol.PROTOCOL_VERSION,
                )
                putExtra(ExternalDownloadProtocol.EXTRA_STATUS, status)
                putExtra(ExternalDownloadProtocol.EXTRA_ERROR_CODE, errorCode)
                errorMessage?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_ERROR_MESSAGE, it)
                }
                taskId?.let { putExtra(ExternalDownloadProtocol.EXTRA_TASK_ID, it) }
                taskIds?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_TASK_IDS, it.toTypedArray())
                }
                callerRequestId?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_CALLER_REQUEST_ID, it)
                }
                contentUri?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_CONTENT_URI, it.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                displayName?.let { putExtra(ExternalDownloadProtocol.EXTRA_DISPLAY_NAME, it) }
                mimeType?.let { putExtra(ExternalDownloadProtocol.EXTRA_MIME_TYPE, it) }
                stripResult?.let { putExtra(ExternalDownloadProtocol.EXTRA_STRIP_RESULT, it) }
                stripMessage?.let { putExtra(ExternalDownloadProtocol.EXTRA_STRIP_MESSAGE, it) }
                progress?.let { putExtra(ExternalDownloadProtocol.EXTRA_PROGRESS, it) }
                downloadedBytes?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_DOWNLOADED_BYTES, it)
                }
                totalBytes?.let { putExtra(ExternalDownloadProtocol.EXTRA_TOTAL_BYTES, it) }
                title?.let { putExtra(ExternalDownloadProtocol.EXTRA_TITLE, it) }
                quality?.let { putExtra(ExternalDownloadProtocol.EXTRA_QUALITY, it) }
                sourceUrl?.let { putExtra(ExternalDownloadProtocol.EXTRA_SOURCE_URL, it) }
                extractAudio?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_EXTRACT_AUDIO, it)
                }
                putExtra(ExternalDownloadProtocol.EXTRA_CALLER_PACKAGE, targetPackage)
            }
        runCatching {
                context.sendBroadcast(intent)
                Log.i(
                    TAG,
                    "broadcast status=$status task=$taskId pkg=$targetPackage reqId=$callerRequestId",
                )
            }
            .onFailure { Log.e(TAG, "broadcast failed status=$status", it) }
    }

    fun activityResultBundle(
        status: String,
        errorCode: String,
        errorMessage: String? = null,
        taskIds: List<String> = emptyList(),
        callerRequestId: String? = null,
    ): Intent {
        return Intent().apply {
            putExtra(
                ExternalDownloadProtocol.EXTRA_PROTOCOL_VERSION,
                ExternalDownloadProtocol.PROTOCOL_VERSION,
            )
            putExtra(ExternalDownloadProtocol.EXTRA_STATUS, status)
            putExtra(ExternalDownloadProtocol.EXTRA_ERROR_CODE, errorCode)
            errorMessage?.let { putExtra(ExternalDownloadProtocol.EXTRA_ERROR_MESSAGE, it) }
            if (taskIds.isNotEmpty()) {
                putExtra(ExternalDownloadProtocol.EXTRA_TASK_ID, taskIds.first())
                putExtra(ExternalDownloadProtocol.EXTRA_TASK_IDS, taskIds.toTypedArray())
            }
            callerRequestId?.let {
                putExtra(ExternalDownloadProtocol.EXTRA_CALLER_REQUEST_ID, it)
            }
        }
    }

    fun finishWithResult(
        activity: Activity,
        resultCode: Int,
        status: String,
        errorCode: String,
        errorMessage: String? = null,
        taskIds: List<String> = emptyList(),
        callerRequestId: String? = null,
        callerPackage: String? = null,
        alsoBroadcast: Boolean = true,
    ) {
        val data =
            activityResultBundle(
                status = status,
                errorCode = errorCode,
                errorMessage = errorMessage,
                taskIds = taskIds,
                callerRequestId = callerRequestId,
            )
        activity.setResult(resultCode, data)
        if (alsoBroadcast) {
            sendStatus(
                context = activity,
                targetPackage = callerPackage,
                status = status,
                errorCode = errorCode,
                errorMessage = errorMessage,
                taskId = taskIds.firstOrNull(),
                taskIds = taskIds,
                callerRequestId = callerRequestId,
            )
        }
    }
}
