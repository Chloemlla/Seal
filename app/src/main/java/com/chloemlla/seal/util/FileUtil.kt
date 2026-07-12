package com.chloemlla.seal.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.chloemlla.seal.App.Companion.context
import com.chloemlla.seal.R
import java.io.File

const val AUDIO_REGEX = "(mp3|aac|opus|m4a)$"
const val THUMBNAIL_REGEX = "\\.(jpg|png)$"
const val SUBTITLE_REGEX = "\\.(lrc|vtt|srt|ass|json3|srv.|ttml)$"
private const val PRIVATE_DIRECTORY_SUFFIX = ".Seal"

object FileUtil {
    fun openFileFromResult(downloadResult: Result<List<String>>) {
        val filePaths = downloadResult.getOrNull()
        if (filePaths.isNullOrEmpty()) return
        openFile(filePaths.first()) {
            ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
        }
    }

    inline fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) =
        path
            .runCatching {
                createIntentForOpeningFile(this)?.run { context.startActivity(this) }
                    ?: throw Exception()
            }
            .onFailure { onFailureCallback(it) }

    private fun createIntentForFile(path: String?): Intent? {
        if (path == null) return null

        val uri =
            path
                .runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(path)).run {
                        if (this?.exists() == true) {
                            this.uri
                        } else if (File(this@runCatching).exists()) {
                            FileProvider.getUriForFile(
                                context,
                                context.getFileProvider(),
                                File(this@runCatching),
                            )
                        } else null
                    }
                }
                .getOrNull() ?: return null

        return Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            data = uri
        }
    }

    fun createIntentForOpeningFile(path: String?): Intent? =
        createIntentForFile(path)?.let {
            it.apply {
                action = (Intent.ACTION_VIEW)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    fun createIntentForSharingFile(path: String?): Intent? =
        createIntentForFile(path)?.apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, data)
            val mimeType = data?.let { context.contentResolver.getType(it) } ?: "media/*"
            setDataAndType(this.data, mimeType)
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(data))
        }

    fun Context.getFileProvider() = "$packageName.provider"

    fun String.getFileSize(): Long =
        this.run {
            val length = File(this).length()
            if (length == 0L) DocumentFile.fromSingleUri(context, Uri.parse(this))?.length() ?: 0L
            else length
        }

    fun String.getFileName(): String =
        this.run {
            File(this).nameWithoutExtension.ifEmpty {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.name ?: "video"
            }
        }

    data class MediaDeleteResult(
        val path: String,
        val primaryDeletedOrMissing: Boolean,
        val deletedPaths: List<String> = emptyList(),
        val failedPaths: List<String> = emptyList(),
    ) {
        val isFullySuccessful: Boolean
            get() = primaryDeletedOrMissing && failedPaths.isEmpty()
    }

    /**
     * Delete a downloaded media path and common same-basename sidecars.
     *
     * Supports absolute filesystem paths and content/document URIs.
     * Missing files are treated as success (already gone).
     */
    fun deleteFile(path: String, deleteRelated: Boolean = true): MediaDeleteResult {
        if (path.isBlank()) {
            return MediaDeleteResult(path = path, primaryDeletedOrMissing = true)
        }

        val deleted = linkedSetOf<String>()
        val failed = linkedSetOf<String>()

        fun record(target: String, ok: Boolean) {
            if (ok) deleted += target else failed += target
        }

        val primaryOk = deleteSinglePath(path)
        record(path, primaryOk)

        if (deleteRelated) {
            for (related in findRelatedMediaPaths(path)) {
                if (related == path) continue
                record(related, deleteSinglePath(related))
            }
        }

        val scanTargets =
            (deleted + failed)
                .filter { !it.startsWith("content:", ignoreCase = true) }
                .distinct()
        if (scanTargets.isNotEmpty()) {
            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    scanTargets.toTypedArray(),
                    null,
                    null,
                )
            }
        }

        return MediaDeleteResult(
            path = path,
            primaryDeletedOrMissing = primaryOk,
            deletedPaths = deleted.toList(),
            failedPaths = failed.toList(),
        )
    }

    private fun deleteSinglePath(path: String): Boolean {
        if (path.isBlank()) return true

        // 1) Absolute / local filesystem path
        runCatching {
            val file = File(path)
            if (file.exists()) {
                if (file.isDirectory) {
                    return file.deleteRecursively()
                }
                if (file.delete()) return true
                val canonical = runCatching { file.canonicalFile }.getOrNull()
                if (canonical != null && canonical != file && canonical.exists()) {
                    if (canonical.delete()) return true
                }
                return false
            } else if (
                !path.startsWith("content:", ignoreCase = true) &&
                    !path.startsWith("file:", ignoreCase = true)
            ) {
                // Non-URI path that does not exist: already gone.
                return true
            }
        }

        // 2) file:// URI
        runCatching {
            val uri = Uri.parse(path)
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val filePath = uri.path ?: return@runCatching
                val file = File(filePath)
                if (!file.exists()) return true
                return if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }

        // 3) content:// document URI
        val contentResult =
            runCatching {
                val uri = Uri.parse(path)
                if (!uri.scheme.equals("content", ignoreCase = true)) {
                    return@runCatching null
                }

                val deletedByContract =
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                        .getOrNull()
                if (deletedByContract == true) return@runCatching true

                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc == null) {
                    return@runCatching !documentUriExists(uri)
                }
                if (!doc.exists()) return@runCatching true
                if (doc.delete()) return@runCatching true

                val rows =
                    runCatching { context.contentResolver.delete(uri, null, null) }.getOrNull()
                if (rows != null && rows > 0) return@runCatching true
                return@runCatching !doc.exists()
            }
                .getOrNull()
        if (contentResult != null) return contentResult

        return false
    }

    private fun documentUriExists(uri: Uri): Boolean =
        runCatching {
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        null,
                        null,
                        null,
                    )
                    ?.use { it.moveToFirst() } ?: false
            }
            .getOrDefault(false)

    private fun isCompanionFileName(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.endsWith(".info.json") || lower.endsWith(".description")) return true
        val ext = lower.substringAfterLast('.', missingDelimiterValue = "")
        return ext in
            setOf(
                "jpg",
                "jpeg",
                "png",
                "webp",
                "lrc",
                "vtt",
                "srt",
                "ass",
                "ssa",
                "json3",
                "ttml",
                "json",
                "description",
                "nfo",
                "txt",
            ) || ext.startsWith("srv")
    }

    private fun findRelatedMediaPaths(path: String): List<String> {
        val related = mutableListOf<String>()

        // Filesystem siblings with same basename.
        runCatching {
            val file =
                when {
                    path.startsWith("file:", ignoreCase = true) ->
                        File(Uri.parse(path).path ?: return@runCatching)
                    path.startsWith("content:", ignoreCase = true) -> return@runCatching
                    else -> File(path)
                }
            val parent = file.parentFile ?: return@runCatching
            if (!parent.isDirectory) return@runCatching
            val base = file.nameWithoutExtension
            if (base.isBlank()) return@runCatching
            parent.listFiles()?.forEach { candidate ->
                if (!candidate.isFile) return@forEach
                if (candidate.absolutePath == file.absolutePath) return@forEach
                val name = candidate.name
                if (name == base || name.startsWith("$base.") || name.startsWith("${base}_")) {
                    if (isCompanionFileName(name)) related += candidate.absolutePath
                }
            }
        }

        // content URI siblings under the same parent document (when available).
        runCatching {
            val uri = Uri.parse(path)
            if (!uri.scheme.equals("content", ignoreCase = true)) return@runCatching
            val doc = DocumentFile.fromSingleUri(context, uri) ?: return@runCatching
            val name = doc.name ?: return@runCatching
            val base = name.substringBeforeLast('.', missingDelimiterValue = name)
            if (base.isBlank()) return@runCatching
            val parent = doc.parentFile ?: return@runCatching
            parent.listFiles().forEach { child ->
                if (!child.isFile) return@forEach
                val childName = child.name ?: return@forEach
                if (child.uri == doc.uri) return@forEach
                if (
                    childName == base ||
                        childName.startsWith("$base.") ||
                        childName.startsWith("${base}_")
                ) {
                    if (isCompanionFileName(childName)) related += child.uri.toString()
                }
            }
        }

        return related.distinct()
    }

    @CheckResult
    fun scanFileToMediaLibraryPostDownload(title: String, downloadDir: String): List<String> =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile && it.absolutePath.contains(title) }
            .map { it.absolutePath }
            .toMutableList()
            .apply {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
                removeAll {
                    it.contains(Regex(THUMBNAIL_REGEX)) || it.contains(Regex(SUBTITLE_REGEX))
                }
            }

    fun scanDownloadDirectoryToMediaLibrary(downloadDir: String) =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .run {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
            }

    @CheckResult
    fun moveFilesToSdcard(tempPath: File, sdcardUri: String): Result<List<String>> {
        val uriList = mutableListOf<String>()
        val destDir =
            Uri.parse(sdcardUri).run {
                DocumentsContract.buildDocumentUriUsingTree(
                    this,
                    DocumentsContract.getTreeDocumentId(this),
                )
            }
        val res =
            tempPath.runCatching {
                walkTopDown().forEach {
                    if (it.isDirectory) return@forEach
                    val mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

                    val destUri =
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            destDir,
                            mimeType,
                            it.name,
                        ) ?: return@forEach

                    val inputStream = it.inputStream()
                    val outputStream =
                        context.contentResolver.openOutputStream(destUri) ?: return@forEach
                    inputStream.use { input ->
                        outputStream.use { output -> input.copyTo(output) }
                    }
                    uriList.add(destUri.toString())
                }
                uriList
            }
        tempPath.deleteRecursively()
        return res
    }

    fun clearTempFiles(downloadDir: File): Int {
        var count = 0
        downloadDir.walkTopDown().forEach {
            if (it.isFile && !it.isHidden) {
                if (it.delete()) count++
            }
        }
        return count
    }

    fun Context.getConfigDirectory(): File = cacheDir

    fun Context.getConfigFile(suffix: String = "") = File(getConfigDirectory(), "config$suffix.txt")

    fun Context.getCookiesFile() = File(getConfigDirectory(), "cookies.txt")

    fun getExternalTempDir() =
        run {
            val base =
                if (StorageAccess.isWritableDirectory(getExternalDownloadDirectory().absolutePath)) {
                    getExternalDownloadDirectory()
                } else {
                    context.getAppSpecificDownloadDirectory()
                }
            File(base, "tmp").apply {
                mkdirs()
                createEmptyFile(".nomedia")
            }
        }

    fun Context.getSdcardTempDir(child: String?): File =
        getExternalTempDir().run { child?.let { resolve(it) } ?: this }

    fun Context.getArchiveFile(): File = filesDir.createEmptyFile("archive.txt").getOrThrow()

    fun Context.getInternalTempDir() = File(filesDir, "tmp")

    /**
     * Best-effort public Downloads/Seal directory. May be unwritable without legacy storage access.
     */
    internal fun getExternalDownloadDirectory() =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seal")
            .also {
                runCatching { it.mkdirs() }
            }

    /** Legacy public hidden folder; prefer [Context.getAppSpecificPrivateDownloadDirectory]. */
    internal fun getExternalPrivateDownloadDirectory() =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PRIVATE_DIRECTORY_SUFFIX,
        )

    /** Always-writable app-specific download root (scoped storage safe). */
    fun Context.getAppSpecificDownloadDirectory(): File =
        (getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir.resolve("downloads"))
            .also { it.mkdirs() }

    fun Context.getAppSpecificPrivateDownloadDirectory(): File =
        getAppSpecificDownloadDirectory()
            .resolve("private")
            .also {
                it.mkdirs()
                runCatching { File(it, ".nomedia").createNewFile() }
            }

    fun Context.getAppSpecificAudioDownloadDirectory(): File =
        getAppSpecificDownloadDirectory().resolve("Audio").also { it.mkdirs() }

    /**
     * Prefer public Downloads/Seal when writable; otherwise app-specific external files.
     */
    fun Context.getPreferredDownloadDirectory(): File {
        val publicDir = getExternalDownloadDirectory()
        return if (StorageAccess.isWritableDirectory(publicDir.absolutePath)) publicDir
        else getAppSpecificDownloadDirectory()
    }

    fun Context.getPreferredPrivateDownloadDirectory(): File {
        val publicPrivate = getExternalPrivateDownloadDirectory()
        return if (StorageAccess.isWritableDirectory(publicPrivate.absolutePath)) {
            publicPrivate.also { runCatching { File(it, ".nomedia").createNewFile() } }
        } else {
            getAppSpecificPrivateDownloadDirectory()
        }
    }

    fun File.createEmptyFile(fileName: String): Result<File> =
        this.runCatching {
                mkdirs()
                resolve(fileName).apply { this@apply.createNewFile() }
            }
            .onFailure { it.printStackTrace() }

    fun writeContentToFile(content: String, file: File): File = file.apply { writeText(content) }

    fun getRealPath(treeUri: Uri): String {
        Log.d(TAG, treeUri.toString())
        val absolute =
            StorageAccess.treeUriToPrimaryAbsolutePath(
                treeUri,
                Environment.getExternalStorageDirectory().absolutePath,
            )
        if (absolute == null) {
            ToastUtil.makeToast(context.getString(R.string.permission_issue_desc))
            return context.getPreferredDownloadDirectory().absolutePath
        }
        return absolute
    }

    private const val TAG = "FileUtil"
}
