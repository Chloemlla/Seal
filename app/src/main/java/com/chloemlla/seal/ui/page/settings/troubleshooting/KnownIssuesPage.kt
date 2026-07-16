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
import androidx.compose.runtime.remember
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
        remember {
            listOf(
                KnownIssue(
                    title = "Why does this app suddenly stop working?",
                    related = "Seal #1733",
                    solution =
                        "This is usually a site anti-bot change, outdated yt-dlp, or a blocked IP. " +
                            "Update yt-dlp first from Troubleshooting, then retry. " +
                            "For Instagram / Facebook / Twitter / YouTube, generate and enable cookies.",
                    links =
                        listOf(
                            "Open related discussion" to
                                "https://github.com/JunkFood02/Seal/issues"
                        ),
                ),
                KnownIssue(
                    title = "Instagram, Facebook, and Twitter downloads fail",
                    related = "Seal #733",
                    solution =
                        "Generate and enable cookies for these sites in Settings → Network → Cookies, " +
                            "then retry the download with cookies enabled.",
                ),
                KnownIssue(
                    title =
                        "[youtube] Sign in to confirm you’re not a bot / HTTP Error 403: Forbidden",
                    related = "yt-dlp #10128",
                    solution =
                        "Your current IP was likely blocked by YouTube. Change network/IP (or VPN), " +
                            "update yt-dlp, and try enabling cookies if needed.",
                    links =
                        listOf(
                            "yt-dlp discussion" to
                                "https://github.com/yt-dlp/yt-dlp/issues/10128"
                        ),
                ),
                KnownIssue(
                    title =
                        "TikTok postprocessing: Error opening input files: Invalid data found when processing input",
                    related = "Seal #1710",
                    solution =
                        "This is often IP / CDN related. Change your IP and retry, or wait for an " +
                            "upstream yt-dlp / site-side fix.",
                ),
                KnownIssue(
                    title = "Python error / Python version is not supported",
                    related = "Seal #1729",
                    solution =
                        "Update Seal to the latest release (v1.13.1 or newer) and update yt-dlp.",
                    links =
                        listOf(
                            "Latest Seal releases" to
                                "https://github.com/JunkFood02/Seal/releases/latest"
                        ),
                ),
                KnownIssue(
                    title = "Cannot link executable: library \"libandroid-support.so\" not found",
                    related = "Seal #1404",
                    solution = "Reinstall the app, or clear app data and try again.",
                ),
                KnownIssue(
                    title = "--ffmpeg-location error on OxygenOS 15 beta",
                    related = "Seal #1837 / Termux #4219",
                    solution =
                        "On some OnePlus / OPPO Android 15 builds, background native processes " +
                            "are killed by the system. Wait for a system update from the vendor.",
                    links =
                        listOf(
                            "Termux notice" to
                                "https://github.com/termux/termux-app/issues/4219"
                        ),
                ),
            )
        }

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
                            uriHandler.openUri("https://github.com/JunkFood02/Seal/issues")
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
