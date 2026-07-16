package com.chloemlla.seal.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeHardenerTest {
    @Test
    fun makeTreeReadOnlyClearsWriteBit() {
        val root =
            File(System.getProperty("java.io.tmpdir"), "seal-native-harden-test").apply {
                deleteRecursively()
                mkdirs()
            }
        val nestedDir = File(root, "usr/lib").apply { mkdirs() }
        val lib =
            File(nestedDir, "libfoo.so").apply {
                writeText("native")
                setWritable(true, false)
            }
        val data =
            File(nestedDir, "cert.pem").apply {
                writeText("pem")
                setWritable(true, false)
            }
        val count = NativeRuntimeHardener.makeTreeReadOnly(root)
        assertEquals(2, count)
        assertTrue(lib.exists())
        assertTrue(data.exists())
        assertTrue(lib.canRead())
        assertTrue(data.canRead())
        root.deleteRecursively()
    }
}
