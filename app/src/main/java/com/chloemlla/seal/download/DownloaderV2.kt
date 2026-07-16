package com.chloemlla.seal.download

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.chloemlla.seal.App
import com.chloemlla.seal.R
import com.chloemlla.seal.download.Task.DownloadState
import com.chloemlla.seal.download.Task.DownloadState.Canceled
import com.chloemlla.seal.download.Task.DownloadState.Completed
import com.chloemlla.seal.download.Task.DownloadState.Error
import com.chloemlla.seal.download.Task.DownloadState.FetchingInfo
import com.chloemlla.seal.download.Task.DownloadState.Idle
import com.chloemlla.seal.download.Task.DownloadState.ReadyWithInfo
import com.chloemlla.seal.download.Task.DownloadState.Running
import com.chloemlla.seal.download.Task.RestartableAction.Download
import com.chloemlla.seal.download.Task.RestartableAction.FetchInfo
import com.chloemlla.seal.download.Task.TypeInfo
import com.chloemlla.seal.util.DownloadUtil
import com.chloemlla.seal.util.FileUtil
import com.chloemlla.seal.util.NotificationUtil
import com.chloemlla.seal.util.COMMAND_DIRECTORY
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.PreferenceUtil.getString
import com.chloemlla.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private const val TAG = "DownloaderV2"

private const val MAX_CONCURRENCY = 3
private const val TASK_BACKUP_DEBOUNCE_MS = 750L
/** Quantize progress into 5% steps for backup fingerprinting. */
private const val PROGRESS_BUCKETS = 20
private const val PROGRESS_BUCKET_NONE = -2
private const val PROGRESS_MISSING = -1f
private const val OUTPUT_LOG_BUCKET_CHARS = 4 * 1024

interface DownloaderV2 {
    fun getTaskStateMap(): SnapshotStateMap<Task, Task.State>

    fun cancel(task: Task): Boolean

    fun cancel(taskId: String): Boolean {
        return getTaskStateMap().keys.find { it.id == taskId }?.let { cancel(it) } ?: false
    }

    fun restart(task: Task)

    /** Enqueue a [Task] with an empty [Task.State] */
    fun enqueue(task: Task)

    fun enqueue(task: Task, state: Task.State)

    fun enqueue(taskWithState: TaskFactory.TaskWithState) {
        val (task, state) = taskWithState
        enqueue(task, state)
    }

    fun remove(task: Task): Boolean

    /**
     * Persist the current non-completed queue immediately.
     * Used when the process is backgrounded so debounced MMKV writes are not lost.
     */
    fun flushPendingBackup() {}
}

internal object FakeDownloaderV2 : DownloaderV2 {
    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return mutableStateMapOf()
    }

    override fun cancel(task: Task): Boolean {
        return false
    }

    override fun restart(task: Task) {}

    override fun enqueue(task: Task) {}

    override fun enqueue(task: Task, state: Task.State) {}

    override fun remove(task: Task): Boolean {
        return true
    }
}

/**
 * Primary download engine (queue, backup, notifications, custom commands).
 */
