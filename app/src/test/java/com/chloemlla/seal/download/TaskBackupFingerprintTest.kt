package com.chloemlla.seal.download

import com.chloemlla.seal.download.Task.DownloadState.Canceled
import com.chloemlla.seal.download.Task.DownloadState.Idle
import com.chloemlla.seal.download.Task.DownloadState.Running
import com.chloemlla.seal.download.Task.RestartableAction.Download
import com.chloemlla.seal.util.DownloadUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TaskBackupFingerprintTest {
    private fun sampleTask(url: String = "https://example.com/a"): Task =
        Task(url = url, preferences = DownloadUtil.DownloadPreferences.EMPTY)

    @Test
    fun progressWithinBucketDoesNotChangeFingerprint() {
        val task = sampleTask()
        val base =
            mapOf(
                task to
                    Task.State(
                        Running(taskId = task.id, progress = 0.10f, progressText = "10%"),
                        null,
                        Task.ViewState(url = task.url, title = "t"),
                    )
            )
        val nearby =
            mapOf(
                task to
                    Task.State(
                        Running(taskId = task.id, progress = 0.14f, progressText = "14%"),
                        null,
                        Task.ViewState(url = task.url, title = "t"),
                    )
            )
        assertEquals(base.toTaskBackupFingerprint(), nearby.toTaskBackupFingerprint())
    }

    @Test
    fun progressCrossingBucketChangesFingerprint() {
        val task = sampleTask()
        val low =
            mapOf(
                task to
                    Task.State(
                        Running(taskId = task.id, progress = 0.10f),
                        null,
                        Task.ViewState(url = task.url, title = "t"),
                    )
            )
        val high =
            mapOf(
                task to
                    Task.State(
                        Running(taskId = task.id, progress = 0.20f),
                        null,
                        Task.ViewState(url = task.url, title = "t"),
                    )
            )
        assertNotEquals(low.toTaskBackupFingerprint(), high.toTaskBackupFingerprint())
    }

    @Test
    fun structureChangeChangesFingerprint() {
        val task = sampleTask()
        val idle =
            mapOf(
                task to
                    Task.State(Idle, null, Task.ViewState(url = task.url, title = "t"))
            )
        val canceled =
            mapOf(
                task to
                    Task.State(
                        Canceled(action = Download, progress = 0.5f),
                        null,
                        Task.ViewState(url = task.url, title = "t"),
                    )
            )
        assertNotEquals(idle.toTaskBackupFingerprint(), canceled.toTaskBackupFingerprint())
    }
}
