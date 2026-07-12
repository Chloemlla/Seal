package com.chloemlla.seal.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStorageKeysTest {
    @Test
    fun runtimeKeysIsolatedFromSettings() {
        assertTrue(PreferenceStorageKeys.isRuntimeKey(TASK_LIST))
        assertTrue(PreferenceStorageKeys.isRuntimeKey(SAVED_LINKS))
        assertTrue(PreferenceStorageKeys.isRuntimeKey(YT_DLP_VERSION))
        assertFalse(PreferenceStorageKeys.isRuntimeKey(EXTRACT_AUDIO))
        assertFalse(PreferenceStorageKeys.isRuntimeKey(EXTERNAL_DELEGATE_ENABLED))
        assertFalse(PreferenceStorageKeys.isRuntimeKey(DARK_THEME_VALUE))
    }

    @Test
    fun downloadPreferenceKeysInvalidateSnapshot() {
        assertTrue(PreferenceStorageKeys.isDownloadPreferenceKey(EXTRACT_AUDIO))
        assertTrue(PreferenceStorageKeys.isDownloadPreferenceKey(VIDEO_QUALITY))
        assertTrue(PreferenceStorageKeys.isDownloadPreferenceKey(PROXY_URL))
        assertFalse(PreferenceStorageKeys.isDownloadPreferenceKey(TASK_LIST))
        assertFalse(PreferenceStorageKeys.isDownloadPreferenceKey(DARK_THEME_VALUE))
        assertFalse(PreferenceStorageKeys.isDownloadPreferenceKey(WELCOME_DIALOG))
    }

    @Test
    fun mmapIdsAreStable() {
        assertTrue(PreferenceStorageKeys.PREFS_MMAP_ID == "seal_prefs")
        assertTrue(PreferenceStorageKeys.RUNTIME_MMAP_ID == "seal_runtime")
    }

    @Test
    fun downloadPreferenceKeysCoverHotConfigureFields() {
        // Keys commonly flipped in configure dialog must invalidate snapshot.
        assertTrue(PreferenceStorageKeys.isDownloadPreferenceKey(CUSTOM_COMMAND))
        assertTrue(PreferenceStorageKeys.isDownloadPreferenceKey(FORMAT_SELECTION))
        assertTrue(PreferenceStorageKeys.isDownloadPreferenceKey(COOKIES))
        assertTrue(PreferenceStorageKeys.isDownloadPreferenceKey(ARIA2C))
        assertFalse(PreferenceStorageKeys.isDownloadPreferenceKey(EXTERNAL_DELEGATE_ENABLED))
    }
}
