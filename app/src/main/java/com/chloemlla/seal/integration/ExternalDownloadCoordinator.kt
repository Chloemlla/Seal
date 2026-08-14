package com.chloemlla.seal.integration

import android.content.Context
import android.util.Log
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.Task
import com.chloemlla.seal.util.DownloadUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles L2 enqueue + L3 directed status broadcasts for external callers.
 * Seal remains the only owner of download execution.
 */
object ExternalDownloadCoordinator {
    private const val TAG = "ExternalDownloadCoord"
    private val recentCallerHits = ConcurrentHashMap<String, LongArray>()

    data class ExternalSession(
        val callerPackage: String,
        val callerRequestId: String?,
        /** From external extract_audio extra; null means caller did not specify. */
        val extractAudio: Boolean? = null,
        /** Task-scoped cookies file path after materialize; null if none. */
        val taskCookiesPath: String? = null,
        val cookiesMid: Long? = null,
        val stripSegments: Boolean = false,
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

    fun beginExternalSession(
        callerPackage: String?,
        callerRequestId: String?,
        extractAudio: Boolean? = null,
        taskCookiesPath: String? = null,
        cookiesMid: Long? = null,
        stripSegments: Boolean = false,
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
                    stripSegments = stripSegments,
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
                "strip=$stripSegments keepSections=${keepSections.size}",
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
                ExternalDownloadTaskOutput.deleteCookies(
                    context = context,
                    callerRequestId = cancelTarget.callerRequestId,
                    taskCookiesPath = cancelTarget.taskCookiesPath,
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
            ExternalDownloadTaskMonitor.registerAndWatch(
                context = appContext,
                downloader = downloader,
                task = task,
                callerPackage = session.callerPackage,
                callerRequestId = session.callerRequestId,
                taskCookiesPath = session.taskCookiesPath,
                stripRequested = task.preferences.stripKeepSections.isNotEmpty(),
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
        val stripSections =
            if (request.stripSegments) request.keepSections else emptyList()
        val ordinarySections =
            if (!request.stripSegments && request.keepSections.isNotEmpty()) {
                request.keepSections
            } else if (request.stripSegments) {
                emptyList()
            } else {
                base.videoClips
            }
        return base.copy(
            extractAudio = request.extractAudio ?: base.extractAudio,
            downloadSubtitle = request.downloadSubtitle ?: base.downloadSubtitle,
            cookies = if (taskCookies != null) true else base.cookies,
            cookiesFilePath = taskCookies ?: base.cookiesFilePath,
            sponsorBlock = if (request.stripSegments) false else base.sponsorBlock,
            videoClips = ordinarySections,
            stripKeepSections = stripSections,
            // External path never injects custom command templates.
        ).also {
            if (forceCookies) {
                Log.i(
                    TAG,
                    "buildPreferences cookies=true taskPath=${taskCookies != null} " +
                        "mid=${request.cookiesMid} strip=${request.stripSegments} " +
                        "keepSections=${request.keepSections.size}",
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
        val stripSegments = request?.stripSegments ?: delegate?.stripSegments ?: false
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
            sponsorBlock = if (stripSegments) false else base.sponsorBlock,
            videoClips = if (stripSegments) emptyList() else sections,
            stripKeepSections = if (stripSegments) sections else emptyList(),
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
                    val ownerPackage = callerPackage?.trim().orEmpty()
                    if (ownerPackage.isNotEmpty()) {
                        ExternalDownloadTaskMonitor.registerAndWatch(
                            context = context.applicationContext,
                            downloader = downloader,
                            task = task,
                            callerPackage = ownerPackage,
                            callerRequestId = prepared.callerRequestId,
                            taskCookiesPath = prepared.taskCookiesPath,
                            stripRequested = prepared.stripSegments,
                        )
                    }
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

    sealed interface EnqueueResult {
        data class Success(val taskIds: List<String>) : EnqueueResult

        data class Failure(val errorCode: String, val message: String) : EnqueueResult
    }
}
