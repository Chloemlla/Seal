package com.chloemlla.seal.integration

import com.chloemlla.seal.download.Task
import com.chloemlla.seal.util.DownloadUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalDownloadTaskSnapshotTest {
    @Test
    fun runningSnapshotReportsProgressAndEstimatedBytes() {
        val task = task()
        val snapshot =
            ExternalDownloadTaskSnapshotFactory.create(
                task = task,
                state =
                    state(
                        Task.DownloadState.Running(
                            taskId = task.id,
                            progress = 0.25f,
                        )
                    ),
                ownership = ownership(task),
            )

        assertEquals(ExternalDownloadProtocol.STATUS_DOWNLOADING, snapshot.status)
        assertEquals(0.25, snapshot.progress!!, 0.0001)
        assertEquals(250L, snapshot.downloadedBytes)
        assertEquals(1000L, snapshot.totalBytes)
        assertEquals("Example title", snapshot.title)
        assertEquals(task.url, snapshot.sourceUrl)
    }

    @Test
    fun explicitPauseKeepsCanceledTaskRecoverable() {
        val task = task()
        val snapshot =
            ExternalDownloadTaskSnapshotFactory.create(
                task = task,
                state =
                    state(
                        Task.DownloadState.Canceled(
                            action = Task.RestartableAction.Download,
                            progress = 0.5f,
                        )
                    ),
                ownership = ownership(task, paused = true),
            )

        assertEquals(ExternalDownloadProtocol.STATUS_PAUSED, snapshot.status)
        assertEquals(ExternalDownloadProtocol.ERROR_OK, snapshot.errorCode)
        assertEquals(0.5, snapshot.progress!!, 0.0001)
        assertEquals(500L, snapshot.downloadedBytes)
    }

    @Test
    fun naturalCancellationRemainsTerminal() {
        val task = task()
        val snapshot =
            ExternalDownloadTaskSnapshotFactory.create(
                task = task,
                state =
                    state(
                        Task.DownloadState.Canceled(
                            action = Task.RestartableAction.Download,
                            progress = 0.5f,
                        )
                    ),
                ownership =
                    ownership(
                        task = task,
                        paused = false,
                        lastStatus = ExternalDownloadProtocol.STATUS_DOWNLOADING,
                    ),
            )

        assertEquals(ExternalDownloadProtocol.STATUS_CANCELED, snapshot.status)
        assertEquals(ExternalDownloadProtocol.ERROR_CANCELED, snapshot.errorCode)
    }

    @Test
    fun failedTaskKeepsRetryableMetadataWithoutInventingProgress() {
        val task = task(extractAudio = true)
        val snapshot =
            ExternalDownloadTaskSnapshotFactory.create(
                task = task,
                state =
                    state(
                        Task.DownloadState.Error(
                            throwable = IllegalStateException("network failed"),
                            action = Task.RestartableAction.Download,
                        )
                    ),
                ownership = ownership(task),
            )

        assertEquals(ExternalDownloadProtocol.STATUS_FAILED, snapshot.status)
        assertEquals(
            ExternalDownloadProtocol.ERROR_DOWNLOAD_FAILED,
            snapshot.errorCode,
        )
        assertEquals("network failed", snapshot.errorMessage)
        assertNull(snapshot.progress)
        assertNull(snapshot.downloadedBytes)
        assertEquals(true, snapshot.extractAudio)
    }

    private fun task(extractAudio: Boolean = false) =
        Task(
            url = "https://example.com/video",
            preferences =
                DownloadUtil.DownloadPreferences.EMPTY.copy(
                    extractAudio = extractAudio,
                ),
            id = "task-1",
        )

    private fun state(downloadState: Task.DownloadState) =
        Task.State(
            downloadState = downloadState,
            videoInfo = null,
            viewState =
                Task.ViewState(
                    title = "Example title",
                    fileSizeApprox = 1000.0,
                ),
        )

    private fun ownership(
        task: Task,
        paused: Boolean = false,
        lastStatus: String = ExternalDownloadProtocol.STATUS_WAITING,
    ) =
        ExternalDownloadOwnership(
            taskId = task.id,
            callerPackage = "com.example.caller",
            callerRequestId = "request-1",
            sourceUrl = task.url,
            extractAudio = task.preferences.extractAudio,
            paused = paused,
            lastStatus = lastStatus,
        )
}
