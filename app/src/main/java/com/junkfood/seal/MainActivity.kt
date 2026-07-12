package com.junkfood.seal

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.integration.ExternalDownloadEntry
import com.junkfood.seal.ui.common.LocalDarkTheme
import com.junkfood.seal.ui.common.SettingsProvider
import com.junkfood.seal.ui.page.AppEntry
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : AppCompatActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()
    private val downloader: DownloaderV2 by inject()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        context = this.baseContext
        setContent {
            KoinContext {
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
        }

        handleExternalIntent(intent, isColdStart = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
}
