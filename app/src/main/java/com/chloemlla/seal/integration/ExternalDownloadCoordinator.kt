package com.chloemlla.seal.integration

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.Task
import com.chloemlla.seal.download.Task.DownloadState
import com.chloemlla.seal.util.DownloadUtil
import com.chloemlla.seal.util.FileUtil.getFileProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

/**
 * Handles L2 enqueue + L3 directed status broadcasts for external callers.
 * Seal remains the only owner of download execution.
 */
object ExternalDownloadCoordinator {
    private const val TAG = "ExternalDownloadCoord"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watchedTasks = ConcurrentHashMap<String, WatchedTask>()
    private val recentCallerHits = ConcurrentHashMap<String, LongArray>()

    data class ExternalSession(
        val callerPackage: String,
        val callerRequestId: String?,
        /** From external extract_audio extra; null means caller did not specify. */
        val extractAudio: Boolean? = null,
        /** Task-scoped cookies file path after materialize; null if none. */
        val taskCookiesPath: String? = null,
        val cookiesMid: Long? = null,
        val keepSections: List<com.chloemlla.seal.util.VideoClip> = emptyList(),
        @Volatile var enqueuedDuringSession: Boolean = false,
    )

    @Volatile private var externalSession: ExternalSession? = null

    /**
     * Sticky overrides for the last external UI delegate. Survives intermediate
     * [endExternalSession]/[currentSession] clears that happen when the configure
     * sheet hides before FormatPage enqueues (quality / detailed format path).
     * Cleared only after a successful external enqueue or a true cancel.
     */
    @Volatile private var stickyDelegate: ExternalSession? = null

    private data class WatchedTask(
        val taskId: String,
        val callerPackage: String?,
        val callerRequestId: String?,
        val taskCookiesPath: String? = null,
        val terminalSent: AtomicBoolean = AtomicBoolean(false),
        var job: Job? = null,
    )

    fun beginExternalSession(
        callerPackage: String?,
        callerRequestId: String?,
        extractAudio: Boolean? = null,
        taskCookiesPath: String? = null,
        cookiesMid: Long? = null,
        keepSections: List<com.chloemlla.seal.util.VideoClip> = emptyList(),
    ) {
        val pkg = callerPackage?.trim().orEmpty()
        val next =
            if (pkg.isEmpty()) {
                null
            } else {
                ExternalSession(
                    callerPackage = pkg,
                    callerRequestId = callerRequestId,
                    extractAudio = extractAudio,
                    taskCookiesPath = taskCookiesPath,
                    cookiesMid = cookiesMid,
                    keepSections = keepSections,
                )
            }
        externalSession = next
        stickyDelegate = next
        Log.i(
            TAG,
            "beginExternalSession pkg=${externalSession?.callerPackage} " +
                "reqId=$callerRequestId extractAudio=$extractAudio " +
                "cookies=${!taskCookiesPath.isNullOrBlank()} mid=$cookiesMid " +
                "keepSections=${keepSections.size}",
        )
    }

    fun endExternalSession(notifyCanceledIfEmpty: Boolean = false, context: Context? = null) {
        val session = externalSession
        val sticky = stickyDelegate
        externalSession = null
        // Prefer live session; fall back to sticky for cancel after intermediate sheet hide.
        val cancelTarget =
            when {
                session != null && !session.enqueuedDuringSession -> session
                sticky != null && !sticky.enqueuedDuringSession -> sticky
                else -> null
            }
        // True cancel: no enqueue yet. Intermediate sheet hide keeps sticky so
        // FormatPage quality selection can still merge keep_sections.
        if (notifyCanceledIfEmpty && cancelTarget != null) {
            stickyDelegate = null
            if (context != null) {
                deleteTaskCookiesForRequest(
                    context,
                    cancelTarget.callerRequestId,
                    cancelTarget.taskCookiesPath,
                )
                ExternalDownloadStatusReporter.sendStatus(
                    context = context.applicationContext,
                    targetPackage = cancelTarget.callerPackage,
                    status = ExternalDownloadProtocol.STATUS_CANCELED,
                    errorCode = ExternalDownloadProtocol.ERROR_CANCELED,
                    callerRequestId = cancelTarget.callerRequestId,
                )
                Log.i(
                    TAG,
                    "endExternalSession canceled (no enqueue) pkg=${cancelTarget.callerPackage}",
                )
            } else {
                Log.i(TAG, "endExternalSession canceled (no enqueue, no context)")
            }
            return
        }
        // Intermediate clear (format sheet hide / activity pause): keep sticky overrides.
        if (cancelTarget != null && sticky != null) {
            Log.d(
                TAG,
                "endExternalSession keep sticky keepSections=${sticky.keepSections.size} " +
                    "pkg=${sticky.callerPackage}",
            )
        } else {
            Log.d(
                TAG,
                "endExternalSession pkg=${session?.callerPackage} enqueued=${session?.enqueuedDuringSession}",
            )
        }
    }

