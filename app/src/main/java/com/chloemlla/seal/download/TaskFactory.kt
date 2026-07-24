package com.chloemlla.seal.download

import androidx.annotation.CheckResult
import com.chloemlla.seal.database.objects.CommandTemplate
import com.chloemlla.seal.download.Task.DownloadState.Idle
import com.chloemlla.seal.download.Task.DownloadState.ReadyWithInfo
import com.chloemlla.seal.integration.ExternalDownloadCoordinator
import com.chloemlla.seal.util.DownloadUtil.DownloadPreferences
import com.chloemlla.seal.util.Format
import com.chloemlla.seal.util.PlaylistResult
import com.chloemlla.seal.util.VideoClip
import com.chloemlla.seal.util.VideoInfo
import kotlin.math.roundToInt

object TaskFactory {
    /**
     * @return A [TaskWithState] with extra configurations made by user in the custom format
     *   selection page.
     *
     * When an external delegate session is active, task-scoped cookies and keep_sections
     * (as [VideoClip]s) are preserved unless the user supplies an explicit clip plan.
     */
    @CheckResult
    fun createWithConfigurations(
        videoInfo: VideoInfo,
        formatList: List<Format>,
        videoClips: List<VideoClip>,
        splitByChapter: Boolean,
        newTitle: String,
        selectedSubtitles: List<String>,
        selectedAutoCaptions: List<String>,
    ): TaskWithState {
        val fileSize =
            formatList.fold(.0) { acc, format ->
                acc + (format.fileSize ?: format.fileSizeApprox ?: .0)
            }

        val info =
            videoInfo
                .run { if (fileSize != .0) copy(fileSize = fileSize) else this }
                .run { if (newTitle.isNotEmpty()) copy(title = newTitle) else this }

        val audioOnlyFormats = formatList.filter { it.isAudioOnly() }
        val videoFormats = formatList.filter { it.containsVideo() }
        val audioOnly = audioOnlyFormats.isNotEmpty() && videoFormats.isEmpty()
        val mergeAudioStream = audioOnlyFormats.size > 1
        val formatId = formatList.joinToString(separator = "+") { it.formatId.toString() }

        val subtitleLanguage =
            (selectedSubtitles + selectedAutoCaptions).joinToString(separator = ",")

        // Start from external-aware prefs so keep_sections / task cookies survive format pick.
        val sessionBase = ExternalDownloadCoordinator.buildPreferencesForSession()
        val effectiveClips =
            when {
                videoClips.isNotEmpty() -> videoClips
                sessionBase.videoClips.isNotEmpty() -> sessionBase.videoClips
                else -> emptyList()
            }

        val preferences =
            sessionBase
                .run {
                    copy(
                        formatIdString = formatId,
                        videoClips = effectiveClips,
                        splitByChapter = splitByChapter,
                        newTitle = newTitle,
                        mergeAudioStream = mergeAudioStream,
                        extractAudio = extractAudio || audioOnly,
                    )
                }
                .run {
                    if (subtitleLanguage.isNotEmpty()) {
                        copy(
                            downloadSubtitle = true,
                            autoSubtitle = selectedAutoCaptions.isNotEmpty(),
                            subtitleLanguage = subtitleLanguage,
                        )
                    } else {
                        this
                    }
                }

        val task = Task(url = info.originalUrl.toString(), preferences = preferences)
        val state =
            Task.State(
                downloadState = ReadyWithInfo,
                videoInfo = info,
                viewState =
                    Task.ViewState.fromVideoInfo(info = info)
                        .copy(videoFormats = videoFormats, audioOnlyFormats = audioOnlyFormats),
            )

        return TaskWithState(task, state)
    }

    /** @return List of [TaskWithState]s created from playlist items */
    @CheckResult
    fun createWithPlaylistResult(
        playlistUrl: String,
        indexList: List<Int>,
        playlistResult: PlaylistResult,
        preferences: DownloadPreferences,
    ): List<TaskWithState> {
        checkNotNull(playlistResult.entries)
        val indexEntryMap = indexList.associateWith { index -> playlistResult.entries[index - 1] }

        val taskList =
            indexEntryMap.map { (index, entry) ->
                val viewState =
                    Task.ViewState(
                        url = entry.url ?: "",
                        title = entry.title ?: "${playlistResult.title} - $index",
                        duration = entry.duration?.roundToInt() ?: 0,
                        uploader = entry.uploader ?: entry.channel ?: playlistResult.channel ?: "",
                        thumbnailUrl = (entry.thumbnails?.lastOrNull()?.url) ?: "",
                    )
                val task = Task(url = playlistUrl, preferences = preferences, type = Task.TypeInfo.Playlist(index))
                val state =
                    Task.State(downloadState = Idle, videoInfo = null, viewState = viewState)
                TaskWithState(task, state)
            }

        return taskList
    }

    /** Custom-command task bound to a [CommandTemplate] and raw URL text (multi-URL allowed). */
    @CheckResult
    fun createCustomCommand(
        url: String,
        template: CommandTemplate,
        preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ): TaskWithState {
        val task =
            Task(
                url = url,
                preferences = preferences,
                type = Task.TypeInfo.CustomCommand(template),
            )
        val state =
            Task.State(
                downloadState = Idle,
                videoInfo = null,
                viewState =
                    Task.ViewState(
                        url = url,
                        title = template.name,
                        uploader = template.name,
                    ),
                outputLog = "",
            )
        return TaskWithState(task, state)
    }

    data class TaskWithState(val task: Task, val state: Task.State)
}
