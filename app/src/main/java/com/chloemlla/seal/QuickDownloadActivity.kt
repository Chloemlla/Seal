package com.chloemlla.seal

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.integration.ExternalDownloadEntry
import com.chloemlla.seal.integration.ExternalDownloadProtocol
import com.chloemlla.seal.integration.ExternalDownloadStatusReporter
import com.chloemlla.seal.ui.common.LocalDarkTheme
import com.chloemlla.seal.ui.common.SettingsProvider
import com.chloemlla.seal.ui.page.downloadv2.configure.Config
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialog
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.SelectionState
import com.chloemlla.seal.ui.page.downloadv2.configure.FormatPage
import com.chloemlla.seal.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.chloemlla.seal.ui.theme.SealTheme
import com.chloemlla.seal.util.DownloadUtil
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.setLanguage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.getViewModel

private const val TAG = "QuickDownloadActivity"

class QuickDownloadActivity : ComponentActivity() {
    private val downloader: DownloaderV2 by inject()
    private var callerPackage: String? = null
    private var callerRequestId: String? = null
    private var resultDelivered: Boolean = false

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (
            val handleResult =
                ExternalDownloadEntry.handle(
                    activity = this,
                    intent = intent,
                    downloader = downloader,
                    finishOnReject = true,
                    finishOnAutoStart = true,
                )
        ) {
            ExternalDownloadEntry.HandleResult.RejectedAndFinished,
            ExternalDownloadEntry.HandleResult.AutoStartedAndFinished -> return
            ExternalDownloadEntry.HandleResult.NotExternal -> {
                finish()
                return
            }
            is ExternalDownloadEntry.HandleResult.ShowUi -> {
                callerPackage = handleResult.accepted.callerPackage
                callerRequestId = handleResult.accepted.request.callerRequestId
                val urls = handleResult.accepted.request.urls
                if (urls.isEmpty()) {
                    finish()
                    return
                }

                App.startService()

                enableEdgeToEdge()

                window.run {
                    setBackgroundDrawable(ColorDrawable(0))
                    setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    } else {
                        setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                    }
                }

                if (Build.VERSION.SDK_INT < 33) {
                    runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
                }

                val viewModel: DownloadDialogViewModel = getViewModel()
                val preferencesOverride =
                    DownloadUtil.DownloadPreferences.createFromPreferences().let { base ->
                        base.copy(
                            extractAudio =
                                handleResult.accepted.request.extractAudio ?: base.extractAudio,
                            downloadSubtitle =
                                handleResult.accepted.request.downloadSubtitle
                                    ?: base.downloadSubtitle,
                        )
                    }

                deliverNeedsUiResult()
                viewModel.postAction(Action.ShowSheet(urls))

                setContent {
                    SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                        SealTheme(
                            darkTheme = LocalDarkTheme.current.isDarkTheme(),
                            isHighContrastModeEnabled =
                                LocalDarkTheme.current.isHighContrastModeEnabled,
                        ) {
                            var preferences by remember { mutableStateOf(preferencesOverride) }

                            val sheetValue =
                                viewModel.sheetValueFlow.collectAsStateWithLifecycle().value

                            val state = viewModel.sheetStateFlow.collectAsStateWithLifecycle().value

                            val sheetState =
                                rememberModalBottomSheetState(skipPartiallyExpanded = true)

                            val selectionState =
                                viewModel.selectionStateFlow.collectAsStateWithLifecycle().value

                            var showDialog by remember { mutableStateOf(false) }

                            LaunchedEffect(sheetValue, selectionState) {
                                if (sheetValue == DownloadDialogViewModel.SheetValue.Expanded) {
                                    showDialog = true
                                } else if (sheetValue == DownloadDialogViewModel.SheetValue.Hidden) {
                                    launch { sheetState.hide() }
                                        .invokeOnCompletion {
                                            showDialog = false
                                            if (selectionState == SelectionState.Idle) {
                                                this@QuickDownloadActivity.finish()
                                            }
                                        }
                                }
                            }

                            if (showDialog) {
                                DownloadDialog(
                                    state = state,
                                    sheetState = sheetState,
                                    config = Config(),
                                    preferences = preferences,
                                    onPreferencesUpdate = { preferences = it },
                                    onActionPost = { viewModel.postAction(it) },
                                )
                            }

                            when (selectionState) {
                                is SelectionState.FormatSelection ->
                                    FormatPage(
                                        state = selectionState,
                                        onDismissRequest = {
                                            viewModel.postAction(Action.Reset)
                                            this.finish()
                                        },
                                    )

                                SelectionState.Idle -> {}
                                is SelectionState.PlaylistSelection -> {
                                    PlaylistSelectionPage(
                                        state = selectionState,
                                        onDismissRequest = {
                                            viewModel.postAction(Action.Reset)
                                            this.finish()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun deliverNeedsUiResult() {
        if (resultDelivered) return
        resultDelivered = true
        ExternalDownloadStatusReporter.finishWithResult(
            activity = this,
            resultCode = Activity.RESULT_OK,
            status = ExternalDownloadProtocol.STATUS_NEEDS_UI,
            errorCode = ExternalDownloadProtocol.ERROR_OK,
            callerRequestId = callerRequestId,
            callerPackage = callerPackage,
            alsoBroadcast = true,
        )
    }
}