    fun currentSession(): ExternalSession? = externalSession

    /** Active session, or sticky delegate left after intermediate sheet hide. */
    fun resolveDelegateSession(): ExternalSession? = externalSession ?: stickyDelegate

    /**
     * UI / ViewModel should call this after [DownloaderV2.enqueue].
     * No-op when there is no external session (in-app downloads unchanged).
     */
    fun watchEnqueuedTaskIfExternal(
        context: Context,
        downloader: DownloaderV2,
        task: Task,
        alsoNotifyAccepted: Boolean = true,
    ) {
        watchEnqueuedTasksIfExternal(
            context = context,
            downloader = downloader,
            tasks = listOf(task),
            alsoNotifyAccepted = alsoNotifyAccepted,
        )
    }

    fun watchEnqueuedTasksIfExternal(
        context: Context,
        downloader: DownloaderV2,
        tasks: List<Task>,
        alsoNotifyAccepted: Boolean = true,
    ) {
        val session = resolveDelegateSession()
        if (session == null) {
            Log.w(
                TAG,
                "watchEnqueuedTasksIfExternal skipped: no external session " +
                    "(tasks=${tasks.size}). UI path needs beginExternalSession first.",
            )
            return
        }
        if (tasks.isEmpty()) return
        session.enqueuedDuringSession = true
        val appContext = context.applicationContext
        val taskIds = mutableListOf<String>()
        tasks.forEach { task ->
            taskIds += task.id
            watchTask(
                context = appContext,
                downloader = downloader,
                taskId = task.id,
                callerPackage = session.callerPackage,
                callerRequestId = session.callerRequestId,
                taskCookiesPath = session.taskCookiesPath,
            )
        }
        if (alsoNotifyAccepted) {
            notifyAccepted(context = appContext, taskIds = taskIds, session = session)
        }
        // Enqueue complete: drop live + sticky so later in-app downloads are not tagged.
        if (externalSession === session) {
            externalSession = null
        }
        if (stickyDelegate === session || stickyDelegate?.callerRequestId == session.callerRequestId) {
            stickyDelegate = null
        }
        Log.i(
            TAG,
            "watchEnqueuedTasksIfExternal count=${taskIds.size} pkg=${session.callerPackage} " +
                "reqId=${session.callerRequestId} ids=$taskIds",
        )
    }

    fun notifyAcceptedForSession(context: Context, taskIds: List<String>) {
        val session = resolveDelegateSession() ?: return
        if (taskIds.isEmpty()) return
        session.enqueuedDuringSession = true
        notifyAccepted(context = context.applicationContext, taskIds = taskIds, session = session)
    }

    private fun notifyAccepted(
        context: Context,
        taskIds: List<String>,
        session: ExternalSession,
    ) {
        ExternalDownloadStatusReporter.sendStatus(
            context = context,
            targetPackage = session.callerPackage,
            status = ExternalDownloadProtocol.STATUS_ACCEPTED,
            errorCode = ExternalDownloadProtocol.ERROR_OK,
            taskId = taskIds.firstOrNull(),
            taskIds = taskIds,
            callerRequestId = session.callerRequestId,
        )
        Log.i(
            TAG,
            "notifyAccepted count=${taskIds.size} pkg=${session.callerPackage} reqId=${session.callerRequestId}",
        )
    }

    fun isRateLimitOk(callerPackage: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val key = callerPackage?.ifBlank { null } ?: "unknown"
        val windowMs = 60_000L
        val maxHits = 20
        val hits = recentCallerHits[key]?.toMutableList() ?: mutableListOf()
        hits.removeAll { nowMs - it > windowMs }
        if (hits.size >= maxHits) {
            recentCallerHits[key] = hits.toLongArray()
            return false
        }
        hits += nowMs
        recentCallerHits[key] = hits.toLongArray()
        return true
    }

