package com.chloemlla.seal.integration

import android.content.Context
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.Task
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Watches owned DownloaderV2 tasks and emits real queue/progress metadata. */
object ExternalDownloadTaskMonitor {
    private const val TAG = "ExternalTaskMonitor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watchers = ConcurrentHashMap<String, Watcher>()

    private data class Observation(val task: Task, val state: Task.State)

    private data class Watcher(
        val taskId: String,
        @Volatile var lastFingerprint: String? = null,
        var job: Job? = null,
    )

    fun registerAndWatch(
        context: Context,
        downloader: DownloaderV2,
        task: Task,
        callerPackage: String,
        callerRequestId: String?,
        taskCookiesPath: String?,
        stripRequested: Boolean,
    ) {
        val ownership =
            ExternalDownloadOwnership(
                taskId = task.id,
                callerPackage = callerPackage,
                callerRequestId = callerRequestId,
                sourceUrl = task.url,
                extractAudio = task.preferences.extractAudio,
                taskCookiesPath = taskCookiesPath,
                stripRequested = stripRequested,
            )
        ExternalDownloadOwnershipStore.put(context, ownership)
        watch(context.applicationContext, downloader, ownership.taskId)
    }

    fun restoreOwnedTasks(context: Context, downloader: DownloaderV2) {
        val records = ExternalDownloadOwnershipStore.monitorable(context)
        records.forEach { ownership ->
            val recovered = recoverExternalDownloadOwnershipAfterRestart(ownership)
            if (recovered != ownership) {
                ExternalDownloadOwnershipStore.update(context, ownership.taskId) { recovered }
            }
            watch(context.applicationContext, downloader, ownership.taskId)
        }
        Log.i(TAG, "restored ${records.size} external task monitor(s)")
    }

    fun stop(taskId: String) {
        watchers.remove(taskId)?.job?.cancel()
    }

    private fun watch(context: Context, downloader: DownloaderV2, taskId: String) {
        if (watchers.containsKey(taskId)) return
        val watcher = Watcher(taskId)
        watchers[taskId] = watcher
        watcher.job =
            scope.launch {
                snapshotFlow {
                        downloader
                            .getTaskStateMap()
                            .entries
                            .firstOrNull { (task, _) -> task.id == taskId }
                            ?.let { (task, state) -> Observation(task, state) }
                    }
                    .collect { observation ->
                        if (observation == null) return@collect
                        val ownership =
                            ExternalDownloadOwnershipStore.get(context, taskId)
                                ?: return@collect
                        emitObservation(
                            context = context,
                            watcher = watcher,
                            observation = observation,
                            ownership = ownership,
                        )
                    }
            }
    }

    private fun emitObservation(
        context: Context,
        watcher: Watcher,
        observation: Observation,
        ownership: ExternalDownloadOwnership,
    ) {
        var snapshot =
            ExternalDownloadTaskSnapshotFactory.create(
                task = observation.task,
                state = observation.state,
                ownership = ownership,
            )
        var output: ExternalDownloadOutput? = null
        if (observation.state.downloadState is Task.DownloadState.Completed) {
            output =
                ExternalDownloadTaskOutput.resolve(
                    context = context,
                    path = snapshot.filePath,
                    viewTitle = snapshot.title,
                    callerPackage = ownership.callerPackage,
                )
            if (snapshot.status == ExternalDownloadProtocol.STATUS_COMPLETED) {
                val actualBytes = output.totalBytes ?: snapshot.totalBytes
                snapshot =
                    snapshot.copy(
                        downloadedBytes = actualBytes,
                        totalBytes = actualBytes,
                    )
            }
        }

        val fingerprint = snapshot.fingerprint(output)
        if (watcher.lastFingerprint == fingerprint) return
        watcher.lastFingerprint = fingerprint

        ExternalDownloadStatusReporter.sendStatus(
            context = context,
            targetPackage = ownership.callerPackage,
            status = snapshot.status,
            errorCode = snapshot.errorCode,
            errorMessage = snapshot.errorMessage,
            taskId = ownership.taskId,
            callerRequestId = ownership.callerRequestId,
            contentUri = output?.contentUri,
            displayName = output?.displayName,
            mimeType = output?.mimeType,
            stripResult = snapshot.stripResult,
            stripMessage = snapshot.stripMessage,
            progress = snapshot.progress,
            downloadedBytes = snapshot.downloadedBytes,
            totalBytes = snapshot.totalBytes,
            title = snapshot.title,
            quality = snapshot.quality,
            sourceUrl = snapshot.sourceUrl,
            extractAudio = snapshot.extractAudio,
        )

        when (snapshot.status) {
            ExternalDownloadProtocol.STATUS_COMPLETED -> {
                ExternalDownloadTaskOutput.deleteCookies(context, ownership)
                ExternalDownloadOwnershipStore.update(context, ownership.taskId) {
                    it.copy(
                        lastStatus = ExternalDownloadProtocol.STATUS_COMPLETED,
                        taskCookiesPath = null,
                        paused = false,
                    )
                }
                stop(ownership.taskId)
            }
            ExternalDownloadProtocol.STATUS_CANCELED -> {
                ExternalDownloadTaskOutput.deleteCookies(context, ownership)
                ExternalDownloadOwnershipStore.remove(context, ownership.taskId)
                stop(ownership.taskId)
            }
            else -> {
                if (ownership.lastStatus != snapshot.status) {
                    ExternalDownloadOwnershipStore.update(context, ownership.taskId) {
                        it.copy(
                            lastStatus = snapshot.status,
                            paused =
                                if (snapshot.status == ExternalDownloadProtocol.STATUS_PAUSED) {
                                    true
                                } else {
                                    it.paused
                                },
                        )
                    }
                }
                if (observation.state.downloadState is Task.DownloadState.Completed) {
                    // A completed strip without Applied is a terminal protocol failure.
                    ExternalDownloadTaskOutput.deleteCookies(context, ownership)
                    ExternalDownloadOwnershipStore.update(context, ownership.taskId) {
                        it.copy(taskCookiesPath = null, paused = false)
                    }
                    stop(ownership.taskId)
                }
            }
        }
    }

    private fun ExternalDownloadTaskSnapshot.fingerprint(
        output: ExternalDownloadOutput?,
    ): String {
        return listOf(
                status,
                errorCode,
                errorMessage,
                progress,
                downloadedBytes,
                totalBytes,
                title,
                quality,
                output?.contentUri,
                output?.totalBytes,
                stripResult,
                stripMessage,
            )
            .joinToString("|")
    }
}
