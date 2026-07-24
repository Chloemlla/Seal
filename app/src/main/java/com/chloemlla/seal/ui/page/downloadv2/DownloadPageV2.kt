package com.chloemlla.seal.ui.page.downloadv2

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.chloemlla.seal.R
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.Task
import com.chloemlla.seal.download.Task.DownloadState.Canceled
import com.chloemlla.seal.download.Task.DownloadState.Completed
import com.chloemlla.seal.download.Task.DownloadState.Error
import com.chloemlla.seal.download.Task.DownloadState.FetchingInfo
import com.chloemlla.seal.download.Task.DownloadState.Idle
import com.chloemlla.seal.download.Task.DownloadState.ReadyWithInfo
import com.chloemlla.seal.download.Task.DownloadState.Running
import com.chloemlla.seal.ui.common.HapticFeedback.slightHapticFeedback
import com.chloemlla.seal.ui.common.LocalDarkTheme
import com.chloemlla.seal.ui.common.LocalFixedColorRoles
import com.chloemlla.seal.ui.common.LocalWindowWidthState
import com.chloemlla.seal.ui.component.glassBackground
import com.chloemlla.seal.ui.component.SealModalBottomSheet
import com.chloemlla.seal.ui.component.SelectionGroupDefaults
import com.chloemlla.seal.ui.component.SelectionGroupItem
import com.chloemlla.seal.ui.component.SelectionGroupRow
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.chloemlla.seal.integration.ExternalDownloadCoordinator
import com.chloemlla.seal.ui.page.downloadv2.configure.Config
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialog
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.chloemlla.seal.ui.page.downloadv2.configure.FormatPage
import com.chloemlla.seal.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.chloemlla.seal.ui.page.downloadv2.configure.PreferencesMock
import com.chloemlla.seal.ui.page.download.NotificationPermissionDialog
import com.chloemlla.seal.ui.svg.DynamicColorImageVectors
import com.chloemlla.seal.ui.svg.drawablevectors.download
import com.chloemlla.seal.ui.theme.SealTheme
import com.chloemlla.seal.util.DownloadUtil
import com.chloemlla.seal.util.DownloadType
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.NOTIFICATION
import com.chloemlla.seal.util.FileUtil
import com.chloemlla.seal.util.getErrorReport
import com.chloemlla.seal.util.makeToast
import com.chloemlla.seal.util.NotificationUtil
import com.chloemlla.seal.util.PreferenceUtil.getBoolean
import com.chloemlla.seal.util.PreferenceUtil.updateBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.chloemlla.seal.util.copyToClipboard
import com.chloemlla.seal.util.readClipboardText

private const val TAG = "DownloadPageV2"

enum class Filter {
    All,
    Downloading,
    Canceled,
    Finished;

    @Composable
    @ReadOnlyComposable
    fun label(): String =
        when (this) {
            All -> stringResource(R.string.all)
            Downloading -> stringResource(R.string.status_downloading)
            Canceled -> stringResource(R.string.status_canceled)
            Finished -> stringResource(R.string.status_completed)
        }

    fun predict(entry: Pair<Task, Task.State>): Boolean {
        if (this == All) return true
        val state = entry.second.downloadState
        return when (this) {
            Downloading -> {
                when (state) {
                    is FetchingInfo,
                    Idle,
                    ReadyWithInfo,
                    is Running -> true
                    else -> false
                }
            }
            Canceled -> {
                state is Error || state is Task.DownloadState.Canceled
            }
            Finished -> {
                state is Completed
            }
        }
    }
}

sealed interface UiAction {
    data class OpenFile(val filePath: String?) : UiAction

    data class ShareFile(val filePath: String?) : UiAction

    data class OpenThumbnailURL(val url: String) : UiAction

    data object CopyVideoURL : UiAction

    data class OpenVideoURL(val url: String) : UiAction

    data object Cancel : UiAction

    data object Delete : UiAction

    data object Resume : UiAction

