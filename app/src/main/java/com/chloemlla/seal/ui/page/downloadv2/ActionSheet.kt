package com.chloemlla.seal.ui.page.downloadv2

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.R
import com.chloemlla.seal.download.Task
import com.chloemlla.seal.download.Task.*
import com.chloemlla.seal.download.Task.DownloadState.Canceled
import com.chloemlla.seal.download.Task.DownloadState.Completed
import com.chloemlla.seal.download.Task.DownloadState.Error
import com.chloemlla.seal.download.Task.DownloadState.FetchingInfo
import com.chloemlla.seal.download.Task.DownloadState.Idle
import com.chloemlla.seal.download.Task.DownloadState.ReadyWithInfo
import com.chloemlla.seal.download.Task.DownloadState.Running
import com.chloemlla.seal.ui.common.LocalFixedColorRoles
import com.chloemlla.seal.ui.component.ActionSheetItem
import com.chloemlla.seal.ui.component.ActionSheetPrimaryButton
import com.chloemlla.seal.ui.component.glassBackground
import com.chloemlla.seal.ui.component.SealModalBottomSheet
import com.chloemlla.seal.ui.page.downloadv2.configure.PreferencesMock
import com.chloemlla.seal.ui.theme.ErrorTonalPalettes
import com.chloemlla.seal.ui.theme.SealTheme
import com.chloemlla.seal.util.Format
import com.chloemlla.seal.util.toBitrateText
import com.chloemlla.seal.util.toDurationText
import com.chloemlla.seal.util.toFileSizeText
import com.chloemlla.seal.util.toLocalizedString
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Portrait only: keep the top action strip compact by overflowing into a More menu. */
private const val PortraitVisibleActionLimit = 5

private data class ActionButtonSpec(
    val key: String,
    val text: String,
    val imageVector: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val outlineColor: Color = Color.Unspecified,
    val onClick: () -> Unit,
)

@Composable
private fun ActionPrimaryButton(spec: ActionButtonSpec, modifier: Modifier = Modifier) {
    ActionSheetPrimaryButton(
        modifier = modifier,
        containerColor = spec.containerColor,
        contentColor = spec.contentColor,
        outlineColor = spec.outlineColor,
        imageVector = spec.imageVector,
        text = spec.text,
        onClick = spec.onClick,
    )
}

@Composable
private fun buildActionButtonSpecs(
    task: Task,
    downloadState: DownloadState,
    viewState: ViewState,
    onDismissRequest: () -> Unit,
    onActionPost: (Task, UiAction) -> Unit,
): List<ActionButtonSpec> {
    val secondaryContainer = Color.Transparent
    val secondaryContent = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val fixed = LocalFixedColorRoles.current

    val specs = mutableListOf<ActionButtonSpec>()
    when (downloadState) {
        is Canceled -> {
            specs +=
                ActionButtonSpec(
                    key = "ResumeButton",
                    text = stringResource(R.string.resume),
                    imageVector = Icons.Outlined.RestartAlt,
                    containerColor = fixed.tertiaryFixed,
                    contentColor = fixed.onTertiaryFixedVariant,
                    onClick = {
                        onActionPost(task, UiAction.Resume)
                        onDismissRequest()
                    },
                )
        }
        is Completed -> {
            specs +=
                ActionButtonSpec(
                    key = "PlayButton",
                    text = stringResource(R.string.open_file),
                    imageVector = Icons.Rounded.PlayArrow,
                    containerColor = fixed.primaryFixed,
                    contentColor = fixed.onPrimaryFixedVariant,
                    onClick = {
                        onActionPost(task, UiAction.OpenFile(downloadState.filePath))
                        onDismissRequest()
                    },
                )
            specs +=
                ActionButtonSpec(
                    key = "ShareButton",
                    text = stringResource(R.string.share),
                    imageVector = Icons.Rounded.Share,
                    containerColor = fixed.secondaryFixed,
                    contentColor = fixed.onSecondaryFixedVariant,
                    onClick = { onActionPost(task, UiAction.ShareFile(downloadState.filePath)) },
                )
        }
        is Error -> {
            specs +=
                ActionButtonSpec(
                    key = "ResumeButton",
                    text = stringResource(R.string.resume),
                    imageVector = Icons.Outlined.RestartAlt,
                    containerColor = fixed.tertiaryFixed,
                    contentColor = fixed.onTertiaryFixedVariant,
                    onClick = {
                        onActionPost(task, UiAction.Resume)
                        onDismissRequest()
                    },
                )
            specs +=
                ActionButtonSpec(
                    key = "ErrorReportButton",
                    text = stringResource(R.string.copy_error_report),
                    imageVector = Icons.Outlined.ErrorOutline,
                    containerColor = ErrorTonalPalettes.accent1(80.0),
                    contentColor = ErrorTonalPalettes.accent1(10.0),
                    onClick = {
                        onActionPost(task, UiAction.CopyErrorReport(downloadState.throwable))
                    },
                )
        }
        is FetchingInfo,
        ReadyWithInfo,
        Idle,
        is Running -> {
            specs +=
                ActionButtonSpec(
                    key = "CancelButton",
                    text = stringResource(R.string.cancel),
                    imageVector = Icons.Outlined.Cancel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        onActionPost(task, UiAction.Cancel)
                        onDismissRequest()
                    },
                )
        }
    }
    if (downloadState is DownloadState.Restartable || downloadState is Completed) {
        specs +=
            ActionButtonSpec(
                key = "DeleteButton",
                text = stringResource(R.string.delete),
                imageVector = Icons.Outlined.Delete,
                containerColor = secondaryContainer,
                contentColor = secondaryContent,
                outlineColor = outline,
                onClick = {
                    onActionPost(task, UiAction.Delete)
                    onDismissRequest()
                },
            )
    }
    specs +=
        ActionButtonSpec(
            key = "CopyURLButton",
            text = stringResource(R.string.copy_link),
            imageVector = Icons.Outlined.ContentCopy,
            containerColor = secondaryContainer,
            contentColor = secondaryContent,
            outlineColor = outline,
            onClick = { onActionPost(task, UiAction.CopyVideoURL) },
        )
    specs +=
        ActionButtonSpec(
            key = "OpenVideoURLButton",
            text = stringResource(R.string.open_url),
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            containerColor = secondaryContainer,
            contentColor = secondaryContent,
            outlineColor = outline,
            onClick = { onActionPost(task, UiAction.OpenVideoURL(viewState.url)) },
        )
    if (!viewState.thumbnailUrl.isNullOrEmpty()) {
        specs +=
            ActionButtonSpec(
                key = "OpenThumbnailURLButton",
                text = stringResource(R.string.thumbnail),
                imageVector = Icons.Outlined.Image,
                containerColor = secondaryContainer,
                contentColor = secondaryContent,
                outlineColor = outline,
                onClick = { onActionPost(task, UiAction.OpenThumbnailURL(viewState.thumbnailUrl)) },
            )
    }
    return specs
}