@OptIn(FlowPreview::class)
class DownloaderV2Impl(private val appContext: Context) : DownloaderV2, KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskStateMap = mutableStateMapOf<Task, Task.State>()
    private val snapshotFlow = snapshotFlow { taskStateMap.toMap() }

    init {
        scope.launch(Dispatchers.Default) {
            snapshotFlow
                .onEach { doYourWork() }
                .map { it.countRunning() }
                .distinctUntilChanged()
                .collect { if (it > 0) App.startService() else App.stopService() }
        }

        scope.launch(Dispatchers.IO) {
            // don't write before we read
            enqueueFromBackup()

            snapshotFlow
                .map { it.filter { it.value.downloadState !is Completed } }
                // Skip pure progress noise: only 5% buckets / structure / titles matter.
                .distinctUntilChangedBy { it.toTaskBackupFingerprint() }
                // Debounce MMKV writes so progress ticks do not rewrite large JSON every frame.
                .debounce(TASK_BACKUP_DEBOUNCE_MS)
                .collect {
                    it.forEach { Log.d(TAG, it.value.viewState.title) }
                    PreferenceUtil.encodeTaskListBackup(it)
                }
        }
    }

    override fun flushPendingBackup() {
        val pending = taskStateMap.filterValues { it.downloadState !is Completed }
        PreferenceUtil.encodeTaskListBackup(pending)
    }

    private fun enqueueFromBackup() {
        val taskList =
            PreferenceUtil.decodeTaskListBackup()
                .filter { it.value.downloadState !is Completed }
                .mapValues { (_, state) ->
                    val preState = state.downloadState
                    val downloadState =
                        when (preState) {
                            is FetchingInfo,
                            Idle -> {
                                Canceled(action = FetchInfo)
                            }
                            is Running -> {
                                Canceled(action = Download, progress = preState.progress)
                            }

                            ReadyWithInfo -> {
                                Canceled(action = Download, progress = null)
                            }
                            else -> {
                                preState
                            }
                        }
                    state.copy(downloadState = downloadState)
                }
        taskList.forEach(::enqueue)
    }

    private fun Map<Task, Task.State>.countRunning(): Int = count { (_, state) ->
        state.downloadState is Running || state.downloadState is FetchingInfo
    }

    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return taskStateMap
    }

    override fun enqueue(task: Task) {
        taskStateMap +=
            task to Task.State(Idle, null, Task.ViewState(url = task.url, title = task.url))
    }

    override fun enqueue(task: Task, state: Task.State) {
        taskStateMap += task to state
    }

    /**
     * Noted the caller is responsible for stopping the [task] before removing it
     *
     * @return true if the task was removed
     */
    override fun remove(task: Task): Boolean {
        if (taskStateMap.contains(task)) {
            taskStateMap.remove(task)
            return true
        }
        return false
    }

    override fun cancel(task: Task): Boolean = task.cancelImpl()

    override fun restart(task: Task) {
        task.restartImpl()
    }

    private var Task.state: Task.State
        get() =
            taskStateMap[this]
                ?: error("Task state missing for ${id}; task was removed or never enqueued")
        set(value) {
            taskStateMap[this] = value
        }

    private var Task.downloadState: DownloadState
        get() = state.downloadState
        set(value) {
            val prevState = state
            taskStateMap[this] = prevState.copy(downloadState = value)
        }

    private var Task.info: VideoInfo?
        get() = state.videoInfo
        set(value) {
            val prevState = state
            taskStateMap[this] = prevState.copy(videoInfo = value)
        }

    private var Task.viewState: Task.ViewState
        get() = state.viewState
        set(value) {
            val prevState = state
            taskStateMap[this] = prevState.copy(viewState = value)
        }

    private val Task.notificationId: Int
        get() = id.hashCode()

    /** Processes pending tasks, prioritizing downloads. */
    private fun doYourWork() {
        if (taskStateMap.countRunning() >= MAX_CONCURRENCY) return

        taskStateMap.entries
            .sortedBy { (_, state) -> state.downloadState }
            .firstOrNull { (_, state) ->
                state.downloadState == ReadyWithInfo || state.downloadState == Idle
            }
            ?.let { (task, state) ->
                when (state.downloadState) {
                    Idle -> task.prepare()
                    ReadyWithInfo -> task.download()
                    else -> {
                        throw IllegalStateException()
                    }
                }
            }
    }

    private fun Task.prepare() {
        check(downloadState == Idle)
        if (type is TypeInfo.CustomCommand) {
            execute()
        } else {
            fetchInfo()
        }
    }

    private fun Task.fetchInfo() {
        check(downloadState == Idle)
        val task = this
        val taskInfo = task.type
        val playlistIndex = if (taskInfo is TypeInfo.Playlist) taskInfo.index else null
        scope
            .launch(Dispatchers.Default) {
                DownloadUtil.fetchVideoInfoFromUrl(
                        url = url,
                        playlistIndex = playlistIndex,
                        preferences = preferences,
                        taskKey = id,
                    )
                    .onSuccess {
                        info = it
                        downloadState = ReadyWithInfo
                        viewState = Task.ViewState.fromVideoInfo(it)
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        task.downloadState = Error(throwable = throwable, action = FetchInfo)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = FetchingInfo(job = job, taskId = id) }
    }

    private fun Task.download() {
        if (type is TypeInfo.CustomCommand) {
            execute()
            return
        }
        check(downloadState == ReadyWithInfo && info != null)
        scope
            .launch(Dispatchers.Default) {
                DownloadUtil.downloadVideo(
                        videoInfo = info,
                        taskId = id,
                        downloadPreferences = preferences,
                        progressCallback = { progressPercentage, _, text ->
                            val progress = progressPercentage / 100f
                            when (val preState = downloadState) {
                                is Running -> {
                                    downloadState =
                                        preState.copy(progress = progress, progressText = text)
                                    NotificationUtil.notifyProgress(
                                        notificationId = notificationId,
                                        progress = progressPercentage.toInt(),
                                        text = text,
                                        title = viewState.title,
                                        taskId = id,
                                    )
                                }
                                else -> {}
                            }
                        },
                    )
                    .onSuccess { pathList ->
                        downloadState = Completed(pathList.firstOrNull())

                        val text =
                            appContext.getString(
                                if (pathList.isEmpty()) R.string.status_completed
                                else R.string.download_finish_notification
                            )
                        NotificationUtil.finishNotification(
                            notificationId,
                            title = viewState.title,
                            text = text,
                            intent =
                                NotificationUtil.createOpenFilePendingIntent(
                                    notificationId = notificationId,
                                    path = pathList.firstOrNull(),
                                ),
                        )
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        downloadState = Error(throwable = throwable, action = Download)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.fetch_info_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = Running(job = job, taskId = id) }
    }

    private fun Task.cancelImpl(): Boolean {
        when (val preState = downloadState) {
            is DownloadState.Cancelable -> {
                val res = YoutubeDL.destroyProcessById(preState.taskId)
                if (res) {
                    preState.job.cancel()
                    val progress = if (preState is Running) preState.progress else null
                    NotificationUtil.cancelNotification(notificationId)
                    downloadState =
                        DownloadState.Canceled(action = preState.action, progress = progress)
                }
                return res
            }
            Idle -> {
                downloadState = DownloadState.Canceled(action = FetchInfo)
            }
            ReadyWithInfo -> {
                downloadState = DownloadState.Canceled(action = Download)
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun Task.restartImpl() {
        when (val preState = downloadState) {
            is DownloadState.Restartable -> {
                downloadState =
                    when (preState.action) {
                        Download -> ReadyWithInfo
                        FetchInfo -> Idle
                    }
            }
            else -> {
                throw IllegalStateException()
            }
        }
    }

    /**
     * Execute a custom command task
     *
     * @see Task.TypeInfo.CustomCommand
     */
    private fun Task.execute() {
        check(type is TypeInfo.CustomCommand)
        check(downloadState == Idle || downloadState == ReadyWithInfo)
        val template = type.template
        // Fresh run: reset accumulated log (matches V1 restart behavior).
        state = state.copy(outputLog = "")
        scope
            .launch {
                DownloadUtil.executeCustomCommandTask(url, id, template, preferences) {
                        progressPercentage,
                        _,
                        text ->
                        val progress = progressPercentage / 100f
                        when (val preState = downloadState) {
                            is Running -> {
                                val line = if (text.endsWith("\n")) text else "$text\n"
                                val nextLog = appendOutputLog(state.outputLog, line)
                                state =
                                    state.copy(
                                        downloadState =
                                            preState.copy(progress = progress, progressText = text),
                                        outputLog = nextLog,
                                    )
                                NotificationUtil.makeNotificationForCustomCommand(
                                    notificationId = notificationId,
                                    taskId = id,
                                    progress = progressPercentage.toInt(),
                                    templateName = template.name,
                                    taskUrl = url,
                                    text = text,
                                )
                            }
                            else -> {}
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is YoutubeDL.CanceledException) {
                            return@onFailure
                        }
                        val report = throwable.stackTraceToString()
                        state =
                            state.copy(
                                downloadState = Error(throwable = throwable, action = Download),
                                outputLog = appendOutputLog(state.outputLog, report + "\n"),
                            )
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.fetch_info_error_msg,
                            notificationId = notificationId,
                            report = report,
                        )
                    }
                    .onSuccess { response ->
                        val combined =
                            listOf(response.out, response.err)
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                        val nextLog =
                            when {
                                combined.isBlank() -> state.outputLog
                                state.outputLog.contains(combined) -> state.outputLog
                                else -> appendOutputLog(state.outputLog, combined + "\n")
                            }
                        state =
                            state.copy(
                                downloadState = Completed(null),
                                outputLog = nextLog,
                            )
                        FileUtil.scanDownloadDirectoryToMediaLibrary(COMMAND_DIRECTORY.getString())

                        val text = appContext.getString(R.string.status_completed)

                        NotificationUtil.finishNotification(
                            notificationId = notificationId,
                            title = viewState.title,
                            text = text,
                            intent = null,
                        )
                    }
            }
            .also { downloadState = Running(job = it, taskId = id) }
    }
}

/** Cap retained custom-command logs to avoid unbounded MMKV growth. */
private const val MAX_OUTPUT_LOG_CHARS = 512 * 1024

private fun appendOutputLog(existing: String, addition: String): String {
    if (addition.isEmpty()) return existing
    val merged = existing + addition
    return if (merged.length <= MAX_OUTPUT_LOG_CHARS) {
        merged
    } else {
        merged.takeLast(MAX_OUTPUT_LOG_CHARS)
    }
}

/**
 * Structural fingerprint for queue MMKV backups.
 * Progress is quantized to ~5% buckets and logs to 4 KiB buckets so frequent callbacks
 * do not force full JSON rewrites while still retaining coarse resume state and logs.
 */
internal fun Map<Task, Task.State>.toTaskBackupFingerprint(): List<String> =
    entries
        .sortedBy { it.key.id }
        .map { (task, state) ->
            val downloadState = state.downloadState
            val kind =
                when (downloadState) {
                    Idle -> "I"
                    is FetchingInfo -> "F"
                    ReadyWithInfo -> "W"
                    is Running -> "R"
                    is Canceled -> "C:${downloadState.action}"
                    is Error -> "E:${downloadState.action}"
                    is Completed -> "D"
                }
            val progressBucket =
                when (downloadState) {
                    is Running -> (downloadState.progress * PROGRESS_BUCKETS).toInt()
                    is Canceled ->
                        ((downloadState.progress ?: PROGRESS_MISSING) * PROGRESS_BUCKETS).toInt()
                    else -> PROGRESS_BUCKET_NONE
                }
            val outputLogBucket = state.outputLog.length / OUTPUT_LOG_BUCKET_CHARS
            "${task.id}|$kind|$progressBucket|$outputLogBucket|${state.viewState.title}"
        }