    /**
     * Materialize cookies if present; returns updated request or Failure code/message.
     * Call before auto-start enqueue and before NeedsUi session begin.
     */
    fun prepareRequestCookies(
        context: Context,
        request: ExternalDownloadRequest,
    ): PrepareCookiesResult {
        if (!request.hasCookiePayload) {
            return PrepareCookiesResult.Ok(request)
        }
        if (request.protocolVersion < 2) {
            return PrepareCookiesResult.Ok(request.clearedCookies())
        }
        val materialize =
            ExternalCookieMaterializer.materialize(
                context = context.applicationContext,
                request = request,
            )
        return when (materialize) {
            is ExternalCookieMaterializer.Result.Failure -> {
                if (request.cookiesRequired) {
                    PrepareCookiesResult.Error(materialize.errorCode, materialize.message)
                } else {
                    // Soft: strip cookies and continue without them.
                    Log.w(
                        TAG,
                        "cookie materialize failed code=${materialize.errorCode}; continuing without cookies",
                    )
                    PrepareCookiesResult.Ok(request.clearedCookies())
                }
            }
            is ExternalCookieMaterializer.Result.Success -> {
                PrepareCookiesResult.Ok(
                    request.withTaskCookiesPath(materialize.filePath)
                )
            }
        }
    }

    sealed interface PrepareCookiesResult {
        data class Ok(val request: ExternalDownloadRequest) : PrepareCookiesResult

        data class Error(val errorCode: String, val message: String) : PrepareCookiesResult
    }

    fun buildPreferences(request: ExternalDownloadRequest): DownloadUtil.DownloadPreferences {
        val base = DownloadUtil.DownloadPreferences.createFromPreferences()
        val taskCookies = request.taskCookiesPath?.takeIf { it.isNotBlank() }
        val forceCookies = taskCookies != null || (request.useCookies == true && taskCookies != null)
        val sections =
            if (request.keepSections.isNotEmpty()) request.keepSections else base.videoClips
        return base.copy(
            extractAudio = request.extractAudio ?: base.extractAudio,
            downloadSubtitle = request.downloadSubtitle ?: base.downloadSubtitle,
            cookies = if (taskCookies != null) true else base.cookies,
            cookiesFilePath = taskCookies ?: base.cookiesFilePath,
            videoClips = sections,
            // External path never injects custom command templates.
        ).also {
            if (forceCookies) {
                Log.i(
                    TAG,
                    "buildPreferences cookies=true taskPath=${taskCookies != null} " +
                        "mid=${request.cookiesMid} keepSections=${sections.size}",
                )
            }
        }
    }

    /**
     * Preferences for UI path: merge external request overrides into current base prefs.
     * Prefer [buildPreferences] when full request is available.
     */
    fun buildPreferencesForSession(
        request: ExternalDownloadRequest? = null,
        session: ExternalSession? = resolveDelegateSession(),
    ): DownloadUtil.DownloadPreferences {
        val base = DownloadUtil.DownloadPreferences.createFromPreferences()
        val delegate = session ?: resolveDelegateSession()
        val taskCookies =
            request?.taskCookiesPath?.takeIf { it.isNotBlank() }
                ?: delegate?.taskCookiesPath?.takeIf { it.isNotBlank() }
        val sections =
            when {
                request != null && request.keepSections.isNotEmpty() -> request.keepSections
                delegate != null && delegate.keepSections.isNotEmpty() -> delegate.keepSections
                else -> base.videoClips
            }
        return base.copy(
            extractAudio =
                request?.extractAudio
                    ?: delegate?.extractAudio
                    ?: base.extractAudio,
            downloadSubtitle = request?.downloadSubtitle ?: base.downloadSubtitle,
            cookies = if (taskCookies != null) true else base.cookies,
            cookiesFilePath = taskCookies ?: base.cookiesFilePath,
            videoClips = sections,
        )
    }

