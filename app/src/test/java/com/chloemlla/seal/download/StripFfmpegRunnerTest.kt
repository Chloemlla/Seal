package com.chloemlla.seal.download

import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StripFfmpegRunnerTest {
    @Test
    fun unavailableRuntimeIsRejectedBeforeProcessStart() {
        val nativeLibraryDir = Files.createTempDirectory("seal-no-ffmpeg").toFile()
        try {
            val error =
                runCatching { StripFfmpegRunner.resolveExecutable(nativeLibraryDir) }
                    .exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(error?.message?.contains("libffmpeg.so") == true)
        } finally {
            nativeLibraryDir.deleteRecursively()
        }
    }

    @Test
    fun commandUsesInitializedNativeEntryAndConcatDemuxer() {
        val command =
            StripFfmpegRunner.buildCommand(
                executable = File("/native/libffmpeg.so"),
                concatFile = File("/tmp/input.ffconcat"),
                output = File("/tmp/output.mp4"),
            )

        assertEquals("/native/libffmpeg.so", command.first())
        assertTrue(command.containsAll(listOf("-f", "concat", "-safe", "0", "-c", "copy")))
        assertEquals("/tmp/output.mp4", command.last())
    }

    @Test
    fun nonZeroExitIsExplicitFailure() {
        val error =
            StripFfmpegRunner.failureForExit(
                exitCode = 1,
                outputReady = false,
                canceled = false,
                outputTail = "concat failed",
            )

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("exit=1") == true)
    }

    @Test
    fun cancellationKeepsCanceledExceptionType() {
        val error =
            StripFfmpegRunner.failureForExit(
                exitCode = 143,
                outputReady = false,
                canceled = true,
                outputTail = "",
            )

        assertTrue(error is YoutubeDL.CanceledException)
    }

    @Test
    fun cancellationBetweenProcessesIsObservedByWorkflowSeam() {
        val taskId = "strip-cancel-between-processes"
        StripFfmpegRunner.begin(taskId)
        try {
            assertTrue(StripFfmpegRunner.cancel(taskId))
            val error = runCatching { StripFfmpegRunner.throwIfCanceled(taskId) }.exceptionOrNull()
            assertTrue(error is YoutubeDL.CanceledException)
        } finally {
            StripFfmpegRunner.end(taskId)
        }
    }

    @Test
    fun cancellationBeforeWorkflowBeginIsNotCleared() {
        val taskId = "strip-cancel-before-begin"
        assertTrue(StripFfmpegRunner.cancel(taskId))
        StripFfmpegRunner.begin(taskId)
        try {
            val error = runCatching { StripFfmpegRunner.throwIfCanceled(taskId) }.exceptionOrNull()
            assertTrue(error is YoutubeDL.CanceledException)
        } finally {
            StripFfmpegRunner.end(taskId)
        }
    }

    @Test
    fun zeroExitWithoutOutputIsFailure() {
        val error =
            StripFfmpegRunner.failureForExit(
                exitCode = 0,
                outputReady = false,
                canceled = false,
                outputTail = "",
            )

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun successfulExitRequiresNonEmptyOutput() {
        assertNull(
            StripFfmpegRunner.failureForExit(
                exitCode = 0,
                outputReady = true,
                canceled = false,
                outputTail = "",
            )
        )
    }
}