@Composable
private fun MoreActionsButton(
    modifier: Modifier = Modifier,
    overflowActions: List<ActionButtonSpec>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ActionSheetPrimaryButton(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            outlineColor = MaterialTheme.colorScheme.outlineVariant,
            imageVector = Icons.Outlined.MoreVert,
            text = stringResource(R.string.show_more_actions),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            overflowActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.text) },
                    leadingIcon = {
                        Icon(imageVector = action.imageVector, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun Title(imageModel: Any?, title: String, author: String, downloadState: DownloadState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.height(IntrinsicSize.Min)) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(8.dp))
            ListItemStateText(downloadState = downloadState)
        }
    }
}

@Composable
fun SheetContent(
    task: Task,
    viewState: ViewState,
    downloadState: DownloadState,
    onDismissRequest: () -> Unit,
    onActionPost: (Task, UiAction) -> Unit,
) {
    LazyColumn {
        item {
            Title(
                imageModel = viewState.thumbnailUrl,
                title = viewState.title,
                author = viewState.uploader,
                downloadState = downloadState,
            )
        }

        item {
            ActionButtonsRow(
                task = task,
                downloadState = downloadState,
                viewState = viewState,
                onDismissRequest = onDismissRequest,
                onActionPost = onActionPost,
            )
        }

        item { ActionSheetInfo(task = task, viewState = viewState) }
    }
}

