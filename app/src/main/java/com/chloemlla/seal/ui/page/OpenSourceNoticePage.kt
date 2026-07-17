package com.chloemlla.seal.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.R
import com.chloemlla.seal.ui.component.CreditItem
import com.chloemlla.seal.ui.page.settings.about.projectCredits
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.OSS_NOTICE_DIALOG

private const val PROJECT_REPO_URL = "https://github.com/Chloemlla/Seal"
private const val PROJECT_LICENSE_URL =
    "https://github.com/Chloemlla/Seal/blob/main/LICENSE"
private const val PROJECT_LICENSE_NAME = "GNU General Public License v3.0"

@Composable
fun OpenSourceNoticePage(
    onFinished: () -> Unit,
    markCompletedOnFinish: Boolean = true,
) {
    val uriHandler = LocalUriHandler.current
    val credits = projectCredits()

    fun complete() {
        if (markCompletedOnFinish) {
            PreferenceUtil.encodeInt(OSS_NOTICE_DIALOG, 0)
        }
        onFinished()
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.oss_notice_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.oss_notice_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                NoticeSectionCard(
                    icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    title = stringResource(R.string.oss_notice_repo_title),
                    body = stringResource(R.string.oss_notice_repo_body),
                    actionLabel = PROJECT_REPO_URL,
                    onActionClick = { uriHandler.openUri(PROJECT_REPO_URL) },
                )
            }

            item {
                NoticeSectionCard(
                    icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
                    title = stringResource(R.string.oss_notice_free_title),
                    body = stringResource(R.string.oss_notice_free_body),
                )
            }

            item {
                NoticeSectionCard(
                    icon = { Icon(Icons.Outlined.Gavel, contentDescription = null) },
                    title = stringResource(R.string.oss_notice_project_license_title),
                    body =
                        stringResource(
                            R.string.oss_notice_project_license_body,
                            PROJECT_LICENSE_NAME,
                        ),
                    actionLabel = PROJECT_LICENSE_NAME,
                    onActionClick = { uriHandler.openUri(PROJECT_LICENSE_URL) },
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VolunteerActivism,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.oss_notice_credits_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.oss_notice_credits_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            items(credits) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CreditItem(
                        title = item.title,
                        author = item.author,
                        description =
                            if (item.descriptionRes != 0) {
                                stringResource(item.descriptionRes)
                            } else {
                                null
                            },
                        license = item.license,
                    ) {
                        if (item.url.isNotEmpty()) {
                            uriHandler.openUri(item.url)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        Button(
            onClick = { complete() },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.oss_notice_continue))
        }
    }
}

@Composable
private fun NoticeSectionCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                icon()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onActionClick() },
                )
            }
        }
    }
}
