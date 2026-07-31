package com.chloemlla.seal.download

import android.content.Context
import android.util.Log
import com.chloemlla.seal.util.VideoClip
import java.io.File

/** Task-scoped FFmpeg concat for externally supplied keep ranges. */
internal object StripConcatProcessor {
    private val stripPartPattern = Regex("""^(.*)\.strip-part-(\d+)-(\d+)\.([^.]+)$""")
    private val stripFullPattern = Regex("""^(.*)\.strip-full\.([^.]+)$""")
    private val mediaExtensions =
        setOf("3gp", "aac", "flac", "m4a", "mkv", "mov", "mp3", "mp4", "ogg", "opus", "wav", "webm")

    data class Session(val directory: File)

    fun prepare(context: Context, taskId: String): Session {
        StripFfmpegRunner.begin(taskId)
        val base = File(context.cacheDir, "external_strip")
        val safeId = taskId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "task" }
        val directory = File(base, safeId)
        try {
            StripFfmpegRunner.throwIfCanceled(taskId)
            check(directory.canonicalPath.startsWith(base.canonicalPath + File.separator))
            if (directory.exists()) {
                check(directory.deleteRecursively()) { "Unable to reset temporary strip workspace" }
            }
            StripFfmpegRunner.throwIfCanceled(taskId)
            check(directory.mkdirs() || directory.isDirectory)
            return Session(directory)
        } catch (error: Throwable) {
            runCatching { directory.deleteRecursively() }
            StripFfmpegRunner.end(taskId)
            throw error
        }
    }

    fun clear(session: Session) {
        session.directory.listFiles()?.forEach { child ->
            check(child.deleteRecursively()) { "Unable to clear temporary strip input ${child.name}" }
        }
    }

    fun collectParts(session: Session, keepSections: List<VideoClip>): Result<List<File>> =
        runCatching {
            val partsByRange =
                session.directory
                    .walkTopDown()
                    .filter(File::isFile)
                    .mapNotNull { file ->
                        val match = stripPartPattern.matchEntire(file.name) ?: return@mapNotNull null
                        if (file.extension.lowercase() !in mediaExtensions) return@mapNotNull null
                        val start = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                        val end = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                        (start to end) to file
                    }
                    .toMap()
            keepSections.map { section ->
                partsByRange[section.start to section.end]
                    ?: error("Missing downloaded keep range ${section.start}-${section.end}")
            }
        }

    fun collectFullSource(session: Session): Result<File> =
        runCatching {
            val sources =
                session.directory
                    .walkTopDown()
                    .filter(File::isFile)
                    .filter { stripFullPattern.matches(it.name) }
                    .filter { it.extension.lowercase() in mediaExtensions }
                    .toList()
            require(sources.size == 1) {
                "Expected one full-source media file, found ${sources.size}"
            }
            sources.single()
        }

    fun concatenate(
        context: Context,
        taskId: String,
        session: Session,
        parts: List<File>,
        destinationDirectory: File,
        restrictFilenames: Boolean,
    ): Result<File> =
        runCatching {
            require(parts.isNotEmpty()) { "No downloaded keep ranges to concatenate" }
            val extensions = parts.map { it.extension.lowercase() }.toSet()
            require(extensions.size == 1 && extensions.first().isNotBlank()) {
                "Downloaded keep ranges use incompatible containers"
            }
            check(destinationDirectory.mkdirs() || destinationDirectory.isDirectory)

            val firstMatch = stripPartPattern.matchEntire(parts.first().name)
                ?: error("Unexpected keep-range filename")
            val baseName = firstMatch.groupValues[1].ifBlank { "video" }
            val tag = if (restrictFilenames) " [stripped]" else " [去广告]"
            val destination = nextAvailableFile(destinationDirectory, "$baseName$tag", extensions.first())

            if (parts.size == 1) {
                StripFfmpegRunner.throwIfCanceled(taskId)
                moveFile(parts.first(), destination)
                return@runCatching destination
            }

            val concatFile = File(session.directory, "concat.txt")
            concatFile.writeText(buildConcatSpec(parts), Charsets.UTF_8)
            val mergedFile = File(session.directory, "merged.${extensions.first()}")
            StripFfmpegRunner.run(context, taskId, concatFile, mergedFile)
            StripFfmpegRunner.throwIfCanceled(taskId)
            moveFile(mergedFile, destination)
            parts.forEach { it.delete() }
            destination
        }

    fun trimFullSource(
        context: Context,
        taskId: String,
        session: Session,
        source: File,
        keepSections: List<VideoClip>,
        destinationDirectory: File,
        restrictFilenames: Boolean,
    ): Result<File> =
        runCatching {
            require(keepSections.isNotEmpty()) { "No keep ranges supplied for full-source strip" }
            val match = stripFullPattern.matchEntire(source.name)
                ?: error("Unexpected full-source filename")
            val extension = source.extension.lowercase()
            require(extension in mediaExtensions) { "Unsupported full-source media container" }
            check(destinationDirectory.mkdirs() || destinationDirectory.isDirectory)

            val baseName = match.groupValues[1].ifBlank { "video" }
            val tag = if (restrictFilenames) " [stripped]" else " [去广告]"
            val destination = nextAvailableFile(destinationDirectory, "$baseName$tag", extension)
            val concatFile = File(session.directory, "full-source.ffconcat")
            concatFile.writeText(buildRangeConcatSpec(source, keepSections), Charsets.UTF_8)
            val strippedFile = File(session.directory, "full-source-stripped.$extension")
            StripFfmpegRunner.run(context, taskId, concatFile, strippedFile)
            StripFfmpegRunner.throwIfCanceled(taskId)
            moveFile(strippedFile, destination)
            destination
        }

    fun throwIfCanceled(taskId: String) {
        StripFfmpegRunner.throwIfCanceled(taskId)
    }

    fun cleanup(taskId: String, session: Session) {
        try {
            runCatching {
                    check(!session.directory.exists() || session.directory.deleteRecursively()) {
                        "Unable to remove temporary strip workspace"
                    }
                }
                .onFailure { Log.w(TAG, "strip workspace cleanup failed", it) }
        } finally {
            StripFfmpegRunner.end(taskId)
        }
    }

    fun cancel(taskId: String): Boolean = StripFfmpegRunner.cancel(taskId)

    internal fun buildConcatSpec(parts: List<File>): String =
        buildString {
            append("ffconcat version 1.0\n")
            parts.forEach { part ->
                val escaped = part.absolutePath.replace("'", "'\\''")
                append("file '").append(escaped).append("'\n")
            }
        }

    internal fun buildRangeConcatSpec(source: File, keepSections: List<VideoClip>): String =
        buildString {
            append("ffconcat version 1.0\n")
            val escaped = source.absolutePath.replace("'", "'\\''")
            keepSections.forEach { section ->
                require(section.start >= 0 && section.end > section.start) {
                    "Invalid keep range ${section.start}-${section.end}"
                }
                append("file '").append(escaped).append("'\n")
                append("inpoint ").append(section.start).append('\n')
                append("outpoint ").append(section.end).append('\n')
            }
        }

    private fun nextAvailableFile(directory: File, baseName: String, extension: String): File {
        val direct = File(directory, "$baseName.$extension")
        if (!direct.exists()) return direct
        for (index in 1..9999) {
            val candidate = File(directory, "$baseName ($index).$extension")
            if (!candidate.exists()) return candidate
        }
        error("Unable to allocate output filename")
    }

    private fun moveFile(source: File, destination: File) {
        if (source.renameTo(destination)) return
        try {
            source.copyTo(destination, overwrite = false)
            check(source.delete()) { "Unable to remove temporary strip output" }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    private const val TAG = "StripConcatProcessor"
}