@Composable
private fun ActionButtonsRow(
    task: Task,
    downloadState: DownloadState,
    viewState: ViewState,
    onDismissRequest: () -> Unit,
    onActionPost: (Task, UiAction) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val actions =
        buildActionButtonSpecs(
            task = task,
            downloadState = downloadState,
            viewState = viewState,
            onDismissRequest = onDismissRequest,
            onActionPost = onActionPost,
        )

    // Portrait: at most 5 visible controls including the More button itself when needed.
    // Landscape: keep the previous horizontal-scroll full strip.
    val (visibleActions, overflowActions) =
        if (isPortrait && actions.size > PortraitVisibleActionLimit) {
            val visibleCount = (PortraitVisibleActionLimit - 1).coerceAtLeast(1)
            actions.take(visibleCount) to actions.drop(visibleCount)
        } else {
            actions to emptyList()
        }

    // Primary action strip sits on a local glass bar for premium contrast over sheet content.
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .glassBackground(enableBlur = false)
                .padding(top = 12.dp, bottom = 24.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            items(items = visibleActions, key = { it.key }) { action ->
                ActionPrimaryButton(spec = action, modifier = Modifier.animateItem())
            }
            if (overflowActions.isNotEmpty()) {
                item(key = "MoreActionsButton") {
                    MoreActionsButton(
                        modifier = Modifier.animateItem(),
                        overflowActions = overflowActions,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SheetPreview() {
    val sheetState =
        with(LocalDensity.current) {
            SheetState(
                initialValue = SheetValue.Expanded,
                skipPartiallyExpanded = true,
                velocityThreshold = { 56.dp.toPx() },
                positionalThreshold = { 125.dp.toPx() },
            )
        }

    var downloadState: DownloadState by remember { mutableStateOf(Running(Job(), "", 0.58f)) }

    val fakeStateList =
        listOf(
            Running(Job(), "", 0.58f),
            Error(throwable = Throwable(), RestartableAction.Download),
            FetchingInfo(Job(), ""),
            Canceled(RestartableAction.Download),
            ReadyWithInfo,
            Idle,
            Completed(null),
        )
    LaunchedEffect(Unit) {
        while (true) {
            fakeStateList.forEach {
                downloadState = it
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val scope = rememberCoroutineScope()
    val viewState =
        ViewState(
            title = "video title looooooooooooooooooooooooooooong title sample",
            uploader = "author loooooooooooooooooooooonggggggggggggggggg",
            videoFormats =
                listOf(
                    Format(
                        vcodec = "vp9",
                        resolution = "1280x720",
                        vbr = 129400.0,
                        fileSize = 11451400.0,
                    )
                ),
            audioOnlyFormats = listOf(Format(acodec = "mp4a", abr = 129.0, fileSize = 114514.0)),
            thumbnailUrl = "https://example.com/thumb.jpg",
            url = "https://www.example.com",
            extractorKey = "youtube",
        )

    SealTheme {
        Surface {
            SealModalBottomSheet(
                contentPadding = PaddingValues(),
                onDismissRequest = {},
                sheetState = sheetState,
            ) {
                SheetContent(
                    task = Task(url = "https://www.example.com", preferences = PreferencesMock),
                    viewState = viewState,
                    downloadState = downloadState,
                    onDismissRequest = { scope.launch { sheetState.hide() } },
                ) { _, _ ->
                }
            }
        }
    }
}

@Composable
fun ActionSheetInfo(modifier: Modifier = Modifier, task: Task, viewState: ViewState) {
    with(viewState) {
        Column(modifier = modifier) {
            HorizontalDivider()
            Text(
                stringResource(R.string.media_info),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )
            ActionSheetItem(
                text = {
                    Text(
                        task.timeCreated.toLocalizedString(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${duration.toDurationText()} · ${fileSizeApprox.toFileSizeText()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null)
                },
            )

            videoFormats?.forEachIndexed { _index, fmt ->
                val index = _index + 1
                val fileSizeText = (fmt.fileSize ?: fmt.fileSizeApprox).toFileSizeText()
                val bitRateText = fmt.vbr.toBitrateText()
                val codecText = fmt.vcodec?.substringBefore(delimiter = ".") ?: ""

                val title = "${stringResource(R.string.video)} #$index: ${fmt.formatNote}"
                val details =
                    listOf(codecText, fmt.resolution, bitRateText, fileSizeText)
                        .filterNot { it.isNullOrBlank() }
                        .joinToString(separator = " · ")

                ActionSheetItem(
                    text = {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(details, style = MaterialTheme.typography.bodySmall)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.VideoFile, contentDescription = null)
                    },
                )
            }

            val audioFormats: List<Format> = buildList {
                videoFormats?.filter { it.containsAudio() }?.let { addAll(it) }
                audioOnlyFormats?.let { addAll(it) }
            }

            audioFormats.forEachIndexed { _index, fmt ->
                val index = _index + 1
                val fileSizeText = (fmt.fileSize ?: fmt.fileSizeApprox).toFileSizeText()
                val bitRateText = fmt.abr.toBitrateText()
                val codecText = fmt.acodec?.substringBefore(delimiter = ".") ?: ""

                val title = "${stringResource(R.string.audio)} #$index: ${fmt.formatNote}"
                val details =
                    listOf(codecText, bitRateText, fileSizeText)
                        .filterNot { it.isBlank() }
                        .joinToString(separator = " · ")

                ActionSheetItem(
                    text = {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(details, style = MaterialTheme.typography.bodySmall)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.AudioFile, contentDescription = null)
                    },
                )
            }

            ActionSheetItem(
                text = {
                    Text(text = extractorKey, style = MaterialTheme.typography.titleSmall)
                    Text(text = url, style = MaterialTheme.typography.bodySmall)
                },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Link, contentDescription = null) },
            )
        }
    }
}