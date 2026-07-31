package com.chloemlla.seal.download

import com.yausername.youtubedl_android.YoutubeDL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StripFallbackControllerTest {
    @Test
    fun primaryFailureResetsThenFallbackSuccessRemainsApplied() {
        val events = mutableListOf<String>()
        val result =
            StripFallbackController.execute<DownloadOutcome>(
                primary = {
                    events += "C"
                    Result.failure(IllegalStateException("sections failed"))
                },
                resetForFallback = {
                    events += "reset"
                    Result.success(Unit)
                },
                fallback = {
                    events += "D"
                    Result.success(
                        DownloadOutcome(
                            filePaths = listOf("stripped.mp4"),
                            stripResult = StripResult.Applied,
                        )
                    )
                },
                cleanup = { events += "cleanup" },
                isCancellation = { it is YoutubeDL.CanceledException },
            )

        assertEquals(StripResult.Applied, result.getOrThrow().stripResult)
        assertEquals(listOf("C", "reset", "D", "cleanup"), events)
    }

    @Test
    fun primaryAndFallbackFailureReturnRetryableFailure() {
        val primaryFailure = IllegalStateException("sections failed")
        val fallbackFailure = IllegalStateException("full source failed")
        var cleanupCalls = 0
        val result =
            StripFallbackController.execute<String>(
                primary = { Result.failure(primaryFailure) },
                resetForFallback = { Result.success(Unit) },
                fallback = { Result.failure(fallbackFailure) },
                cleanup = { cleanupCalls++ },
                isCancellation = { false },
            )

        val error = result.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("retry the strip task") == true)
        assertSame(fallbackFailure, error?.cause)
        assertTrue(error?.suppressed?.contains(primaryFailure) == true)
        assertEquals(1, cleanupCalls)
    }

    @Test
    fun cancellationDoesNotEnterFallbackAndStillCleansUp() {
        val events = mutableListOf<String>()
        val canceled = YoutubeDL.CanceledException()
        val result =
            StripFallbackController.execute<String>(
                primary = {
                    events += "C"
                    Result.failure(canceled)
                },
                resetForFallback = {
                    events += "reset"
                    Result.success(Unit)
                },
                fallback = {
                    events += "D"
                    Result.success("unexpected")
                },
                cleanup = { events += "cleanup" },
                isCancellation = { it is YoutubeDL.CanceledException },
            )

        assertSame(canceled, result.exceptionOrNull())
        assertEquals(listOf("C", "cleanup"), events)
    }

    @Test
    fun primarySuccessAlsoRunsCleanupExactlyOnce() {
        var cleanupCalls = 0
        val result =
            StripFallbackController.execute(
                primary = { Result.success("section-output") },
                resetForFallback = { Result.success(Unit) },
                fallback = { Result.success("unexpected") },
                cleanup = { cleanupCalls++ },
                isCancellation = { false },
            )

        assertEquals("section-output", result.getOrThrow())
        assertEquals(1, cleanupCalls)
    }
}
