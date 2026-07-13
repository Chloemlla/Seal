package com.chloemlla.seal.ui.page.command

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chloemlla.seal.R
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.Task.DownloadState.Canceled
import com.chloemlla.seal.download.Task.DownloadState.Error
import com.chloemlla.seal.download.Task.DownloadState.Running
import com.chloemlla.seal.download.Task.TypeInfo
import com.chloemlla.seal.ui.component.ButtonChip
import com.chloemlla.seal.util.ToastUtil
import com.chloemlla.seal.util.copyToClipboard
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskLogPage(
    onNavigateBack: () -> Unit,
    taskHashCode: Int,
    downloader: DownloaderV2 = koinInject(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val entry =
        downloader
            .getTaskStateMap()
            .entries
            .find { it.key.id.hashCode() == taskHashCode && it.key.type is TypeInfo.CustomCommand }
            ?: return
    val task = entry.key
    val state = entry.value
    val downloadState = state.downloadState
    val context = LocalContext.current
    var expandLog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.logs),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close))
                    }
                },
                actions = {},
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            val scrollState = rememberScrollState()
            LaunchedEffect(Unit) { scrollState.scrollTo(scrollState.maxValue) }
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.horizontalScroll(scrollState)) {
                    ButtonChip(
                        icon = Icons.Outlined.ContentCopy,
                        label = stringResource(id = R.string.copy_log),
                        onClick = { context.copyToClipboard(state.outputLog) },
                    )
                    if (downloadState is Error) {
                        ButtonChip(
                            icon = Icons.Outlined.ErrorOutline,
                            label = stringResource(id = R.string.copy_error_report),
                            iconColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                val err =
                                    downloadState.throwable.message
                                        ?: state.outputLog
                                context.copyToClipboard(err)
                                ToastUtil.makeToast(R.string.error_copied)
                            },
                        )
                    }
                    if (downloadState is Running) {
                        ButtonChip(
                            icon = Icons.Outlined.Cancel,
                            label = stringResource(id = R.string.cancel),
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { downloader.cancel(task) },
                        )
                    }
                    if (downloadState is Canceled || downloadState is Error) {
                        ButtonChip(
                            icon = Icons.Outlined.RestartAlt,
                            label = stringResource(id = R.string.restart),
                            onClick = { downloader.restart(task) },
                        )
                    }
                }
            }
        },
    ) { paddings ->
        Column(
            modifier =
                Modifier.padding(paddings)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
        ) {
            val templateName = (task.type as TypeInfo.CustomCommand).template.name
            Text(
                text = templateName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = task.url,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            val lastLine =
                when (downloadState) {
                    is Running -> downloadState.progressText
                    is Error ->
                        downloadState.throwable.message
                            ?: state.outputLog.lineSequence().lastOrNull().orEmpty()
                    else -> state.outputLog.lineSequence().lastOrNull().orEmpty()
                }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = lastLine,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                )
                ElevatedAssistChip(
                    onClick = { expandLog = !expandLog },
                    label = {
                        Icon(
                            imageVector = Icons.Outlined.UnfoldMore,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize).rotate(90f),
                        )
                    },
                )
            }
            if (expandLog) {
                SelectionContainer {
                    Text(
                        text = state.outputLog.ifBlank { lastLine },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }
}
