package com.chloemlla.seal.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
            val streamUri = data ?: return@apply
            putExtra(Intent.EXTRA_STREAM, streamUri)
            val mimeType =
                context.contentResolver.getType(streamUri) ?: "media/*"
            // ACTION_SEND should carry the stream URI in EXTRA_STREAM, not as data.
            // Keeping data set can make some receivers treat it as ACTION_VIEW-style content
            // and miss temporary URI grants on newer Android releases.
            this.data = null
            type = mimeType
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(streamUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        if (!primaryOk) {
            Log.w(TAG, "Failed to delete media path: $path")
        }

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

        val normalizedPath = normalizeStoredPath(path)

        // 1) Absolute / local filesystem path
        runCatching {
            val file = File(normalizedPath)
            if (file.exists()) {
                if (file.isDirectory) {
                    if (file.deleteRecursively()) return true
                } else {
                    if (file.delete()) return true
                    val canonical = runCatching { file.canonicalFile }.getOrNull()
                    if (canonical != null && canonical != file && canonical.exists()) {
                        if (canonical.delete()) return true
                    }
                }
                // Public Downloads files can still be removed via MediaStore /
                // DocumentsContract even when direct File.delete() is denied.
                if (deleteViaMediaStore(file.absolutePath)) return true
                if (deleteViaPrimaryDocumentsProvider(file.absolutePath)) return true
                return !file.exists()
            } else if (
                !normalizedPath.startsWith("content:", ignoreCase = true) &&
                    !normalizedPath.startsWith("file:", ignoreCase = true)
            ) {
                // Path may still be indexed by MediaStore even if File.exists() is false.
                if (deleteViaMediaStore(normalizedPath)) return true
                if (deleteViaPrimaryDocumentsProvider(normalizedPath)) return true
                return true
            }
        }

        // 2) file:// URI
        runCatching {
            val uri = Uri.parse(normalizedPath)
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val filePath = uri.path ?: return@runCatching
                return deleteSinglePath(filePath)
            }
        }

        // 3) content:// document URI
        val contentResult =
            runCatching {
                val uri = Uri.parse(normalizedPath)
                if (!uri.scheme.equals("content", ignoreCase = true)) {
                    return@runCatching null
                }

                val deletedByContract =
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                        .getOrNull()
                if (deletedByContract == true) return@runCatching true

                // Tree child URIs sometimes need a document URI rebuild.
                if (deleteViaTreeDocumentUri(uri)) return@runCatching true

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

        // Final MediaStore attempt for opaque paths.
        if (deleteViaMediaStore(normalizedPath)) return true
        return false
    }

    private fun normalizeStoredPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("file:", ignoreCase = true)) {
            return Uri.parse(trimmed).path?.takeIf { it.isNotBlank() } ?: trimmed
        }
        return trimmed
    }

    private fun deleteViaMediaStore(absolutePath: String): Boolean {
        if (absolutePath.isBlank() || absolutePath.startsWith("content:", ignoreCase = true)) {
            return false
        }
        val file = File(absolutePath)
        val resolver = context.contentResolver
        val collections =
            buildList {
                add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                add(MediaStore.Files.getContentUri("external"))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
                }
            }

        // Legacy DATA column match (works on many OEM builds, including for app-owned media).
        for (collection in collections) {
            val deleted =
                runCatching {
                        resolver.delete(
                            collection,
                            MediaStore.MediaColumns.DATA + "=?",
                            arrayOf(file.absolutePath),
                        )
                    }
                    .getOrDefault(0)
            if (deleted > 0) return true
        }

        // Android 10+ preferred lookup by relative path + display name.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = relativePathForMediaStore(file) ?: return false
            val displayName = file.name
            if (displayName.isBlank()) return false
            val selection =
                MediaStore.MediaColumns.RELATIVE_PATH +
                    "=? AND " +
                    MediaStore.MediaColumns.DISPLAY_NAME +
                    "=?"
            for (collection in collections) {
                val deleted =
                    runCatching {
                            resolver.delete(collection, selection, arrayOf(relative, displayName))
                        }
                        .getOrDefault(0)
                if (deleted > 0) return true

                // Some providers keep a trailing-slash difference on RELATIVE_PATH.
                val altRelative =
                    if (relative.endsWith("/")) relative.dropLast(1) else "$relative/"
                val altDeleted =
                    runCatching {
                            resolver.delete(
                                collection,
                                selection,
                                arrayOf(altRelative, displayName),
                            )
                        }
                        .getOrDefault(0)
                if (altDeleted > 0) return true
            }
        }
        return false
    }

    private fun relativePathForMediaStore(file: File): String? {
        val absolute =
            runCatching { file.canonicalFile.absolutePath }.getOrDefault(file.absolutePath)
        val publicRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/', '\\')
        if (!absolute.startsWith(publicRoot)) return null
        val relativeFile = absolute.removePrefix(publicRoot).trimStart('/', '\\')
        val parent = relativeFile.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isBlank()) return ""
        return parent.replace('\\', '/') + "/"
    }

    private fun deleteViaPrimaryDocumentsProvider(absolutePath: String): Boolean {
        val relative = relativePathForMediaStore(File(absolutePath)) ?: return false
        val displayName = File(absolutePath).name
        if (displayName.isBlank()) return false
        val documentId =
            "primary:" +
                (relative.trimEnd('/') + "/" + displayName).trimStart('/').replace('\\', '/')
        val uri =
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                documentId,
            )
        val deleted =
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                .getOrNull()
        if (deleted == true) return true

        // Fallback: tree URI under primary Download/Documents.
        val treeRoots = listOf("primary:Download", "primary:Downloads", "primary:Documents")
        for (root in treeRoots) {
            if (!documentId.startsWith(root, ignoreCase = true)) continue
            val treeUri =
                DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    root,
                )
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            val ok =
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }
                    .getOrNull()
            if (ok == true) return true
        }
        return false
    }

    private fun deleteViaTreeDocumentUri(uri: Uri): Boolean {
        return runCatching {
                if (!DocumentsContract.isDocumentUri(context, uri)) return@runCatching false
                val docId = DocumentsContract.getDocumentId(uri)
                val authority = uri.authority ?: return@runCatching false
                if (uri.path?.contains("/tree/") == true) {
                    val treeId = DocumentsContract.getTreeDocumentId(uri)
                    val treeUri = DocumentsContract.buildTreeDocumentUri(authority, treeId)
                    val rebuilt = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    DocumentsContract.deleteDocument(context.contentResolver, rebuilt)
                } else {
                    false
                }
            }
            .getOrDefault(false)
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
        val normalizedPath = normalizeStoredPath(path)

        // Filesystem siblings with same basename.
        runCatching {
            val file =
                when {
                    normalizedPath.startsWith("file:", ignoreCase = true) ->
                        File(Uri.parse(normalizedPath).path ?: return@runCatching)
                    normalizedPath.startsWith("content:", ignoreCase = true) -> return@runCatching
                    else -> File(normalizedPath)
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
            val uri = Uri.parse(normalizedPath)
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

    /** Task-scoped Netscape cookie file for external delegate (protocol v2). */
    fun Context.getExternalTaskCookiesFile(taskId: String): File {
        val safe =
            taskId
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(120)
                .ifBlank { "unknown" }
        return File(File(cacheDir, "external_cookies"), "$safe.txt")
    }

    fun Context.deleteExternalTaskCookiesFile(taskId: String) {
        runCatching {
            val file = getExternalTaskCookiesFile(taskId)
            if (file.exists()) file.delete()
        }
    }

    fun Context.clearStaleExternalTaskCookies(maxAgeMs: Long = 24L * 60 * 60 * 1000) {
        runCatching {
            val dir = File(cacheDir, "external_cookies")
            if (!dir.isDirectory) return
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { f ->
                if (f.isFile && now - f.lastModified() > maxAgeMs) {
                    f.delete()
                }
            }
        }
    }

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
