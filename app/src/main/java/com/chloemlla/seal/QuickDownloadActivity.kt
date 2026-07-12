package com.chloemlla.seal

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.chloemlla.seal.integration.ExternalDownloadCoordinator
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
import com.chloemlla.seal.util.DownloadType
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

    /** UI binding for the latest external request (singleInstance may reuse process). */
    private val uiRequestState = mutableStateOf<UiRequest?>(null)

    data class UiRequest(
        val urls: List<String>,
        val extractAudio: Boolean?,
        val downloadSubtitle: Boolean?,
        val generation: Long,
    )

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!bindExternalIntent(intent)) {
            return
        }

        enableEdgeToEdge()
        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }

        val viewModel: DownloadDialogViewModel = getViewModel()

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    val request = uiRequestState.value
                    if (request == null) return@SealTheme

                    // Rebuild preferences / type when a new external request arrives.
                    var preferences by remember(request.generation) {
                        mutableStateOf(
                            DownloadUtil.DownloadPreferences.createFromPreferences().let { base ->
                                base.copy(
                                    extractAudio = request.extractAudio ?: base.extractAudio,
                                    downloadSubtitle =
                                        request.downloadSubtitle ?: base.downloadSubtitle,
                                )
                            }
                        )
                    }
                    val initialType =
                        when (request.extractAudio) {
                            true -> DownloadType.Audio
                            false -> DownloadType.Video
                            null ->
                                if (preferences.extractAudio) DownloadType.Audio
                                else DownloadType.Video
                        }

                    val sheetValue = viewModel.sheetValueFlow.collectAsStateWithLifecycle().value
                    val state = viewModel.sheetStateFlow.collectAsStateWithLifecycle().value
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val selectionState =
                        viewModel.selectionStateFlow.collectAsStateWithLifecycle().value
                    var showDialog by remember(request.generation) { mutableStateOf(false) }

                    LaunchedEffect(request.generation) {
                        viewModel.postAction(Action.ShowSheet(request.urls))
                    }

                    LaunchedEffect(sheetValue, selectionState, request.generation) {
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
                            config = Config(downloadType = initialType),
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
                                    this@QuickDownloadActivity.finish()
                                },
                            )
                        SelectionState.Idle -> {}
                        is SelectionState.PlaylistSelection -> {
                            PlaylistSelectionPage(
                                state = selectionState,
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this@QuickDownloadActivity.finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleInstance reuses this Activity; re-bind so extract_audio is reapplied.
        resultDelivered = false
        bindExternalIntent(intent)
    }

    /**
     * @return false when Activity already finished / should not continue setup.
     */
    private fun bindExternalIntent(intent: Intent?): Boolean {
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
            ExternalDownloadEntry.HandleResult.AutoStartedAndFinished -> return false
            ExternalDownloadEntry.HandleResult.NotExternal -> {
                finish()
                return false
            }
            is ExternalDownloadEntry.HandleResult.ShowUi -> {
                callerPackage = handleResult.accepted.callerPackage
                callerRequestId = handleResult.accepted.request.callerRequestId
                val req = handleResult.accepted.request
                ExternalDownloadCoordinator.beginExternalSession(
                    callerPackage = callerPackage,
                    callerRequestId = callerRequestId,
                    extractAudio = req.extractAudio,
                )
                val urls = req.urls
                if (urls.isEmpty()) {
                    ExternalDownloadCoordinator.endExternalSession()
                    finish()
                    return false
                }
                App.startService()
                deliverNeedsUiResult()
                uiRequestState.value =
                    UiRequest(
                        urls = urls,
                        extractAudio = req.extractAudio,
                        downloadSubtitle = req.downloadSubtitle,
                        generation = System.currentTimeMillis(),
                    )
                Log.i(
                    TAG,
                    "ShowUi extractAudio=${req.extractAudio} urls=${urls.size} reqId=$callerRequestId",
                )
                return true
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

    override fun onDestroy() {
        // Stops binding new UI enqueues; already-watched tasks keep terminal reporting.
        // notifyCanceledIfEmpty only fires when the user never confirmed a download.
        // After watchEnqueuedTasksIfExternal the session is cleared, so this will not
        // emit a late canceled that races accepted/completed.
        if (isFinishing) {
            ExternalDownloadCoordinator.endExternalSession(
                notifyCanceledIfEmpty = true,
                context = applicationContext,
            )
        }
        super.onDestroy()
    }
}