    fun enqueue(
        context: Context,
        downloader: DownloaderV2,
        request: ExternalDownloadRequest,
        callerPackage: String?,
    ): EnqueueResult {
        return runCatching {
                val prepared =
                    when (val p = prepareRequestCookies(context, request)) {
                        is PrepareCookiesResult.Error ->
                            return EnqueueResult.Failure(p.errorCode, p.message)
                        is PrepareCookiesResult.Ok -> p.request
                    }
                val preferences = buildPreferences(prepared)
                val taskIds = mutableListOf<String>()
                prepared.urls.forEach { url ->
                    val task = Task(url = url, preferences = preferences)
                    downloader.enqueue(task)
                    taskIds += task.id
                    watchTask(
                        context = context.applicationContext,
                        downloader = downloader,
                        taskId = task.id,
                        callerPackage = callerPackage,
                        callerRequestId = prepared.callerRequestId,
                        taskCookiesPath = prepared.taskCookiesPath,
                    )
                }
                EnqueueResult.Success(taskIds = taskIds)
            }
            .getOrElse {
                Log.e(TAG, "enqueue failed", it)
                EnqueueResult.Failure(
                    errorCode = ExternalDownloadProtocol.ERROR_INTERNAL,
                    message = it.message ?: "Failed to enqueue delegated download",
                )
            }
    }

