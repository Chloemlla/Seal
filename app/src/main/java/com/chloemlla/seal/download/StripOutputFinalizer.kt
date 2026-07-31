package com.chloemlla.seal.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

/** Transactional single-file SAF export for a stripped task output. */
internal object StripOutputFinalizer {
    data class SdcardOutput(val uri: String, private val documentUri: Uri) {
        fun rollback(context: Context) {
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, documentUri)
            }
        }
    }

    fun moveToSdcard(
        context: Context,
        source: File,
        treeUri: String,
        throwIfCanceled: () -> Unit,
    ): Result<SdcardOutput> =
        runCatching {
            require(source.isFile && source.length() > 0L) {
                "Missing stripped SD-card staging file"
            }
            throwIfCanceled()
            val tree = Uri.parse(treeUri)
            val destinationDirectory =
                DocumentsContract.buildDocumentUriUsingTree(
                    tree,
                    DocumentsContract.getTreeDocumentId(tree),
                )
            val mimeType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension) ?: "*/*"
            val destination =
                requireNotNull(
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        destinationDirectory,
                        mimeType,
                        source.name,
                    )
                ) { "Unable to create stripped output in the selected SD-card directory" }

            try {
                source.inputStream().use { input ->
                    val outputStream =
                        requireNotNull(
                            context.contentResolver.openOutputStream(destination, "w")
                        ) { "Unable to open stripped SD-card output" }
                    outputStream.use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (true) {
                            throwIfCanceled()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
                throwIfCanceled()
                check(source.delete()) { "Unable to remove stripped SD-card staging file" }
                SdcardOutput(destination.toString(), destination)
            } catch (error: Throwable) {
                runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, destination)
                }
                throw error
            }
        }

    private const val COPY_BUFFER_SIZE = 64 * 1024
}
