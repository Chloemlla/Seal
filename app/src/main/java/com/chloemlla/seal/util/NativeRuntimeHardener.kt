package com.chloemlla.seal.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Hardens youtubedl-android extracted package trees for Android 17 (API 37)
 * dynamic-native-load guidance: prefer read-only payloads before runtime use.
 *
 * The library unzips python/ffmpeg/aria2c into noBackupFilesDir and points
 * LD_LIBRARY_PATH at those trees. We cannot change upstream unzip, but we can
 * strip write bits immediately after init.
 */
object NativeRuntimeHardener {
    private const val TAG = "NativeRuntimeHardener"
    private const val YOUTUBEDL_BASE_DIR = "youtubedl-android"
    private const val PACKAGES_DIR = "packages"

    fun hardenYoutubeDlPackages(context: Context) {
        val packagesRoot =
            File(context.noBackupFilesDir, YOUTUBEDL_BASE_DIR).resolve(PACKAGES_DIR)
        val count = makeTreeReadOnly(packagesRoot)
        if (count > 0) {
            Log.i(TAG, "Hardened $count extracted native package file(s) under ${packagesRoot.path}")
        }
    }

    /**
     * Best-effort: clear write permission for every file under [root].
     * Returns number of files successfully visited (including already-readonly).
     */
    fun makeTreeReadOnly(root: File): Int {
        if (!root.exists()) return 0
        var count = 0
        root.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            runCatching {
                // Owner/world write off; keep world-readable so dynamic linker can map libs.
                file.setReadable(true, /* ownerOnly = */ false)
                file.setWritable(false, /* ownerOnly = */ false)
                val name = file.name
                val looksExecutable =
                    name.endsWith(".so") ||
                        name.endsWith(".bin") ||
                        !name.contains('.')
                if (looksExecutable) {
                    file.setExecutable(true, /* ownerOnly = */ false)
                }
                count++
            }
                .onFailure { Log.w(TAG, "Failed to harden ${file.absolutePath}", it) }
        }
        return count
    }
}
