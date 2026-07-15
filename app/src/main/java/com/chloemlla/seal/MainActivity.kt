package com.chloemlla.seal

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.ui.LumenCrashReportScreen
import com.chloemlla.seal.App.Companion.context
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.integration.ExternalDownloadCoordinator
import com.chloemlla.seal.integration.ExternalDownloadEntry
import com.chloemlla.seal.ui.common.LocalDarkTheme
import com.chloemlla.seal.ui.common.SettingsProvider
import com.chloemlla.seal.ui.page.AppEntry
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.chloemlla.seal.ui.theme.SealTheme
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()
    private val downloader: DownloaderV2 by inject()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LumenCrash.recordBreadcrumb("MainActivity.onCreate")

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        context = this.baseContext
        val hasPendingCrashReport = LumenCrash.loadPendingReport() != null
        setContent {
            var pendingReport by remember { mutableStateOf(LumenCrash.loadPendingReport()) }
            if (pendingReport != null) {
                SealTheme(darkTheme = true, isHighContrastModeEnabled = false) {
                    LumenCrashReportScreen(
                        report = pendingReport!!,
                        onContinue = {
                            LumenCrash.clearPendingReport()
                            pendingReport = null
                            recreate()
                        },
                    )
                }
                return@setContent
            }

            val windowSizeClass = calculateWindowSizeClass(this)
            SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    AppEntry(dialogViewModel = dialogViewModel)
                }
            }
        }

        // Avoid auto-starting external downloads while a crash report blocks normal UI.
        if (!hasPendingCrashReport) {
            handleExternalIntent(intent, isColdStart = true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LumenCrash.recordBreadcrumb("MainActivity.onNewIntent")
        setIntent(intent)
        handleExternalIntent(intent, isColdStart = false)
    }

    private fun handleExternalIntent(intent: Intent?, isColdStart: Boolean) {
        when (
            val result =
                ExternalDownloadEntry.handle(
                    activity = this,
                    intent = intent,
                    downloader = downloader,
                    // Main stays open for UI; only auto-start may finish if launched for result only.
                    finishOnReject = false,
                    finishOnAutoStart = false,
                )
        ) {
            is ExternalDownloadEntry.HandleResult.ShowUi -> {
                ExternalDownloadCoordinator.beginExternalSession(
                    callerPackage = result.accepted.callerPackage,
                    callerRequestId = result.accepted.request.callerRequestId,
                    extractAudio = result.accepted.request.extractAudio,
                )
                dialogViewModel.postAction(
                    DownloadDialogViewModel.Action.ShowSheet(result.accepted.request.urls)
                )
            }
            ExternalDownloadEntry.HandleResult.AutoStartedAndFinished -> {
                // Task accepted into queue; keep main UI for user visibility.
            }
            ExternalDownloadEntry.HandleResult.RejectedAndFinished -> {
                // Rejection result already delivered; keep app usable on cold start launcher path.
                if (!isColdStart) {
                    // no-op
                }
            }
            ExternalDownloadEntry.HandleResult.NotExternal -> Unit
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            ExternalDownloadCoordinator.endExternalSession(
                notifyCanceledIfEmpty = true,
                context = applicationContext,
            )
        }
        super.onDestroy()
    }
}
