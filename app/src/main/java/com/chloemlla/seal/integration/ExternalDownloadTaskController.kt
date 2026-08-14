package com.chloemlla.seal.integration

import android.content.Context
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.Task

internal enum class ExternalDownloadControlAction(val wireValue: String) {
    Pause("pause"),
    Resume("resume"),
    Retry("retry"),
    Delete("delete");

    companion object {
        fun parse(value: String?): ExternalDownloadControlAction? {
            val normalized = value?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.wireValue == normalized }
        }
    }
}

internal sealed interface ExternalDownloadControlResult {
    data class Success(val status: String, val ownership: ExternalDownloadOwnership) :
        ExternalDownloadControlResult

    data class Failure(val errorCode: String, val message: String) :
        ExternalDownloadControlResult
}

internal object ExternalDownloadTaskController {
    fun perform(
        context: Context,
        downloader: DownloaderV2,
        ownership: ExternalDownloadOwnership,
        action: ExternalDownloadControlAction,
    ): ExternalDownloadControlResult {
        val entry =
            downloader
                .getTaskStateMap()
                .entries
                .firstOrNull { (task, _) -> task.id == ownership.taskId }
                ?: return missingTask()
        val task = entry.key
        val state = entry.value.downloadState
        return when (action) {
            ExternalDownloadControlAction.Pause ->
                pause(context, downloader, task, state, ownership)
            ExternalDownloadControlAction.Resume ->
                resume(context, downloader, task, state, ownership)
            ExternalDownloadControlAction.Retry ->
                retry(context, downloader, task, state, ownership)
            ExternalDownloadControlAction.Delete ->
                delete(context, downloader, task, state, ownership)
        }
    }

    private fun pause(
        context: Context,
        downloader: DownloaderV2,
        task: Task,
        state: Task.DownloadState,
        ownership: ExternalDownloadOwnership,
    ): ExternalDownloadControlResult {
        if (state is Task.DownloadState.Canceled && ownership.paused) {
            return ExternalDownloadControlResult.Success(
                ExternalDownloadProtocol.STATUS_PAUSED,
                ownership,
            )
        }
        if (
            state !is Task.DownloadState.Cancelable &&
                state != Task.DownloadState.Idle &&
                state != Task.DownloadState.ReadyWithInfo
        ) {
            return unsupported("Task cannot be paused from ${state::class.simpleName}")
        }
        val pausedOwnership =
            ExternalDownloadOwnershipStore.update(context, ownership.taskId) {
                it.copy(paused = true, lastStatus = ExternalDownloadProtocol.STATUS_PAUSED)
            } ?: return missingTask()
        if (!downloader.cancel(task)) {
            ExternalDownloadOwnershipStore.update(context, ownership.taskId) {
                it.copy(paused = false, lastStatus = ownership.lastStatus)
            }
            return unsupported("Seal could not pause the task")
        }
        return ExternalDownloadControlResult.Success(
            ExternalDownloadProtocol.STATUS_PAUSED,
            pausedOwnership,
        )
    }

    private fun resume(
        context: Context,
        downloader: DownloaderV2,
        task: Task,
        state: Task.DownloadState,
        ownership: ExternalDownloadOwnership,
    ): ExternalDownloadControlResult {
        if (state !is Task.DownloadState.Canceled || !ownership.paused) {
            return unsupported("Only a paused task can be resumed")
        }
        val resumedOwnership =
            ExternalDownloadOwnershipStore.update(context, ownership.taskId) {
                it.copy(paused = false, lastStatus = ExternalDownloadProtocol.STATUS_WAITING)
            } ?: return missingTask()
        return runCatching { downloader.restart(task) }
            .fold(
                onSuccess = {
                    ExternalDownloadControlResult.Success(
                        ExternalDownloadProtocol.STATUS_WAITING,
                        resumedOwnership,
                    )
                },
                onFailure = {
                    ExternalDownloadOwnershipStore.update(context, ownership.taskId) { current ->
                        current.copy(
                            paused = true,
                            lastStatus = ExternalDownloadProtocol.STATUS_PAUSED,
                        )
                    }
                    unsupported("Seal could not resume the task")
                },
            )
    }

    private fun retry(
        context: Context,
        downloader: DownloaderV2,
        task: Task,
        state: Task.DownloadState,
        ownership: ExternalDownloadOwnership,
    ): ExternalDownloadControlResult {
        if (state !is Task.DownloadState.Error) {
            return unsupported("Only a failed task can be retried")
        }
        val retryOwnership =
            ExternalDownloadOwnershipStore.update(context, ownership.taskId) {
                it.copy(paused = false, lastStatus = ExternalDownloadProtocol.STATUS_WAITING)
            } ?: return missingTask()
        return runCatching { downloader.restart(task) }
            .fold(
                onSuccess = {
                    ExternalDownloadControlResult.Success(
                        ExternalDownloadProtocol.STATUS_WAITING,
                        retryOwnership,
                    )
                },
                onFailure = {
                    ExternalDownloadOwnershipStore.update(context, ownership.taskId) { current ->
                        current.copy(lastStatus = ExternalDownloadProtocol.STATUS_FAILED)
                    }
                    unsupported("Seal could not retry the task")
                },
            )
    }

    private fun delete(
        context: Context,
        downloader: DownloaderV2,
        task: Task,
        state: Task.DownloadState,
        ownership: ExternalDownloadOwnership,
    ): ExternalDownloadControlResult {
        if (
            state is Task.DownloadState.Cancelable ||
                state == Task.DownloadState.Idle ||
                state == Task.DownloadState.ReadyWithInfo
        ) {
            downloader.cancel(task)
        }
        if (!downloader.remove(task)) return missingTask()
        ExternalDownloadTaskMonitor.stop(ownership.taskId)
        ExternalDownloadTaskOutput.deleteCookies(context, ownership)
        ExternalDownloadOwnershipStore.remove(context, ownership.taskId)
        return ExternalDownloadControlResult.Success(
            ExternalDownloadProtocol.STATUS_CANCELED,
            ownership,
        )
    }

    private fun missingTask() =
        ExternalDownloadControlResult.Failure(
            ExternalDownloadProtocol.ERROR_TASK_NOT_FOUND,
            "External download task was not found",
        )

    private fun unsupported(message: String) =
        ExternalDownloadControlResult.Failure(
            ExternalDownloadProtocol.ERROR_UNSUPPORTED_ACTION,
            message,
        )
}
