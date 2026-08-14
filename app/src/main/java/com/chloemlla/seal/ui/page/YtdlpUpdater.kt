package com.chloemlla.seal.ui.page

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.Task.DownloadState.FetchingInfo
import com.chloemlla.seal.download.Task.DownloadState.ReadyWithInfo
import com.chloemlla.seal.download.Task.DownloadState.Running
import com.chloemlla.seal.download.YtDlpUpdateGate
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.PreferenceUtil.getBoolean
import com.chloemlla.seal.util.PreferenceUtil.getLong
import com.chloemlla.seal.util.PreferenceUtil.getString
import com.chloemlla.seal.util.UpdateUtil
import com.chloemlla.seal.util.YT_DLP_AUTO_UPDATE
import com.chloemlla.seal.util.YT_DLP_UPDATE_INTERVAL
import com.chloemlla.seal.util.YT_DLP_UPDATE_TIME
import com.chloemlla.seal.util.YT_DLP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val TAG = "YtdlpUpdater"

@Composable
fun YtdlpUpdater(downloader: DownloaderV2 = koinInject()) {
    LaunchedEffect(Unit) {
        if (YtDlpUpdateGate.isUpdating.value) return@LaunchedEffect

        if (!YT_DLP_AUTO_UPDATE.getBoolean() && YT_DLP_VERSION.getString().isNotEmpty())
            return@LaunchedEffect

        if (!PreferenceUtil.isNetworkAvailableForDownload()) {
            return@LaunchedEffect
        }

        val hasActiveWork =
            downloader.getTaskStateMap().values.any { state ->
                when (state.downloadState) {
                    is FetchingInfo,
                    ReadyWithInfo,
                    is Running -> true
                    else -> false
                }
            }
        if (hasActiveWork) return@LaunchedEffect

        val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
        val currentTime = System.currentTimeMillis()

        if (currentTime < lastUpdateTime + YT_DLP_UPDATE_INTERVAL.getLong()) {
            return@LaunchedEffect
        }

        if (!YtDlpUpdateGate.tryBegin()) return@LaunchedEffect
        try {
            runCatching { withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() } }
                .onFailure { Log.w(TAG, "updateYtDlp failed", it) }
        } finally {
            YtDlpUpdateGate.end()
        }
    }
}