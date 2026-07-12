package com.chloemlla.seal.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object PackageManagerCompat {
    fun getPackageInfo(context: Context, packageName: String = context.packageName): PackageInfo {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
    }

    fun getPackageArchiveInfo(context: Context, apkFile: File): PackageInfo? {
        val path = apkFile.absolutePath
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, 0)
        }
    }
}