    data class CopyErrorReport(val throwable: Throwable) : UiAction
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DownloadPageV2(
    modifier: Modifier = Modifier,
    onMenuOpen: (() -> Unit) = {},
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2 = koinInject(),
) {
    val view = LocalView.current
    val context = LocalContext.current
    val shareTitleText = stringResource(R.string.share)
    val scope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current

    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var openSheetAfterPermissionPrompt by rememberSaveable { mutableStateOf(false) }
    var hasPromptedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermission =
        if (Build.VERSION.SDK_INT >= 33) {
            rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    fun openDownloadSheet() {
        dialogViewModel.postAction(Action.ShowSheet())
    }

    fun onDownloadClicked() {
        view.slightHapticFeedback()
        val notificationsPreferred = NOTIFICATION.getBoolean()
        val permissionDenied =
            Build.VERSION.SDK_INT >= 33 &&
                notificationPermission?.status is PermissionStatus.Denied &&
                !NotificationUtil.areNotificationsEnabled()
        if (
            notificationsPreferred &&
                permissionDenied &&
                !hasPromptedNotificationPermission
        ) {
            hasPromptedNotificationPermission = true
            openSheetAfterPermissionPrompt = true
            showNotificationPermissionDialog = true
            return
        }
        openDownloadSheet()
    }

    DownloadPageImplV2(
        modifier = modifier,
        taskDownloadStateMap = downloader.getTaskStateMap(),
        downloadCallback = {
            onDownloadClicked()
        },
        onMenuOpen = onMenuOpen,
    ) { task, action ->
        view.slightHapticFeedback()
        when (action) {
            UiAction.Cancel -> downloader.cancel(task)
            UiAction.Delete -> downloader.remove(task)
            UiAction.Resume -> downloader.restart(task)
            is UiAction.CopyErrorReport -> {
                context.copyToClipboard(getErrorReport(action.throwable, task.url)
                )
                context.makeToast(R.string.error_copied)
            }
            UiAction.CopyVideoURL -> {
                context.copyToClipboard(task.url)
                context.makeToast(R.string.link_copied)
            }
            is UiAction.OpenFile -> {
                action.filePath?.let {
                    FileUtil.openFile(path = it) { context.makeToast(R.string.file_unavailable) }
                }
            }
            is UiAction.OpenThumbnailURL -> {
                uriHandler.openUri(action.url)
            }
            is UiAction.OpenVideoURL -> {
                uriHandler.openUri(action.url)
            }
            is UiAction.ShareFile -> {
                val shareTitle = shareTitleText
                FileUtil.createIntentForSharingFile(action.filePath)?.let {
                    context.startActivity(Intent.createChooser(it, shareTitle))
                }
            }
        }
    }

    var preferences by remember {
        mutableStateOf(DownloadUtil.DownloadPreferences.createFromPreferences())
    }
    var dialogConfig by remember { mutableStateOf(Config()) }
    val sheetValue by dialogViewModel.sheetValueFlow.collectAsStateWithLifecycle()
    val state by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()

    val selectionState = dialogViewModel.selectionStateFlow.collectAsStateWithLifecycle().value

    var showDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // External UI-path: honor extract_audio / keep_sections / task cookies from session.
    LaunchedEffect(sheetValue) {
        if (sheetValue == DownloadDialogViewModel.SheetValue.Expanded) {
            val session = ExternalDownloadCoordinator.currentSession()
            val merged = ExternalDownloadCoordinator.buildPreferencesForSession(session = session)
            val extract = session?.extractAudio
            preferences = merged
            dialogConfig =
                Config(
                    downloadType =
                        when (extract) {
                            true -> DownloadType.Audio
                            false -> DownloadType.Video
                            null ->
                                PreferenceUtil.getDownloadType()
                                    ?: if (merged.extractAudio) DownloadType.Audio
                                    else DownloadType.Video
                        },
                    // Upstream #2585: never expose Custom Command on delegated external UI.
                    typeEntries =
                        if (session != null) {
                            DownloadType.entries - DownloadType.Command
                        } else {
                            Config().typeEntries
                        },
                )
            showDialog = true
        } else {
            launch { sheetState.hide() }.invokeOnCompletion { showDialog = false }
        }
    }

    if (showDialog) {

        DownloadDialog(
            state = state,
            sheetState = sheetState,
            config = dialogConfig,
            preferences = preferences,
            onPreferencesUpdate = { preferences = it },
            onActionPost = { dialogViewModel.postAction(it) },
        )
    }
    when (selectionState) {
        is DownloadDialogViewModel.SelectionState.FormatSelection ->
            FormatPage(
                state = selectionState,
                onDismissRequest = { dialogViewModel.postAction(Action.Reset) },
            )

        is DownloadDialogViewModel.SelectionState.PlaylistSelection -> {
            PlaylistSelectionPage(
                state = selectionState,
                onDismissRequest = { dialogViewModel.postAction(Action.Reset) },
            )
        }

        DownloadDialogViewModel.SelectionState.Idle -> {}
    }

    if (showNotificationPermissionDialog) {
        NotificationPermissionDialog(
            onDismissRequest = {
                showNotificationPermissionDialog = false
                if (openSheetAfterPermissionPrompt) {
                    openSheetAfterPermissionPrompt = false
                    openDownloadSheet()
                }
            },
            onPermissionGranted = {
                notificationPermission?.launchPermissionRequest()
                NOTIFICATION.updateBoolean(true)
                showNotificationPermissionDialog = false
                if (openSheetAfterPermissionPrompt) {
                    openSheetAfterPermissionPrompt = false
                    openDownloadSheet()
                }
            },
        )
    }
}

@Composable
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = calculateTopPadding() + other.calculateTopPadding(),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
        start =
            calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
    )
}

