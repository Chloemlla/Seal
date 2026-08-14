package com.chloemlla.seal.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDownloadOwnershipTest {
    @Test
    fun ownershipHistoryIsBoundedByMostRecentUpdate() {
        val source =
            (0 until ExternalDownloadOwnershipStore.MAX_RECORDS + 4).map { index ->
                ownership(taskId = "task-$index", updatedAtMs = index.toLong())
            }

        val bounded = boundExternalDownloadOwnerships(source)

        assertEquals(ExternalDownloadOwnershipStore.MAX_RECORDS, bounded.size)
        assertEquals(
            "task-${ExternalDownloadOwnershipStore.MAX_RECORDS + 3}",
            bounded.first().taskId,
        )
        assertFalse(bounded.any { it.taskId == "task-0" })
        assertTrue(
            bounded.zipWithNext().all { (left, right) ->
                left.updatedAtMs >= right.updatedAtMs
            }
        )
    }

    @Test
    fun ownershipValidationRequiresTheSystemCallerAndMatchingRequest() {
        val ownership = ownership(taskId = "task-1", callerRequestId = "request-1")

        assertNull(
            validateExternalDownloadOwnership(
                callingPackage = "com.example.caller",
                requestedRequestId = "request-1",
                ownership = ownership,
            ),
        )
        assertEquals(
            ExternalDownloadProtocol.ERROR_CALLER_DENIED,
            validateExternalDownloadOwnership(
                callingPackage = "com.example.other",
                requestedRequestId = "request-1",
                ownership = ownership,
            ),
        )
        assertEquals(
            ExternalDownloadProtocol.ERROR_CALLER_DENIED,
            validateExternalDownloadOwnership(
                callingPackage = "com.example.caller",
                requestedRequestId = "request-2",
                ownership = ownership,
            ),
        )
    }

    @Test
    fun controlActionParsingIsStrictAndCaseInsensitive() {
        assertEquals(
            ExternalDownloadControlAction.Pause,
            ExternalDownloadControlAction.parse(" PAUSE "),
        )
        assertEquals(
            ExternalDownloadControlAction.Resume,
            ExternalDownloadControlAction.parse("resume"),
        )
        assertEquals(
            ExternalDownloadControlAction.Retry,
            ExternalDownloadControlAction.parse("Retry"),
        )
        assertEquals(
            ExternalDownloadControlAction.Delete,
            ExternalDownloadControlAction.parse("delete"),
        )
        assertNull(ExternalDownloadControlAction.parse("open"))
        assertNull(ExternalDownloadControlAction.parse(null))
    }

    @Test
    fun activeOwnershipBecomesRecoverablePauseAfterProcessRestart() {
        val active =
            ownership(taskId = "task-active").copy(
                paused = false,
                lastStatus = ExternalDownloadProtocol.STATUS_DOWNLOADING,
            )
        val failed =
            ownership(taskId = "task-failed").copy(
                paused = false,
                lastStatus = ExternalDownloadProtocol.STATUS_FAILED,
            )

        val recoveredActive = recoverExternalDownloadOwnershipAfterRestart(active)
        val recoveredFailed = recoverExternalDownloadOwnershipAfterRestart(failed)

        assertTrue(recoveredActive.paused)
        assertEquals(ExternalDownloadProtocol.STATUS_PAUSED, recoveredActive.lastStatus)
        assertEquals(failed, recoveredFailed)
    }

    private fun ownership(
        taskId: String,
        callerRequestId: String? = null,
        updatedAtMs: Long = 0L,
    ) =
        ExternalDownloadOwnership(
            taskId = taskId,
            callerPackage = "com.example.caller",
            callerRequestId = callerRequestId,
            sourceUrl = "https://example.com/$taskId",
            extractAudio = false,
            createdAtMs = updatedAtMs,
            updatedAtMs = updatedAtMs,
        )
}
