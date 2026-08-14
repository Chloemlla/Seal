package com.chloemlla.seal.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.chloemlla.seal.util.FileUtil.getFileProvider
import java.io.File

internal data class ExternalDownloadOutput(
    val contentUri: Uri?,
    val displayName: String?,
    val mimeType: String?,
    val totalBytes: Long?,
)

internal object ExternalDownloadTaskOutput {
    private const val TAG = "ExternalTaskOutput"

    fun resolve(
        context: Context,
        path: String?,
        viewTitle: String?,
        callerPackage: String,
    ): ExternalDownloadOutput {
        if (path.isNullOrBlank()) {
            return ExternalDownloadOutput(
                contentUri = null,
                displayName = normalizedTitle(viewTitle),
                mimeType = null,
                totalBytes = null,
            )
        }
        val uri = createContentUri(context, path, callerPackage)
        val file = path.takeUnless { it.startsWith("content:", ignoreCase = true) }?.let(::File)
        val displayName =
            file?.name?.takeIf { it.isNotBlank() }
                ?: uri?.let { queryDisplayName(context, it) }
                ?: normalizedTitle(viewTitle)
        val mimeType =
            uri?.let { context.contentResolver.getType(it) }
                ?: file?.extension
                    ?.lowercase()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
        val totalBytes =
            file?.takeIf { it.isFile }?.length()?.takeIf { it > 0L }
                ?: uri?.let { querySize(context, it) }
        return ExternalDownloadOutput(uri, displayName, mimeType, totalBytes)
    }

    fun deleteCookies(context: Context, ownership: ExternalDownloadOwnership) {
        deleteCookies(
            context = context,
            callerRequestId = ownership.callerRequestId,
            taskCookiesPath = ownership.taskCookiesPath,
        )
    }

    fun deleteCookies(
        context: Context,
        callerRequestId: String?,
        taskCookiesPath: String?,
    ) {
        runCatching {
                callerRequestId?.takeIf { it.isNotBlank() }?.let {
                    ExternalCookieMaterializer.deleteTaskCookies(context, it)
                }
                taskCookiesPath?.takeIf { it.isNotBlank() }?.let { path ->
                    File(path).takeIf { it.exists() }?.delete()
                }
            }
            .onFailure { Log.w(TAG, "delete task cookies failed", it) }
    }

    private fun createContentUri(
        context: Context,
        path: String,
        callerPackage: String,
    ): Uri? {
        return runCatching {
                val uri =
                    if (path.startsWith("content:", ignoreCase = true)) {
                        Uri.parse(path)
                    } else {
                        val file = File(path)
                        if (!file.exists()) return null
                        FileProvider.getUriForFile(context, context.getFileProvider(), file)
                    }
                context.grantUriPermission(
                    callerPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                uri
            }
            .onFailure { Log.e(TAG, "createContentUri failed", it) }
            .getOrNull()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return queryLongOrString(context, uri, OpenableColumns.DISPLAY_NAME) as? String
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        val value = queryLongOrString(context, uri, OpenableColumns.SIZE)
        return (value as? Number)?.toLong()?.takeIf { it > 0L }
    }

    private fun queryLongOrString(context: Context, uri: Uri, column: String): Any? {
        return runCatching {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(column)
                    if (index < 0 || cursor.isNull(index)) return@use null
                    when (cursor.getType(index)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        else -> cursor.getString(index)
                    }
                }
            }
            .getOrNull()
    }

    private fun normalizedTitle(title: String?): String? {
        val value = title?.trim().orEmpty()
        return value.takeIf {
            it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://")
        }
    }
}