private const val HeaderSpacingDp = 28

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPageImplV2(
    modifier: Modifier = Modifier,
    taskDownloadStateMap: SnapshotStateMap<Task, Task.State>,
    downloadCallback: () -> Unit = {},
    onMenuOpen: (() -> Unit) = {},
    onActionPost: (Task, UiAction) -> Unit,
) {
    var activeFilter by remember { mutableStateOf(Filter.All) }
    val filteredMap by
        remember(activeFilter) {
            derivedStateOf { taskDownloadStateMap.filter { activeFilter.predict(it.toPair()) } }
        }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    val view = LocalView.current

    fun showActionSheet(task: Task) {
        view.slightHapticFeedback()
        scope.launch {
            selectedTask = task
            delay(50)
            sheetState.show()
        }
    }

    fun cancelActiveDownloads() {
        taskDownloadStateMap.forEach { (task, state) ->
            when (state.downloadState) {
                is FetchingInfo,
                Idle,
                ReadyWithInfo,
                is Running -> onActionPost(task, UiAction.Cancel)
                else -> Unit
            }
        }
    }

    fun removeFinishedDownloads() {
        taskDownloadStateMap
            .filter { (_, state) -> state.downloadState is Completed }
            .keys
            .forEach { onActionPost(it, UiAction.Delete) }
    }

    fun removeCanceledDownloads() {
        taskDownloadStateMap
            .filter { (_, state) ->
                state.downloadState is Canceled || state.downloadState is Error
            }
            .keys
            .forEach { onActionPost(it, UiAction.Delete) }
    }

    LaunchedEffect(selectedTask, taskDownloadStateMap.size) {
        if (!taskDownloadStateMap.contains(selectedTask)) {
            selectedTask = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        floatingActionButton = { FABs(modifier = Modifier, downloadCallback = downloadCallback) },
    ) { windowInsetsPadding ->
        val lazyListState = rememberLazyGridState()
        val windowWidthSizeClass = LocalWindowWidthState.current
        val spacerHeight =
            with(LocalDensity.current) {
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact) 0f
                else HeaderSpacingDp.dp.toPx()
            }
        var headerOffset by remember { mutableFloatStateOf(spacerHeight) }
        var isGridView by rememberSaveable { mutableStateOf(true) }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .then(
                        if (windowWidthSizeClass != WindowWidthSizeClass.Compact) Modifier
                        else
                            Modifier.nestedScroll(
                                connection =
                                    TopBarNestedScrollConnection(
                                        maxOffset = spacerHeight,
                                        flingAnimationSpec = rememberSplineBasedDecay(),
                                        offset = { headerOffset },
                                        onOffsetUpdate = { headerOffset = it },
                                    )
                            )
                    )
        ) {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                // Sticky header chrome: local fake-glass strip (not applied to grid items).
                Column(
                    modifier = Modifier.fillMaxWidth().glassBackground(enableBlur = false)
                ) {
                    Spacer(Modifier.height(with(LocalDensity.current) { headerOffset.toDp() }))
                    Header(onMenuOpen = onMenuOpen, modifier = Modifier.padding(horizontal = 16.dp))
                    SelectionGroupRow(
                        modifier =
                            Modifier.horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Filter.entries.forEach { filter ->
                            SelectionGroupItem(
                                colors =
                                    SelectionGroupDefaults.colors(
                                        activeContainerColor =
                                            LocalFixedColorRoles.current.tertiaryFixed,
                                        activeContentColor =
                                            LocalFixedColorRoles.current.onTertiaryFixed,
                                    ),
                                selected = activeFilter == filter,
                                onClick = {
                                    if (activeFilter == filter) {
                                        scope.launch { lazyListState.animateScrollToItem(0) }
                                        scope.launch {
                                            val initialValue = headerOffset
                                            AnimationState(initialValue = initialValue).animateTo(
                                                spacerHeight
                                            ) {
                                                headerOffset = value
                                            }
                                        }
                                    } else {
                                        activeFilter = filter
                                    }
                                },
                            ) {
                                Text(filter.label())
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (headerOffset <= 0.1f && spacerHeight > 0f) {
                        HorizontalDivider(thickness = Dp.Hairline)
                    }
                }

                LazyVerticalGrid(
                    modifier = Modifier,
                    state = lazyListState,
                    columns = GridCells.Adaptive(240.dp),
                    contentPadding =
                        windowInsetsPadding +
                            PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (filteredMap.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val videoCount =
                                filteredMap.count {
                                    !it.value.viewState.videoFormats.isNullOrEmpty()
                                }
                            SubHeader(
                                modifier = Modifier,
                                videoCount = videoCount,
                                audioCount = filteredMap.size - videoCount,
                                isGridView = isGridView,
                                onToggleView = { isGridView = !isGridView },
                                onCancelActive = ::cancelActiveDownloads,
                                onRemoveFinished = ::removeFinishedDownloads,
                                onRemoveCanceled = ::removeCanceledDownloads,
                            )
                        }
                    }

                    if (isGridView) {
                        items(
                            items =
                                filteredMap.toList().sortedBy { (_, state) -> state.downloadState },
                            key = { (task, _) -> task.id },
                        ) { (task, state) ->
                            with(state.viewState) {
                                VideoCardV2(
                                    modifier = Modifier.padding(bottom = 16.dp),
                                    viewState = this,
                                    actionButton = {
                                        ActionButton(
                                            modifier = Modifier,
                                            downloadState = state.downloadState,
                                        ) {
                                            onActionPost(task, it)
                                        }
                                    },
                                    stateIndicator = {
                                        CardStateIndicator(
                                            modifier = Modifier,
                                            downloadState = state.downloadState,
                                        )
                                    },
                                    onButtonClick = { showActionSheet(task) },
                                )
                            }
                        }
                    } else {
                        items(
                            items =
                                filteredMap.toList().sortedBy { (_, state) -> state.downloadState },
                            key = { (task, _) -> task.id },
                            span = { GridItemSpan(maxLineSpan) },
                        ) { (task, state) ->
                                VideoListItem(
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    viewState = state.viewState,
                                stateIndicator = {
                                    ListItemStateText(
                                        modifier = Modifier.padding(top = 3.dp),
                                        downloadState = state.downloadState,
                                    )
                                },
                                onButtonClick = { showActionSheet(task) },
                            )
                        }
                    }
                }
            }
        }
        if (filteredMap.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (taskDownloadStateMap.isEmpty()) {
                    DownloadQueuePlaceholder(
                        modifier =
                            Modifier.fillMaxHeight(0.4f)
                                .widthIn(max = 360.dp)
                                .align(Alignment.Center)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_downloads_in_filter),
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    if (selectedTask != null) {
        val task = selectedTask!!
        val (downloadState, _, viewState) = taskDownloadStateMap[task] ?: return
        SealModalBottomSheet(
            sheetState = sheetState,
            contentPadding = PaddingValues(),
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
            },
        ) {
            SheetContent(
                task = task,
                downloadState = downloadState,
                viewState = viewState,
                onDismissRequest = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
                },
                onActionPost = onActionPost,
            )
        }
    }
}

@Composable
fun Header(modifier: Modifier = Modifier, onMenuOpen: () -> Unit = {}) {
    val windowWidthSizeClass = LocalWindowWidthState.current
    when (windowWidthSizeClass) {
        WindowWidthSizeClass.Expanded -> {
            HeaderExpanded(modifier = modifier)
        }
        else -> {
            HeaderCompact(modifier = modifier, onMenuOpen = onMenuOpen)
        }
    }
}

@Composable
private fun HeaderCompact(modifier: Modifier = Modifier, onMenuOpen: () -> Unit) {
    Row(
        modifier = modifier.height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onMenuOpen,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.show_navigation_drawer),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stringResource(R.string.download_queue),
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeaderExpanded(modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            stringResource(R.string.download_queue),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun FABs(modifier: Modifier = Modifier, downloadCallback: () -> Unit = {}) {
    val expanded = LocalWindowWidthState.current != WindowWidthSizeClass.Compact
    Column(modifier = modifier.padding(6.dp), horizontalAlignment = Alignment.End) {
        FloatingActionButton(
            onClick = downloadCallback,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
            content = {
                if (expanded) {
                    Row(
                        modifier = Modifier.widthIn(min = 80.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.download),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = stringResource(R.string.download),
                    )
                }
            },
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
@Preview
private fun DownloadQueuePlaceholder(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        ConstraintLayout {
            val (image, text) = createRefs()
            val showImage =
                with(LocalDensity.current) {
                    this@BoxWithConstraints.constraints.maxHeight >= 240.dp.toPx()
                }
            if (showImage) {
                Image(
                    painter = rememberVectorPainter(image = DynamicColorImageVectors.download()),
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxHeight(0.5f).widthIn(max = 240.dp).constrainAs(image) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        },
                )
            } else {
                Spacer(Modifier.height(72.dp).constrainAs(image) { top.linkTo(parent.top) })
            }
            Column(
                modifier = Modifier.constrainAs(text) { top.linkTo(image.bottom, margin = 36.dp) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.you_ll_find_your_downloads_here),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.download_hint),
                    modifier = Modifier.padding(top = 8.dp).padding(horizontal = 28.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SubHeader(
    modifier: Modifier = Modifier,
    containerColor: Color =
        MaterialTheme.colorScheme.run {
            if (LocalDarkTheme.current.isDarkTheme()) surfaceContainer else surfaceContainerLowest
        },
    videoCount: Int = 0,
    audioCount: Int = 0,
    isGridView: Boolean = true,
    onToggleView: () -> Unit,
    onCancelActive: () -> Unit = {},
    onRemoveFinished: () -> Unit = {},
    onRemoveCanceled: () -> Unit = {},
) {
    val text = buildString {
        if (videoCount > 0) {
            append(pluralStringResource(R.plurals.video_count, videoCount).format(videoCount))
            if (audioCount > 0) {
                append(", ")
            }
        }
        if (audioCount > 0) {
            append(pluralStringResource(R.plurals.audio_count, audioCount).format(audioCount))
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(containerColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        FilledIconButton(
            onClick = onToggleView,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
        ) {
            Icon(
                imageVector =
                    if (isGridView) Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView,
                contentDescription =
                    stringResource(
                        if (isGridView) R.string.show_list_view else R.string.show_grid_view
                    ),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(modifier.width(4.dp))

        Box {
            FilledIconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.queue_actions),
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cancel_active_downloads)) },
                    onClick = {
                        menuExpanded = false
                        onCancelActive()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_finished_downloads)) },
                    onClick = {
                        menuExpanded = false
                        onRemoveFinished()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_canceled_downloads)) },
                    onClick = {
                        menuExpanded = false
                        onRemoveCanceled()
                    },
                )
            }
        }
    }
}

internal class DownloadPageV2Test {
    private val mockDownloader =
        object : DownloaderV2 {
            private val map = mutableStateMapOf<Task, Task.State>()

            init {
                val viewState =
                    Task.ViewState(title = "Sample title", uploader = "dummy video uploader")
                val list =
                    listOf(
                        Task.State(Idle, null, viewState),
                        Task.State(Canceled(Task.RestartableAction.Download), null, viewState),
                        Task.State(Completed(null), null, viewState),
                    )
                map.run {
                    repeat(9) {
                        put(Task(url = "$it", preferences = PreferencesMock), list[it % 3])
                    }
                }
                val scope = CoroutineScope(SupervisorJob())

                scope.launch(Dispatchers.Default) {
                    while (true) {
                        delay(1000)
                        val newEntries =
                            map.toMap().map { (task, state) ->
                                val newDownloadState =
                                    when (state.downloadState) {
                                        is Canceled -> Idle
                                        is Completed -> Idle
                                        is Error -> Idle
                                        is FetchingInfo -> ReadyWithInfo
                                        Idle -> FetchingInfo(Job(), task.id)
                                        ReadyWithInfo -> Running(Job(), task.id)
                                        is Running -> {
                                            val preState: Running = state.downloadState
                                            if (preState.progress >= 1f) Completed(null)
                                            else preState.copy(progress = preState.progress + 0.1f)
                                        }
                                    }
                                task to state.copy(downloadState = newDownloadState)
                            }
                        Snapshot.withMutableSnapshot {
                            newEntries.forEach { (task, state) ->
                                delay(100)
                                map[task] = state
                            }
                        }
                    }
                }
            }

            override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
                return map
            }

            override fun cancel(task: Task): Boolean {
                return false
            }

            override fun restart(task: Task) {}

            override fun enqueue(task: Task) {}

            override fun enqueue(task: Task, state: Task.State) {}

            override fun remove(task: Task): Boolean {
                return true
            }
        }

    @Composable
    @Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
    @Preview(name = "Tablet", device = "spec:width=600dp,height=800dp,dpi=240")
    private fun Preview() {

        val downloader: DownloaderV2 = mockDownloader
        SealTheme {
            Column() {
                DownloadPageImplV2(
                    taskDownloadStateMap = downloader.getTaskStateMap(),
                    onActionPost = { task, state -> },
                    onMenuOpen = {},
                )
            }
        }
    }
}
