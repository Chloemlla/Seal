package com.chloemlla.seal.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Runs Seal's initialized youtubedl-android FFmpeg executable for strip post-processing. */
internal object StripFfmpegRunner {
    private val activeProcesses = ConcurrentHashMap<String, Process>()
    private val activeTasks = ConcurrentHashMap.newKeySet<String>()
    private val canceledTasks = ConcurrentHashMap.newKeySet<String>()

    fun begin(taskId: String) {
        activeTasks += taskId
    }

    fun end(taskId: String) {
        activeTasks.remove(taskId)
        canceledTasks.remove(taskId)
        activeProcesses.remove(taskId)?.destroy()
    }

    fun throwIfCanceled(taskId: String) {
        if (taskId in canceledTasks) throw YoutubeDL.CanceledException()
    }

    fun cancel(taskId: String): Boolean {
        val wasActive = taskId in activeTasks
        val firstCancellation = canceledTasks.add(taskId)
        val process = activeProcesses.remove(taskId)
        process?.destroy()
        return firstCancellation || wasActive || process != null
    }

    fun run(context: Context, taskId: String, concatFile: File, output: File) {
        throwIfCanceled(taskId)
        val executable = resolveExecutable(File(context.applicationInfo.nativeLibraryDir))
        val process =
            ProcessBuilder(buildCommand(executable, concatFile, output))
                .redirectErrorStream(true)
                .apply {
                    val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
                    environment()["LD_LIBRARY_PATH"] =
                        listOf("python", "ffmpeg", "aria2c")
                            .joinToString(":") { File(packages, "$it/usr/lib").absolutePath }
                    environment()["PATH"] =
                        System.getenv("PATH").orEmpty() +
                            ":" +
                            context.applicationInfo.nativeLibraryDir
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                }
                .start()

        activeProcesses[taskId] = process
        if (taskId in canceledTasks) {
            activeProcesses.remove(taskId, process)
            process.destroy()
            throw YoutubeDL.CanceledException()
        }
        val outputTail = StringBuilder()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    outputTail.appendLine(line)
                    if (outputTail.length > MAX_LOG_CHARS) {
                        outputTail.delete(0, outputTail.length - MAX_LOG_CHARS)
                    }
                }
            }
            val exitCode = process.waitFor()
            failureForExit(
                    exitCode = exitCode,
                    outputReady = output.isFile && output.length() > 0L,
                    canceled = taskId in canceledTasks,
                    outputTail = outputTail.toString(),
                )
                ?.let { throw it }
        } catch (error: Throwable) {
            process.destroy()
            if (taskId in canceledTasks) throw YoutubeDL.CanceledException()
            throw error
        } finally {
            activeProcesses.remove(taskId, process)
        }
    }

    /**
     * youtubedl-android sets this exact native-library entry as its `--ffmpeg-location`.
     * FFmpeg.init extracts the dependent libraries under noBackupFilesDir/packages/ffmpeg.
     */
    internal fun resolveExecutable(nativeLibraryDir: File): File =
        File(nativeLibraryDir, FFMPEG_EXECUTABLE).also {
            check(it.isFile) { "Embedded FFmpeg is unavailable at ${it.absolutePath}" }
        }

    internal fun buildCommand(executable: File, concatFile: File, output: File): List<String> =
        listOf(
            executable.absolutePath,
            "-hide_banner",
            "-nostdin",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            concatFile.absolutePath,
            "-c",
            "copy",
            output.absolutePath,
        )

    internal fun failureForExit(
        exitCode: Int,
        outputReady: Boolean,
        canceled: Boolean,
        outputTail: String,
    ): Throwable? {
        if (canceled) return YoutubeDL.CanceledException()
        if (exitCode == 0 && outputReady) return null
        return IllegalStateException(
            "FFmpeg concat failed (exit=$exitCode): ${outputTail.trim()}"
        )
    }

    private const val FFMPEG_EXECUTABLE = "libffmpeg.so"
    private const val MAX_LOG_CHARS = 16 * 1024
}
