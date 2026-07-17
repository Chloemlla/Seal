package com.chloemlla.seal.ui.page.settings.troubleshooting

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.App
import com.chloemlla.seal.R
import com.chloemlla.seal.ui.common.Route
import com.chloemlla.seal.ui.common.booleanState
import com.chloemlla.seal.ui.component.PreferenceInfo
import com.chloemlla.seal.ui.component.PreferenceItem
import com.chloemlla.seal.ui.component.PreferenceSubtitle
import com.chloemlla.seal.ui.component.PreferenceSwitch
import com.chloemlla.seal.ui.page.settings.BasePreferencePage
import com.chloemlla.seal.ui.page.settings.general.YtdlpUpdateChannelDialog
import com.chloemlla.seal.util.PreferenceUtil.getString
import com.chloemlla.seal.util.PreferenceUtil.updateBoolean
import com.chloemlla.seal.util.RESTRICT_FILENAMES
import com.chloemlla.seal.util.UpdateUtil
import com.chloemlla.seal.util.YT_DLP_VERSION
import com.chloemlla.seal.util.makeToast
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew

@Composable
fun TroubleShootingPage(
    modifier: Modifier = Modifier,
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val ytdlpUpdateText = stringResource(R.string.ytdlp_update)
    val ytDlpUpToDateText = stringResource(R.string.yt_dlp_up_to_date)
    val ytDlpUpdateFailText = stringResource(R.string.yt_dlp_update_fail)
    val scope = rememberCoroutineScope()

    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.trouble_shooting),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
            item {
                OutlinedCard(modifier = Modifier.padding(16.dp)) {
                    PreferenceInfo(
                        modifier = Modifier,
                        text = stringResource(R.string.issue_tracker_hint),
                    )
                    PreferenceItem(
                        title = stringResource(R.string.known_issues_faqs),
                        description = stringResource(R.string.known_issues_faqs_desc),
                        icon = Icons.Outlined.HelpOutline,
                        onClick = { onNavigateTo(Route.KNOWN_ISSUES) },
                    )
                    val knownIssueUrlSeal = "https://github.com/Chloemlla/Seal/issues"
                    PreferenceItem(
                        title = stringResource(R.string.issue_tracker),
                        description = null,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        onClick = { uriHandler.openUri(knownIssueUrlSeal) },
                    )

                    val knownIssueUrlYtdlp = "https://github.com/yt-dlp/yt-dlp/issues/3766"
                    PreferenceItem(
                        title = stringResource(R.string.ytdlp_issue_tracker),
                        description = null,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        onClick = { uriHandler.openUri(knownIssueUrlYtdlp) },
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
            item { PreferenceSubtitle(text = stringResource(R.string.update)) }
            item {
                var isUpdating by remember { mutableStateOf(false) }
                var showYtdlpDialog by remember { mutableStateOf(false) }

                var ytdlpVersion by remember {
                    mutableStateOf(
                        YoutubeDL.getInstance().version(context.applicationContext)
                            ?: ytdlpUpdateText
                    )
                }
                PreferenceItem(
                    title = stringResource(id = R.string.ytdlp_update_action),
                    description = ytdlpVersion,
                    leadingIcon = {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.padding(start = 8.dp, end = 16.dp)
                                        .size(24.dp)
                                        .padding(2.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                    isUpdating = true
                                    UpdateUtil.updateYtDlp()
                                    ytdlpVersion = YT_DLP_VERSION.getString()
                                }
                                .onFailure { th ->
                                    th.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        context.makeToast(
                                            ytDlpUpdateFailText
                                        )
                                    }
                                }
                                .onSuccess {
                                    withContext(Dispatchers.Main) {
                                        context.makeToast(
                                            ytDlpUpToDateText +
                                                " (${YT_DLP_VERSION.getString()})"
                                        )
                                    }
                                }
                            isUpdating = false
                        }
                    },
                    onClickLabel = stringResource(id = R.string.update),
                    trailingIcon = {
                        IconButton(onClick = { showYtdlpDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(id = R.string.open_settings),
                            )
                        }
                    },
                )
                if (showYtdlpDialog) {
                    YtdlpUpdateChannelDialog(onDismissRequest = { showYtdlpDialog = false })
                }
            }

            item { PreferenceSubtitle(text = stringResource(R.string.network)) }
            item {
                PreferenceItem(
                    title = stringResource(R.string.cookies),
                    description = stringResource(R.string.cookies_desc),
                    icon = Icons.Outlined.Cookie,
                    onClick = { onNavigateTo(Route.COOKIE_PROFILE) },
                )
            }
            item { PreferenceSubtitle(text = stringResource(R.string.download_directory)) }
            item {
                var restrictFilenames by RESTRICT_FILENAMES.booleanState
                PreferenceSwitch(
                    title = stringResource(id = R.string.restrict_filenames),
                    icon = Icons.Outlined.Spellcheck,
                    description = stringResource(id = R.string.restrict_filenames_desc),
                    isChecked = restrictFilenames,
                ) {
                    restrictFilenames = !restrictFilenames
                    RESTRICT_FILENAMES.updateBoolean(restrictFilenames)
                }
            }
        }
    }
}
