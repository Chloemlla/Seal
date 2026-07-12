package com.junkfood.seal.integration

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.Task.DownloadState
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.FileUtil.getFileProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

/**
 * Handles L2 enqueue + L3 directed status broadcasts for external callers.
 * Seal remains the only owner of download execution.
 */
object ExternalDownloadCoordinator {
    private const val TAG = "ExternalDownloadCoord"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watchedTasks = ConcurrentHashMap<String, WatchedTask>()
    private val recentCallerHits = ConcurrentHashMap<String, LongArray>()

    private data class WatchedTask(
        val taskId: String,
        val callerPackage: String?,
        val callerRequestId: String?,
        var job: Job? = null,
    )

    fun isRateLimitOk(callerPackage: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val key = callerPackage?.ifBlank { null } ?: "unknown"
        val windowMs = 60_000L
        val maxHits = 20
        val hits = recentCallerHits[key]?.toMutableList() ?: mutableListOf()
        hits.removeAll { nowMs - it > windowMs }
        if (hits.size >= maxHits) {
            recentCallerHits[key] = hits.toLongArray()
            return false
        }
        hits += nowMs
        recentCallerHits[key] = hits.toLongArray()
        return true
    }

    fun buildPreferences(request: ExternalDownloadRequest): DownloadUtil.DownloadPreferences {
        val base = DownloadUtil.DownloadPreferences.createFromPreferences()
        return base.copy(
            extractAudio = request.extractAudio ?: base.extractAudio,
            downloadSubtitle = request.downloadSubtitle ?: base.downloadSubtitle,
            // External path never injects custom command templates.
        )
    }

    fun enqueue(
        context: Context,
        downloader: DownloaderV2,
        request: ExternalDownloadRequest,
        callerPackage: String?,
    ): EnqueueResult {
        return runCatching {
                val preferences = buildPreferences(request)
                val taskIds = mutableListOf<String>()
                request.urls.forEach { url ->
                    val task = Task(url = url, preferences = preferences)
                    downloader.enqueue(task)
                    taskIds += task.id
                    watchTask(
                        context = context.applicationContext,
                        downloader = downloader,
                        taskId = task.id,
                        callerPackage = callerPackage,
                        callerRequestId = request.callerRequestId,
                    )
                }
                EnqueueResult.Success(taskIds = taskIds)
            }
            .getOrElse {
                Log.e(TAG, "enqueue failed", it)
                EnqueueResult.Failure(
                    errorCode = ExternalDownloadProtocol.ERROR_INTERNAL,
                    message = it.message ?: "Failed to enqueue delegated download",
                )
            }
    }

    fun watchTask(
        context: Context,
        downloader: DownloaderV2,
        taskId: String,
        callerPackage: String?,
        callerRequestId: String?,
    ) {
        if (callerPackage.isNullOrBlank()) return
        val existing = watchedTasks[taskId]
        if (existing != null) return

        val watched =
            WatchedTask(
                taskId = taskId,
                callerPackage = callerPackage,
                callerRequestId = callerRequestId,
            )
        watchedTasks[taskId] = watched

        watched.job =
            scope.launch {
                snapshotFlow { downloader.getTaskStateMap().entries.associate { it.key.id to it.value } }
                    .map { it[taskId] }
                    .distinctUntilChanged()
                    .collect { state ->
                        if (state == null) return@collect
                        when (val downloadState = state.downloadState) {
                            is DownloadState.Completed -> {
                                val contentUri =
                                    downloadState.filePath?.let {
                                        createContentUri(context, it, callerPackage)
                                    }
                                ExternalDownloadStatusReporter.sendStatus(
                                    context = context,
                                    targetPackage = callerPackage,
                                    status = ExternalDownloadProtocol.STATUS_COMPLETED,
                                    errorCode = ExternalDownloadProtocol.ERROR_OK,
                                    taskId = taskId,
                                    callerRequestId = callerRequestId,
                                    contentUri = contentUri,
                                )
                                clearWatch(taskId)
                            }
                            is DownloadState.Error -> {
                                ExternalDownloadStatusReporter.sendStatus(
                                    context = context,
                                    targetPackage = callerPackage,
                                    status = ExternalDownloadProtocol.STATUS_FAILED,
                                    errorCode = ExternalDownloadProtocol.ERROR_DOWNLOAD_FAILED,
                                    errorMessage = downloadState.throwable.message,
                                    taskId = taskId,
                                    callerRequestId = callerRequestId,
                                )
                                clearWatch(taskId)
                            }
                            is DownloadState.Canceled -> {
                                ExternalDownloadStatusReporter.sendStatus(
                                    context = context,
                                    targetPackage = callerPackage,
                                    status = ExternalDownloadProtocol.STATUS_CANCELED,
                                    errorCode = ExternalDownloadProtocol.ERROR_CANCELED,
                                    taskId = taskId,
                                    callerRequestId = callerRequestId,
                                )
                                clearWatch(taskId)
                            }
                            else -> Unit
                        }
                    }
            }
    }

    private fun clearWatch(taskId: String) {
        watchedTasks.remove(taskId)?.job?.cancel()
    }

    fun createContentUri(context: Context, path: String, callerPackage: String?): Uri? {
        return runCatching {
                val fileUri =
                    if (path.startsWith("content:", ignoreCase = true)) {
                        Uri.parse(path)
                    } else {
                        val file = File(path)
                        if (!file.exists()) return null
                        FileProvider.getUriForFile(context, context.getFileProvider(), file)
                    }
                if (!callerPackage.isNullOrBlank()) {
                    context.grantUriPermission(
                        callerPackage,
                        fileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                fileUri
            }
            .onFailure { Log.e(TAG, "createContentUri failed for $path", it) }
            .getOrNull()
    }

    sealed interface EnqueueResult {
        data class Success(val taskIds: List<String>) : EnqueueResult

        data class Failure(val errorCode: String, val message: String) : EnqueueResult
    }
}

object ExternalDownloadStatusReporter {
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
    ) {
        if (targetPackage.isNullOrBlank()) return
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
                putExtra(ExternalDownloadProtocol.EXTRA_CALLER_PACKAGE, targetPackage)
            }
        runCatching { context.sendBroadcast(intent) }
            .onFailure { Log.e("ExternalDownloadStatus", "broadcast failed", it) }
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
