package com.chloemlla.seal.download

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Serializes yt-dlp binary auto-updates so they do not overlap with each other.
 * Callers should also skip when [DownloaderV2] has active work.
 */
object YtDlpUpdateGate {
    private val updating = AtomicBoolean(false)
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    fun tryBegin(): Boolean {
        if (!updating.compareAndSet(false, true)) return false
        _isUpdating.value = true
        return true
    }

    fun end() {
        updating.set(false)
        _isUpdating.value = false
    }
}