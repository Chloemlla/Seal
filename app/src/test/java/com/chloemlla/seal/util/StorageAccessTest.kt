package com.chloemlla.seal.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAccessTest {
    @Test
    fun resolveUsableDirectoryFallsBackWhenMissing() {
        val fallback =
            File(System.getProperty("java.io.tmpdir"), "seal-fallback-test").apply {
                deleteRecursively()
                mkdirs()
            }
        val resolved =
            StorageAccess.resolveUsableDirectory(
                preferred = "/this/path/should/not/exist/seal-audit",
                fallback = fallback,
            )
        assertEquals(fallback.absolutePath, resolved)
    }

    @Test
    fun isWritableDirectoryForTemp() {
        val dir = File(System.getProperty("java.io.tmpdir"), "seal-writable").apply { mkdirs() }
        assertTrue(StorageAccess.isWritableDirectory(dir.absolutePath))
    }

    @Test
    fun isAllowedAbsolutePathAcceptsAppAndPublicRoots() {
        val ok =
            StorageAccess.isAllowedAbsolutePath(
                path = "/data/user/0/com.chloemlla.seal/files/downloads/Seal",
                publicDownloadsRoot = "/storage/emulated/0/Download",
                publicDocumentsRoot = "/storage/emulated/0/Documents",
                appSpecificRoots = listOf("/data/user/0/com.chloemlla.seal/files/downloads"),
            )
        assertTrue(ok)
        val bad =
            StorageAccess.isAllowedAbsolutePath(
                path = "/storage/emulated/0/DCIM/Camera",
                publicDownloadsRoot = "/storage/emulated/0/Download",
                publicDocumentsRoot = "/storage/emulated/0/Documents",
                appSpecificRoots = listOf("/data/user/0/com.chloemlla.seal/files/downloads"),
            )
        assertFalse(bad)
    }
}
