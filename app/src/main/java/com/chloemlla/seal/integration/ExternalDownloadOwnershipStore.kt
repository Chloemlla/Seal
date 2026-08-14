package com.chloemlla.seal.integration

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExternalDownloadOwnership(
    val taskId: String,
    val callerPackage: String,
    val callerRequestId: String? = null,
    val sourceUrl: String,
    val extractAudio: Boolean,
    val taskCookiesPath: String? = null,
    val stripRequested: Boolean = false,
    val paused: Boolean = false,
    val lastStatus: String = ExternalDownloadProtocol.STATUS_WAITING,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = createdAtMs,
) {
    val shouldMonitorAfterRestart: Boolean
        get() =
            lastStatus != ExternalDownloadProtocol.STATUS_COMPLETED &&
                lastStatus != ExternalDownloadProtocol.STATUS_CANCELED
}

/** Bounded task-to-caller authorization records for external queue control. */
object ExternalDownloadOwnershipStore {
    private const val TAG = "ExternalOwnership"
    private const val PREFS_NAME = "external_download_ownership"
    private const val KEY_RECORDS = "records_v1"
    internal const val MAX_RECORDS = 256
    private val json = Json { ignoreUnknownKeys = true }

    private var loaded = false
    private var records = linkedMapOf<String, ExternalDownloadOwnership>()

    @Synchronized
    fun put(context: Context, ownership: ExternalDownloadOwnership) {
        ensureLoaded(context)
        records[ownership.taskId] = ownership
        persist(context)
    }

    @Synchronized
    fun get(context: Context, taskId: String): ExternalDownloadOwnership? {
        ensureLoaded(context)
        return records[taskId]
    }

    @Synchronized
    fun findUniqueByRequestId(
        context: Context,
        callerRequestId: String,
    ): ExternalDownloadOwnership? {
        ensureLoaded(context)
        val matches = records.values.filter { it.callerRequestId == callerRequestId }
        return matches.singleOrNull()
    }

    @Synchronized
    fun hasRequestId(context: Context, callerRequestId: String): Boolean {
        ensureLoaded(context)
        return records.values.any { it.callerRequestId == callerRequestId }
    }

    @Synchronized
    fun update(
        context: Context,
        taskId: String,
        transform: (ExternalDownloadOwnership) -> ExternalDownloadOwnership,
    ): ExternalDownloadOwnership? {
        ensureLoaded(context)
        val current = records[taskId] ?: return null
        val next = transform(current).copy(updatedAtMs = System.currentTimeMillis())
        records[taskId] = next
        persist(context)
        return next
    }

    @Synchronized
    fun remove(context: Context, taskId: String): ExternalDownloadOwnership? {
        ensureLoaded(context)
        val removed = records.remove(taskId)
        if (removed != null) persist(context)
        return removed
    }

    @Synchronized
    fun monitorable(context: Context): List<ExternalDownloadOwnership> {
        ensureLoaded(context)
        return records.values.filter { it.shouldMonitorAfterRestart }
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val raw =
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_RECORDS, null)
        records =
            runCatching {
                    if (raw.isNullOrBlank()) emptyList()
                    else json.decodeFromString<List<ExternalDownloadOwnership>>(raw)
                }
                .onFailure { Log.w(TAG, "ownership decode failed; starting empty", it) }
                .getOrDefault(emptyList())
                .let(::boundExternalDownloadOwnerships)
                .associateByTo(linkedMapOf()) { it.taskId }
        loaded = true
    }

    @Synchronized
    private fun persist(context: Context) {
        val bounded = boundExternalDownloadOwnerships(records.values)
        records = bounded.associateByTo(linkedMapOf()) { it.taskId }
        val encoded = json.encodeToString(bounded)
        val saved =
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECORDS, encoded)
                .commit()
        if (!saved) Log.w(TAG, "ownership persistence failed")
    }
}

internal fun boundExternalDownloadOwnerships(
    source: Iterable<ExternalDownloadOwnership>,
): List<ExternalDownloadOwnership> {
    return source
        .associateBy { it.taskId }
        .values
        .sortedByDescending { it.updatedAtMs }
        .take(ExternalDownloadOwnershipStore.MAX_RECORDS)
}

internal fun validateExternalDownloadOwnership(
    callingPackage: String?,
    requestedRequestId: String?,
    ownership: ExternalDownloadOwnership,
): String? {
    val caller = callingPackage?.trim().orEmpty()
    if (caller.isEmpty() || caller != ownership.callerPackage) {
        return ExternalDownloadProtocol.ERROR_CALLER_DENIED
    }
    val requestId = requestedRequestId?.trim().orEmpty()
    if (requestId.isNotEmpty() && requestId != ownership.callerRequestId) {
        return ExternalDownloadProtocol.ERROR_CALLER_DENIED
    }
    return null
}

internal fun recoverExternalDownloadOwnershipAfterRestart(
    ownership: ExternalDownloadOwnership,
): ExternalDownloadOwnership {
    if (ownership.paused) return ownership
    return when (ownership.lastStatus) {
        ExternalDownloadProtocol.STATUS_WAITING,
        ExternalDownloadProtocol.STATUS_DOWNLOADING ->
            ownership.copy(
                paused = true,
                lastStatus = ExternalDownloadProtocol.STATUS_PAUSED,
            )
        else -> ownership
    }
}
