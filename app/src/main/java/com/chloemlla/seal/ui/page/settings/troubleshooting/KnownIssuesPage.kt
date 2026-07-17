package com.chloemlla.seal.ui.page.settings.troubleshooting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.R
import com.chloemlla.seal.ui.component.PreferenceInfo
import com.chloemlla.seal.ui.page.settings.BasePreferencePage

private data class KnownIssue(
    val title: String,
    val related: String? = null,
    val solution: String,
    val links: List<Pair<String, String>> = emptyList(),
)

@Composable
fun KnownIssuesPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val issues =
        listOf(
            KnownIssue(
                title = stringResource(R.string.known_issue_app_stop_title),
                related = "Seal #1733",
                solution = stringResource(R.string.known_issue_app_stop_solution),
                links =
                    listOf(
                        stringResource(R.string.known_issue_open_related_discussion) to
                            "https://github.com/JunkFood02/Seal/issues"
                    ),
            ),
            KnownIssue(
                title = stringResource(R.string.known_issue_social_fail_title),
                related = "Seal #733",
                solution = stringResource(R.string.known_issue_social_fail_solution),
            ),
            KnownIssue(
                title = stringResource(R.string.known_issue_youtube_bot_title),
                related = "yt-dlp #10128",
                solution = stringResource(R.string.known_issue_youtube_bot_solution),
                links =
                    listOf(
                        stringResource(R.string.known_issue_ytdlp_discussion) to
                            "https://github.com/yt-dlp/yt-dlp/issues/10128"
                    ),
            ),
            KnownIssue(
                title = stringResource(R.string.known_issue_tiktok_post_title),
                related = "Seal #1710",
                solution = stringResource(R.string.known_issue_tiktok_post_solution),
            ),
            KnownIssue(
                title = stringResource(R.string.known_issue_python_title),
                related = "Seal #1729",
                solution = stringResource(R.string.known_issue_python_solution),
                links =
                    listOf(
                        stringResource(R.string.known_issue_latest_seal_releases) to
                            "https://github.com/Chloemlla/Seal/releases/latest"
                    ),
            ),
            KnownIssue(
                title = stringResource(R.string.known_issue_libandroid_title),
                related = "Seal #1404",
                solution = stringResource(R.string.known_issue_libandroid_solution),
            ),
            KnownIssue(
                title = stringResource(R.string.known_issue_ffmpeg_oxygen_title),
                related = "Seal #1837 / Termux #4219",
                solution = stringResource(R.string.known_issue_ffmpeg_oxygen_solution),
                links =
                    listOf(
                        stringResource(R.string.known_issue_termux_notice) to
                            "https://github.com/termux/termux-app/issues/4219"
                    ),
            ),
        )

    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.known_issues_faqs),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
            item {
                OutlinedCard(modifier = Modifier.padding(16.dp)) {
                    PreferenceInfo(
                        modifier = Modifier,
                        text = stringResource(R.string.known_issues_intro),
                    )
                    TextButton(
                        onClick = {
                            uriHandler.openUri("https://github.com/Chloemlla/Seal/issues")
                        },
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.open_seal_issue_tracker))
                    }
                }
            }

            items(issues) { issue ->
                OutlinedCard(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = issue.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!issue.related.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = issue.related,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.known_issue_solution),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = issue.solution,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (issue.links.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            issue.links.forEach { (label, url) ->
                                TextButton(onClick = { uriHandler.openUri(url) }) {
                                    Text(text = label)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
