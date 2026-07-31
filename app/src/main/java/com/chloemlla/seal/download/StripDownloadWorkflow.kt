package com.chloemlla.seal.download

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.chloemlla.seal.App
import com.chloemlla.seal.App.Companion.audioDownloadDir
import com.chloemlla.seal.App.Companion.videoDownloadDir
import com.chloemlla.seal.util.DownloadUtil
import com.chloemlla.seal.util.FileUtil.getSdcardTempDir
import com.chloemlla.seal.util.VideoClip
import com.chloemlla.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/** C (section download + concat) with D (full download + explicit range post-process) fallback. */
internal class StripDownloadWorkflow(
    private val context: Context,
    private val videoInfo: VideoInfo,
    private val playlistUrl: String,
    private val playlistItem: Int,
    private val taskId: String,
    private val preferences: DownloadUtil.DownloadPreferences,
    private val progressCallback: ((Float, Long, String) -> Unit)?,
) {
    fun executeOutcome(): Result<DownloadOutcome> =
        execute().map { paths ->
            DownloadOutcome(filePaths = paths, stripResult = StripResult.Applied)
        }

    fun execute(): Result<List<String>> {
        val session =
            runCatching { StripConcatProcessor.prepare(context, taskId) }
                .getOrElse { failure ->
                    return Result.failure(
                        IllegalStateException(
                            "Unable to prepare strip workspace; retry the strip task",
                            failure,
                        )
                    )
                }
        return try {
            StripConcatProcessor.throwIfCanceled(taskId)
            val outputResult =
                StripFallbackController.execute(
                    primary = { downloadSectionsAndConcat(session) },
                    resetForFallback = {
                        runCatching {
                            StripConcatProcessor.clear(session)
                            StripConcatProcessor.throwIfCanceled(taskId)
                        }
                    },
                    fallback = { downloadFullSourceAndStrip(session) },
                    cleanup = {},
                    isCancellation = { it.isCancellation() },
                    onFallback = { failure ->
                        Log.w(
                            TAG,
                            "section strip failed; retrying full source with keep ranges",
                            failure,
                        )
                    },
                )
            outputResult.fold(
                onSuccess = { finalFile -> finalizeOutput(finalFile) },
                onFailure = { failure -> Result.failure(failure) },
            )
        } finally {
            StripConcatProcessor.cleanup(taskId, session)
            if (preferences.sdcard) {
                val staging = sdcardStagingDirectory()
                runCatching {
                        check(!staging.exists() || staging.deleteRecursively()) {
                            "Unable to remove strip SD-card staging"
                        }
                    }
                    .onFailure { Log.w(TAG, "strip SD-card staging cleanup failed", it) }
            }
        }
    }

    private fun downloadSectionsAndConcat(
        session: StripConcatProcessor.Session
    ): Result<File> =
        runCatching {
            val keepSections = preferences.stripKeepSections
            downloadInput(
                    temporaryPreferences = temporaryPreferences(videoClips = keepSections),
                    session = session,
                    outputTemplate = SECTION_OUTPUT_TEMPLATE,
                )
                .getOrThrow()
            StripConcatProcessor.throwIfCanceled(taskId)
            val parts = StripConcatProcessor.collectParts(session, keepSections).getOrThrow()
            StripConcatProcessor.concatenate(
                    context = context,
                    taskId = taskId,
                    session = session,
                    parts = parts,
                    destinationDirectory = prepareDestination(),
                    restrictFilenames = preferences.restrictFilenames,
                )
                .getOrThrow()
        }

    private fun downloadFullSourceAndStrip(
        session: StripConcatProcessor.Session
    ): Result<File> =
        runCatching {
            downloadInput(
                    temporaryPreferences = temporaryPreferences(videoClips = emptyList()),
                    session = session,
                    outputTemplate = FULL_OUTPUT_TEMPLATE,
                )
                .getOrThrow()
            StripConcatProcessor.throwIfCanceled(taskId)
            val source = StripConcatProcessor.collectFullSource(session).getOrThrow()
            StripConcatProcessor.trimFullSource(
                    context = context,
                    taskId = taskId,
                    session = session,
                    source = source,
                    keepSections = preferences.stripKeepSections,
                    destinationDirectory = prepareDestination(),
                    restrictFilenames = preferences.restrictFilenames,
                )
                .getOrThrow()
        }

    private fun temporaryPreferences(videoClips: List<VideoClip>) =
        preferences.copy(
            stripKeepSections = emptyList(),
            videoClips = videoClips,
            sponsorBlock = false,
            createThumbnail = false,
            downloadSubtitle = false,
            embedSubtitle = false,
            keepSubtitle = false,
            embedThumbnail = false,
            splitByChapter = false,
            useDownloadArchive = false,
            subdirectoryExtractor = false,
            subdirectoryPlaylistTitle = false,
            privateDirectory = false,
            sdcard = false,
        )

    private fun prepareDestination(): File {
        if (preferences.sdcard) {
            return sdcardStagingDirectory().apply {
                if (exists()) {
                    check(deleteRecursively()) { "Unable to reset strip SD-card staging" }
                }
                check(mkdirs() || isDirectory)
            }
        }
        return when {
            preferences.privateDirectory -> File(App.privateDownloadDir)
            preferences.extractAudio || videoInfo.vcodec == "none" -> File(audioDownloadDir)
            else -> File(videoDownloadDir)
        }
    }

    private fun sdcardStagingDirectory(): File {
        val safeId = taskId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        return context.getSdcardTempDir("strip-$safeId")
    }

    private fun finalizeOutput(finalFile: File): Result<List<String>> =
        if (preferences.sdcard) {
            StripOutputFinalizer.moveToSdcard(
                    context = context,
                    source = finalFile,
                    treeUri = preferences.sdcardUri,
                    throwIfCanceled = { StripConcatProcessor.throwIfCanceled(taskId) },
                )
                .mapCatching { output ->
                    try {
                        StripConcatProcessor.throwIfCanceled(taskId)
                        if (preferences.privateMode) {
                            emptyList()
                        } else {
                            DownloadUtil.insertInfoIntoDownloadHistory(
                                videoInfo,
                                listOf(output.uri),
                            )
                        }
                    } catch (error: Throwable) {
                        output.rollback(context)
                        throw error
                    }
                }
        } else {
            runCatching {
                    StripConcatProcessor.throwIfCanceled(taskId)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFile.absolutePath),
                        null,
                        null,
                    )
                    StripConcatProcessor.throwIfCanceled(taskId)
                    if (preferences.privateMode) {
                        emptyList()
                    } else {
                        DownloadUtil.insertInfoIntoDownloadHistory(
                            videoInfo,
                            listOf(finalFile.absolutePath),
                        )
                    }
                }
                .onFailure { finalFile.delete() }
        }

    private fun downloadInput(
        temporaryPreferences: DownloadUtil.DownloadPreferences,
        session: StripConcatProcessor.Session,
        outputTemplate: String,
    ): Result<Unit> =
        runCatching { StripConcatProcessor.throwIfCanceled(taskId) }
            .mapCatching {
                DownloadUtil.downloadVideoFiles(
                        videoInfo = videoInfo,
                        playlistUrl = playlistUrl,
                        playlistItem = playlistItem,
                        taskId = taskId,
                        downloadPreferences = temporaryPreferences,
                        progressCallback = progressCallback,
                        outputDirectoryOverride = session.directory,
                        outputTemplateOverride = outputTemplate,
                        finalizeDownload = false,
                    )
                    .getOrThrow()
                Unit
            }

    private fun Throwable.isCancellation(): Boolean =
        this is YoutubeDL.CanceledException || this is InterruptedException

    companion object {
        private const val TAG = "StripDownloadWorkflow"
        val SECTION_OUTPUT_TEMPLATE =
            "${DownloadUtil.BASENAME}.strip-part-%(section_start)d-%(section_end)d${DownloadUtil.EXTENSION}"
        val FULL_OUTPUT_TEMPLATE =
            "${DownloadUtil.BASENAME}.strip-full${DownloadUtil.EXTENSION}"

        fun cancel(taskId: String): Boolean = StripConcatProcessor.cancel(taskId)
    }
}
