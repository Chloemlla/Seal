package com.chloemlla.seal.integration

import com.chloemlla.seal.download.StripResult
import com.chloemlla.seal.download.Task
import kotlin.math.roundToLong

internal data class ExternalDownloadTaskSnapshot(
    val status: String,
    val errorCode: String,
    val errorMessage: String? = null,
    val progress: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val title: String? = null,
    val quality: String? = null,
    val sourceUrl: String,
    val extractAudio: Boolean,
    val filePath: String? = null,
    val stripResult: String? = null,
    val stripMessage: String? = null,
)

internal object ExternalDownloadTaskSnapshotFactory {
    fun create(
        task: Task,
        state: Task.State,
        ownership: ExternalDownloadOwnership,
    ): ExternalDownloadTaskSnapshot {
        val totalBytes = resolveTotalBytes(state)
        val title =
            state.viewState.title.trim().takeIf { it.isNotEmpty() }
                ?: state.videoInfo?.title?.trim()?.takeIf { it.isNotEmpty() }
        val quality = resolveQuality(task, state)
        val base =
            ExternalDownloadTaskSnapshot(
                status = ExternalDownloadProtocol.STATUS_WAITING,
                errorCode = ExternalDownloadProtocol.ERROR_OK,
                progress = 0.0,
                downloadedBytes = if (totalBytes != null) 0L else null,
                totalBytes = totalBytes,
                title = title,
                quality = quality,
                sourceUrl = task.url.ifBlank { ownership.sourceUrl },
                extractAudio = task.preferences.extractAudio,
            )
        return when (val downloadState = state.downloadState) {
            Task.DownloadState.Idle,
            Task.DownloadState.ReadyWithInfo -> base
            is Task.DownloadState.FetchingInfo ->
                base.copy(
                    status = ExternalDownloadProtocol.STATUS_DOWNLOADING,
                    progress = null,
                    downloadedBytes = null,
                )
            is Task.DownloadState.Running -> {
                val progress = normalizeProgress(downloadState.progress)
                base.copy(
                    status = ExternalDownloadProtocol.STATUS_DOWNLOADING,
                    progress = progress,
                    downloadedBytes =
                        if (progress != null && totalBytes != null) {
                            (progress * totalBytes).roundToLong().coerceIn(0L, totalBytes)
                        } else {
                            null
                        },
                )
            }
            is Task.DownloadState.Canceled -> {
                val progress = normalizeProgress(downloadState.progress)
                val recoverablePause = ownership.paused
                base.copy(
                    status =
                        if (recoverablePause) {
                            ExternalDownloadProtocol.STATUS_PAUSED
                        } else {
                            ExternalDownloadProtocol.STATUS_CANCELED
                        },
                    errorCode =
                        if (recoverablePause) {
                            ExternalDownloadProtocol.ERROR_OK
                        } else {
                            ExternalDownloadProtocol.ERROR_CANCELED
                        },
                    progress = progress,
                    downloadedBytes =
                        if (progress != null && totalBytes != null) {
                            (progress * totalBytes).roundToLong().coerceIn(0L, totalBytes)
                        } else {
                            null
                        },
                )
            }
            is Task.DownloadState.Error ->
                base.copy(
                    status = ExternalDownloadProtocol.STATUS_FAILED,
                    errorCode = ExternalDownloadProtocol.ERROR_DOWNLOAD_FAILED,
                    errorMessage = downloadState.throwable.message,
                    progress = null,
                    downloadedBytes = null,
                    stripResult =
                        stripResultForTerminal(
                            stripRequested = ownership.stripRequested,
                            completed = false,
                            actualResult = null,
                        ),
                    stripMessage =
                        if (ownership.stripRequested) downloadState.throwable.message else null,
                )
            is Task.DownloadState.Completed -> {
                val reportedStripResult =
                    stripResultForTerminal(
                        stripRequested = ownership.stripRequested,
                        completed = true,
                        actualResult = downloadState.stripResult,
                    )
                val stripConfirmed =
                    !ownership.stripRequested ||
                        reportedStripResult == ExternalDownloadProtocol.STRIP_RESULT_APPLIED
                base.copy(
                    status =
                        if (stripConfirmed) {
                            ExternalDownloadProtocol.STATUS_COMPLETED
                        } else {
                            ExternalDownloadProtocol.STATUS_FAILED
                        },
                    errorCode =
                        if (stripConfirmed) {
                            ExternalDownloadProtocol.ERROR_OK
                        } else {
                            ExternalDownloadProtocol.ERROR_DOWNLOAD_FAILED
                        },
                    errorMessage =
                        if (stripConfirmed) {
                            null
                        } else {
                            "Strip task completed without a confirmed applied outcome"
                        },
                    progress = if (stripConfirmed) 1.0 else null,
                    downloadedBytes = if (stripConfirmed) totalBytes else null,
                    filePath = if (stripConfirmed) downloadState.filePath else null,
                    stripResult = reportedStripResult,
                    stripMessage =
                        when {
                            !ownership.stripRequested -> null
                            stripConfirmed -> downloadState.stripMessage
                            else ->
                                downloadState.stripMessage
                                    ?: "Strip result was not applied; retry the strip task"
                        },
                )
            }
        }
    }

    internal fun stripResultForTerminal(
        stripRequested: Boolean,
        completed: Boolean,
        actualResult: StripResult?,
    ): String? {
        if (completed && actualResult == StripResult.Applied) {
            return ExternalDownloadProtocol.STRIP_RESULT_APPLIED
        }
        return if (stripRequested) ExternalDownloadProtocol.STRIP_RESULT_FAILED else null
    }

    private fun resolveTotalBytes(state: Task.State): Long? {
        val info = state.videoInfo
        val requestedTotal =
            info?.requestedFormats
                ?.sumOf { it.fileSize ?: it.fileSizeApprox ?: 0.0 }
                ?.takeIf { it > 0.0 }
                ?: info?.requestedDownloads
                    ?.sumOf { it.fileSize ?: it.fileSizeApprox ?: 0.0 }
                    ?.takeIf { it > 0.0 }
        val total =
            requestedTotal
                ?: info?.fileSize?.takeIf { it > 0.0 }
                ?: info?.fileSizeApprox?.takeIf { it > 0.0 }
                ?: state.viewState.fileSizeApprox.takeIf { it > 0.0 }
        return total?.roundToLong()?.takeIf { it > 0L }
    }

    private fun resolveQuality(task: Task, state: Task.State): String? {
        val formats =
            if (task.preferences.extractAudio) {
                state.viewState.audioOnlyFormats
            } else {
                state.viewState.videoFormats
            }
        val formatLabel =
            formats
                ?.firstOrNull()
                ?.let { it.formatNote ?: it.resolution ?: it.format }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        if (formatLabel != null) return formatLabel
        val info = state.videoInfo
        return (info?.formatNote ?: info?.resolution ?: info?.format)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: if (task.preferences.extractAudio) "audio" else null
    }

    private fun normalizeProgress(progress: Float?): Double? {
        val value = progress?.toDouble() ?: return null
        if (!value.isFinite() || value < 0.0) return null
        return value.coerceIn(0.0, 1.0)
    }
}
