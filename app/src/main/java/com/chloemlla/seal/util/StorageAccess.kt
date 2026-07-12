package com.chloemlla.seal.util

import android.net.Uri
import java.io.File

/**
 * Scoped-storage helpers. Prefer app-specific dirs; allow public Download/Documents trees only.
 * No MANAGE_EXTERNAL_STORAGE dependency.
 */
object StorageAccess {
    private val PUBLIC_TREE_PREFIXES =
        listOf(
            "primary:Download",
            "primary:Downloads",
            "primary:Documents",
            "primary:Download/",
            "primary:Documents/",
        )

    fun isWritableDirectory(path: String): Boolean {
        if (path.isBlank()) return false
        val dir = File(path)
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            dir.isDirectory && dir.canWrite()
        } catch (_: SecurityException) {
            false
        }
    }

    fun resolveUsableDirectory(preferred: String?, fallback: File): String {
        val candidate = preferred?.takeIf { it.isNotBlank() }
        if (candidate != null && isWritableDirectory(candidate)) return File(candidate).absolutePath
        fallback.mkdirs()
        return fallback.absolutePath
    }

    /** Tree URI path segment after /tree/ must target Download or Documents on primary. */
    fun isAllowedPublicTreeUri(uri: Uri): Boolean {
        val path = uri.path ?: return false
        // examples: /tree/primary:Download/Seal , /tree/primary:Documents/foo
        val marker = path.substringAfter("/tree/", missingDelimiterValue = "")
        if (marker.isEmpty()) return false
        val decoded = Uri.decode(marker)
        return PUBLIC_TREE_PREFIXES.any { prefix ->
            decoded.equals(prefix.removeSuffix("/"), ignoreCase = true) ||
                decoded.startsWith(prefix, ignoreCase = true)
        }
    }

    fun isAllowedAbsolutePath(path: String, publicDownloadsRoot: String, publicDocumentsRoot: String, appSpecificRoots: List<String>): Boolean {
        if (path.isBlank()) return false
        val normalized = path.replace('\', '/')
        val allowedRoots =
            (listOf(publicDownloadsRoot, publicDocumentsRoot) + appSpecificRoots)
                .filter { it.isNotBlank() }
                .map { it.replace('\', '/').trimEnd('/') }
        return allowedRoots.any { root ->
            normalized.equals(root, ignoreCase = true) ||
                normalized.startsWith("$root/", ignoreCase = true)
        }
    }

    fun treeUriToPrimaryAbsolutePath(uri: Uri, externalStorageRoot: String): String? {
        val raw = uri.path ?: return null
        if (!raw.contains("primary:")) return null
        if (!isAllowedPublicTreeUri(uri)) return null
        val relative = Uri.decode(raw.substringAfter("primary:"))
        return externalStorageRoot.trimEnd('/') + "/" + relative.trimStart('/')
    }
}