    fun watchTask(
        context: Context,
        downloader: DownloaderV2,
        taskId: String,
        callerPackage: String?,
        callerRequestId: String?,
        taskCookiesPath: String? = null,
    ) {
        if (callerPackage.isNullOrBlank()) {
            Log.w(TAG, "watchTask skipped: blank callerPackage taskId=$taskId")
            return
        }
        val existing = watchedTasks[taskId]
        if (existing != null) {
            Log.d(TAG, "watchTask already watching taskId=$taskId")
            return
        }

        val watched =
            WatchedTask(
                taskId = taskId,
                callerPackage = callerPackage,
                callerRequestId = callerRequestId,
                taskCookiesPath = taskCookiesPath,
            )
        watchedTasks[taskId] = watched
        Log.i(TAG, "watchTask start taskId=$taskId pkg=$callerPackage reqId=$callerRequestId")

        watched.job =
            scope.launch {
                // SnapshotStateMap mutations are observed via snapshotFlow.
                // Map by task.id string so Task instance identity changes never drop the watch.
                snapshotFlow {
                        downloader
                            .getTaskStateMap()
                            .entries
                            .associate { (task, state) -> task.id to state }
                    }
                    .map { states -> states[taskId] }
                    .distinctUntilChanged { old, new ->
                        // Progress ticks on Running must not thrash collectors; only
                        // downloadState class + terminal payload fields matter for L3.
                        old.sameTerminalObservation(new)
                    }
                    .collect { state ->
                        if (state == null) {
                            // Task not yet present or temporarily removed — keep watching.
                            return@collect
                        }
                        when (val downloadState = state.downloadState) {
                            is DownloadState.Completed -> {
                                emitTerminalOnce(watched) {
                                    val filePath = downloadState.filePath
                                    val contentUri =
                                        filePath?.let {
                                            createContentUri(context, it, callerPackage)
                                        }
                                    val displayName =
                                        resolveDisplayName(
                                            viewTitle = state.viewState.title,
                                            filePath = filePath,
                                        )
                                    val mimeType = resolveMimeType(filePath)
                                    Log.i(
                                        TAG,
                                        "terminal COMPLETED taskId=$taskId " +
                                            "uri=${contentUri != null} name=$displayName mime=$mimeType",
                                    )
                                    ExternalDownloadStatusReporter.sendStatus(
                                        context = context,
                                        targetPackage = callerPackage,
                                        status = ExternalDownloadProtocol.STATUS_COMPLETED,
                                        errorCode = ExternalDownloadProtocol.ERROR_OK,
                                        taskId = taskId,
                                        callerRequestId = callerRequestId,
                                        contentUri = contentUri,
                                        displayName = displayName,
                                        mimeType = mimeType,
                                    )
                                    deleteTaskCookiesForRequest(
                                        context,
                                        callerRequestId,
                                        watched.taskCookiesPath,
                                    )
                                }
                            }
                            is DownloadState.Error -> {
                                emitTerminalOnce(watched) {
                                    Log.i(
                                        TAG,
                                        "terminal FAILED taskId=$taskId msg=${downloadState.throwable.message}",
                                    )
                                    ExternalDownloadStatusReporter.sendStatus(
                                        context = context,
                                        targetPackage = callerPackage,
                                        status = ExternalDownloadProtocol.STATUS_FAILED,
                                        errorCode =
                                            ExternalDownloadProtocol.ERROR_DOWNLOAD_FAILED,
                                        errorMessage = downloadState.throwable.message,
                                        taskId = taskId,
                                        callerRequestId = callerRequestId,
                                    )
                                    deleteTaskCookiesForRequest(
                                        context,
                                        callerRequestId,
                                        watched.taskCookiesPath,
                                    )
                                }
                            }
                            is DownloadState.Canceled -> {
                                emitTerminalOnce(watched) {
                                    Log.i(TAG, "terminal CANCELED taskId=$taskId")
                                    ExternalDownloadStatusReporter.sendStatus(
                                        context = context,
                                        targetPackage = callerPackage,
                                        status = ExternalDownloadProtocol.STATUS_CANCELED,
                                        errorCode = ExternalDownloadProtocol.ERROR_CANCELED,
                                        taskId = taskId,
                                        callerRequestId = callerRequestId,
                                    )
                                    deleteTaskCookiesForRequest(
                                        context,
                                        callerRequestId,
                                        watched.taskCookiesPath,
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
            }
    }

    private fun deleteTaskCookiesForRequest(
        context: Context,
        callerRequestId: String?,
        taskCookiesPath: String?,
    ) {
        runCatching {
            if (!callerRequestId.isNullOrBlank()) {
                ExternalCookieMaterializer.deleteTaskCookies(context, callerRequestId)
            }
            if (!taskCookiesPath.isNullOrBlank()) {
                val f = File(taskCookiesPath)
                if (f.exists()) f.delete()
            }
        }
            .onFailure { Log.w(TAG, "delete task cookies failed (non-fatal)") }
    }

    private fun emitTerminalOnce(watched: WatchedTask, block: () -> Unit) {
        if (!watched.terminalSent.compareAndSet(false, true)) {
            Log.d(TAG, "terminal already sent taskId=${watched.taskId}")
            return
        }
        runCatching(block).onFailure {
            // Allow a later retry if broadcast construction failed hard.
            watched.terminalSent.set(false)
            Log.e(TAG, "emit terminal failed taskId=${watched.taskId}", it)
        }
        if (watched.terminalSent.get()) {
            clearWatch(watched.taskId)
        }
    }

    private fun clearWatch(taskId: String) {
        watchedTasks.remove(taskId)?.job?.cancel()
    }

    private fun Task.State?.sameTerminalObservation(other: Task.State?): Boolean {
        if (this === other) return true
        if (this == null || other == null) return this == other
        val a = downloadState
        val b = other.downloadState
        return when {
            a is DownloadState.Completed && b is DownloadState.Completed ->
                a.filePath == b.filePath && viewState.title == other.viewState.title
            a is DownloadState.Error && b is DownloadState.Error ->
                a.throwable.message == b.throwable.message && a.action == b.action
            a is DownloadState.Canceled && b is DownloadState.Canceled -> a.action == b.action
            // Collapse Idle/Fetching/Ready/Running progress noise into one observation.
            a::class == b::class -> true
            else -> false
        }
    }

    private fun resolveDisplayName(viewTitle: String?, filePath: String?): String? {
        val fromPath =
            filePath
                ?.takeIf { it.isNotBlank() && !it.startsWith("content:", ignoreCase = true) }
                ?.let { File(it).name }
                ?.takeIf { it.isNotBlank() }
        if (!fromPath.isNullOrBlank()) return fromPath
        val title = viewTitle?.trim().orEmpty()
        return title.takeIf { it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") }
    }

    private fun resolveMimeType(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        if (filePath.startsWith("content:", ignoreCase = true)) return null
        val ext = File(filePath).extension.lowercase().ifBlank { return null }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    fun createContentUri(context: Context, path: String, callerPackage: String?): Uri? {
        return runCatching {
                val fileUri =
                    if (path.startsWith("content:", ignoreCase = true)) {
                        Uri.parse(path)
                    } else {
                        val file = File(path)
                        if (!file.exists()) {
                            Log.w(TAG, "createContentUri file missing: $path")
                            return null
                        }
                        FileProvider.getUriForFile(context, context.getFileProvider(), file)
                    }
                if (!callerPackage.isNullOrBlank()) {
                    context.grantUriPermission(
                        callerPackage,
                        fileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                fileUri
            }
            .onFailure { Log.e(TAG, "createContentUri failed for $path", it) }
            .getOrNull()
    }

    sealed interface EnqueueResult {
        data class Success(val taskIds: List<String>) : EnqueueResult

        data class Failure(val errorCode: String, val message: String) : EnqueueResult
    }
}

object ExternalDownloadStatusReporter {
    fun sendStatus(
        context: Context,
        targetPackage: String?,
        status: String,
        errorCode: String,
        errorMessage: String? = null,
        taskId: String? = null,
        taskIds: List<String>? = null,
        callerRequestId: String? = null,
        contentUri: Uri? = null,
        displayName: String? = null,
        mimeType: String? = null,
    ) {
        if (targetPackage.isNullOrBlank()) {
            Log.w("ExternalDownloadStatus", "sendStatus skipped: blank target status=$status")
            return
        }
        val intent =
            Intent(ExternalDownloadProtocol.ACTION_DOWNLOAD_STATUS).apply {
                setPackage(targetPackage)
                putExtra(
                    ExternalDownloadProtocol.EXTRA_PROTOCOL_VERSION,
                    ExternalDownloadProtocol.PROTOCOL_VERSION,
                )
                putExtra(ExternalDownloadProtocol.EXTRA_STATUS, status)
                putExtra(ExternalDownloadProtocol.EXTRA_ERROR_CODE, errorCode)
                errorMessage?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_ERROR_MESSAGE, it)
                }
                taskId?.let { putExtra(ExternalDownloadProtocol.EXTRA_TASK_ID, it) }
                taskIds?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_TASK_IDS, it.toTypedArray())
                }
                callerRequestId?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_CALLER_REQUEST_ID, it)
                }
                contentUri?.let {
                    putExtra(ExternalDownloadProtocol.EXTRA_CONTENT_URI, it.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                displayName?.let { putExtra(ExternalDownloadProtocol.EXTRA_DISPLAY_NAME, it) }
                mimeType?.let { putExtra(ExternalDownloadProtocol.EXTRA_MIME_TYPE, it) }
                putExtra(ExternalDownloadProtocol.EXTRA_CALLER_PACKAGE, targetPackage)
            }
        runCatching {
                context.sendBroadcast(intent)
                Log.i(
                    "ExternalDownloadStatus",
                    "broadcast status=$status task=$taskId pkg=$targetPackage reqId=$callerRequestId",
                )
            }
            .onFailure { Log.e("ExternalDownloadStatus", "broadcast failed status=$status", it) }
    }

    fun activityResultBundle(
        status: String,
        errorCode: String,
        errorMessage: String? = null,
        taskIds: List<String> = emptyList(),
        callerRequestId: String? = null,
    ): Intent {
        return Intent().apply {
            putExtra(
                ExternalDownloadProtocol.EXTRA_PROTOCOL_VERSION,
                ExternalDownloadProtocol.PROTOCOL_VERSION,
            )
            putExtra(ExternalDownloadProtocol.EXTRA_STATUS, status)
            putExtra(ExternalDownloadProtocol.EXTRA_ERROR_CODE, errorCode)
            errorMessage?.let { putExtra(ExternalDownloadProtocol.EXTRA_ERROR_MESSAGE, it) }
            if (taskIds.isNotEmpty()) {
                putExtra(ExternalDownloadProtocol.EXTRA_TASK_ID, taskIds.first())
                putExtra(ExternalDownloadProtocol.EXTRA_TASK_IDS, taskIds.toTypedArray())
            }
            callerRequestId?.let {
                putExtra(ExternalDownloadProtocol.EXTRA_CALLER_REQUEST_ID, it)
            }
        }
    }

    fun finishWithResult(
        activity: Activity,
        resultCode: Int,
        status: String,
        errorCode: String,
        errorMessage: String? = null,
        taskIds: List<String> = emptyList(),
        callerRequestId: String? = null,
        callerPackage: String? = null,
        alsoBroadcast: Boolean = true,
    ) {
        val data =
            activityResultBundle(
                status = status,
                errorCode = errorCode,
                errorMessage = errorMessage,
                taskIds = taskIds,
                callerRequestId = callerRequestId,
            )
        activity.setResult(resultCode, data)
        if (alsoBroadcast) {
            sendStatus(
                context = activity,
                targetPackage = callerPackage,
                status = status,
                errorCode = errorCode,
                errorMessage = errorMessage,
                taskId = taskIds.firstOrNull(),
                taskIds = taskIds,
                callerRequestId = callerRequestId,
            )
        }
    }
}
