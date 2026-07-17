package com.chloemlla.seal.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.R
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.WELCOME_DIALOG
import kotlinx.coroutines.launch

private data class OnboardingPageContent(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
    val showUsageTips: Boolean = false,
)

@Composable
fun OnboardingPage(
    onFinished: () -> Unit,
    markCompletedOnFinish: Boolean = true,
) {
    val pages =
        listOf(
            OnboardingPageContent(
                icon = Icons.Outlined.AutoAwesome,
                titleRes = R.string.onboarding_page_welcome_title,
                bodyRes = R.string.onboarding_page_welcome_body,
            ),
            OnboardingPageContent(
                icon = Icons.Outlined.Info,
                titleRes = R.string.onboarding_page_identity_title,
                bodyRes = R.string.onboarding_page_identity_body,
            ),
            OnboardingPageContent(
                icon = Icons.Outlined.NewReleases,
                titleRes = R.string.onboarding_page_platform_title,
                bodyRes = R.string.onboarding_page_platform_body,
            ),
            OnboardingPageContent(
                icon = Icons.Outlined.Folder,
                titleRes = R.string.onboarding_page_storage_title,
                bodyRes = R.string.onboarding_page_storage_body,
            ),
            OnboardingPageContent(
                icon = Icons.Outlined.Share,
                titleRes = R.string.onboarding_page_delegate_title,
                bodyRes = R.string.onboarding_page_delegate_body,
            ),
            OnboardingPageContent(
                icon = Icons.Outlined.FileDownload,
                titleRes = R.string.onboarding_page_ytdlp_title,
                bodyRes = R.string.onboarding_page_ytdlp_body,
            ),
            OnboardingPageContent(
                icon = Icons.Outlined.Shield,
                titleRes = R.string.onboarding_page_security_title,
                bodyRes = R.string.onboarding_page_security_body,
            ),
            OnboardingPageContent(
                icon = Icons.Outlined.SettingsSuggest,
                titleRes = R.string.onboarding_page_usage_title,
                bodyRes = R.string.onboarding_page_usage_body,
                showUsageTips = true,
            ),
        )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    fun completeOnboarding() {
        if (markCompletedOnFinish) {
            PreferenceUtil.encodeInt(WELCOME_DIALOG, 0)
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isLastPage) {
                TextButton(onClick = { completeOnboarding() }) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { page ->
            OnboardingPageBody(content = pages[page])
        }

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pages.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier =
                        Modifier.size(if (active) 8.dp else 6.dp)
                            .background(
                                color =
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                ) {
                    Text(stringResource(R.string.onboarding_back))
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            if (isLastPage) {
                Button(onClick = { completeOnboarding() }) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                ) {
                    Text(stringResource(R.string.onboarding_next))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageBody(content: OnboardingPageContent) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = content.icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(content.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(content.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (content.showUsageTips) {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconDescription(
                    icon = Icons.Outlined.ContentPaste,
                    description = stringResource(R.string.paste_desc),
                )
                IconDescription(
                    icon = Icons.Outlined.FileDownload,
                    description = stringResource(R.string.download_desc),
                )
                IconDescription(
                    icon = Icons.Outlined.Subscriptions,
                    description = stringResource(R.string.download_history_desc),
                )
                IconDescription(
                    icon = Icons.Outlined.Downloading,
                    description = stringResource(R.string.battery_settings_desc),
                )
                IconDescription(
                    icon = Icons.Outlined.SettingsSuggest,
                    description = stringResource(R.string.check_download_settings_desc),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
