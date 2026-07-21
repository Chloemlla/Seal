package com.chloemlla.seal.ui.page.settings.interaction

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.R
import com.chloemlla.seal.ui.component.BackButton
import com.chloemlla.seal.ui.component.PreferenceItem
import com.chloemlla.seal.ui.component.PreferenceSubtitle
import com.chloemlla.seal.ui.component.PreferenceSwitch
import com.chloemlla.seal.util.DOWNLOAD_TYPE_INITIALIZATION
import com.chloemlla.seal.util.EXTERNAL_ACCEPT_COOKIES
import com.chloemlla.seal.util.EXTERNAL_AUTO_START_ENABLED
import com.chloemlla.seal.util.EXTERNAL_CALLER_WHITELIST
import com.chloemlla.seal.util.EXTERNAL_DELEGATE_ENABLED
import com.chloemlla.seal.util.EXTERNAL_WHITELIST_MODE
import com.chloemlla.seal.util.PreferenceUtil.getBoolean
import com.chloemlla.seal.util.PreferenceUtil.getInt
import com.chloemlla.seal.util.PreferenceUtil.getString
import com.chloemlla.seal.util.PreferenceUtil.updateBoolean
import com.chloemlla.seal.util.PreferenceUtil.updateInt
import com.chloemlla.seal.util.PreferenceUtil.updateString
import com.chloemlla.seal.util.USE_PREVIOUS_SELECTION

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionPreferencePage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showDownloadTypeDialog by remember { mutableStateOf(false) }
    val initialType by
        remember(showDownloadTypeDialog) {
            mutableIntStateOf(DOWNLOAD_TYPE_INITIALIZATION.getInt())
        }

    var externalDelegateEnabled by remember {
        mutableStateOf(EXTERNAL_DELEGATE_ENABLED.getBoolean())
    }
    var externalAutoStartEnabled by remember {
        mutableStateOf(EXTERNAL_AUTO_START_ENABLED.getBoolean())
    }
    var externalWhitelistMode by remember { mutableStateOf(EXTERNAL_WHITELIST_MODE.getBoolean()) }
    var externalAcceptCookies by remember {
        mutableStateOf(EXTERNAL_ACCEPT_COOKIES.getBoolean())
    }
    var whitelistText by remember { mutableStateOf(EXTERNAL_CALLER_WHITELIST.getString()) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.interface_and_interaction)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) {
        LazyColumn(modifier = Modifier, contentPadding = it) {
            item {
                PreferenceSubtitle(text = stringResource(id = R.string.settings_before_download))
            }

            item {
                PreferenceItem(
                    title = stringResource(id = R.string.download_type),
                    description =
                        when (initialType) {
                            USE_PREVIOUS_SELECTION ->
                                stringResource(id = R.string.use_previous_selection)
                            else -> stringResource(id = R.string.none)
                        },
                ) {
                    showDownloadTypeDialog = true
                }
            }

            item {
                PreferenceSubtitle(text = stringResource(id = R.string.external_download_section))
            }

            item {
                PreferenceSwitch(
                    title = stringResource(id = R.string.external_delegate_enabled),
                    description = stringResource(id = R.string.external_delegate_enabled_desc),
                    icon = Icons.Outlined.DownloadForOffline,
                    isChecked = externalDelegateEnabled,
                ) {
                    externalDelegateEnabled = !externalDelegateEnabled
                    EXTERNAL_DELEGATE_ENABLED.updateBoolean(externalDelegateEnabled)
                    if (!externalDelegateEnabled) {
                        externalAutoStartEnabled = false
                        EXTERNAL_AUTO_START_ENABLED.updateBoolean(false)
                    }
                }
            }

            item {
                PreferenceSwitch(
                    title = stringResource(id = R.string.external_auto_start_enabled),
                    description = stringResource(id = R.string.external_auto_start_enabled_desc),
                    icon = Icons.Outlined.FlashOn,
                    enabled = externalDelegateEnabled,
                    isChecked = externalAutoStartEnabled && externalDelegateEnabled,
                ) {
                    externalAutoStartEnabled = !externalAutoStartEnabled
                    EXTERNAL_AUTO_START_ENABLED.updateBoolean(externalAutoStartEnabled)
                }
            }

            item {
                PreferenceSwitch(
                    title = stringResource(id = R.string.external_accept_cookies),
                    description = stringResource(id = R.string.external_accept_cookies_desc),
                    icon = Icons.Outlined.Key,
                    enabled = externalDelegateEnabled,
                    isChecked = externalAcceptCookies && externalDelegateEnabled,
                ) {
                    externalAcceptCookies = !externalAcceptCookies
                    EXTERNAL_ACCEPT_COOKIES.updateBoolean(externalAcceptCookies)
                }
            }

            item {
                PreferenceSwitch(
                    title = stringResource(id = R.string.external_whitelist_mode),
                    description = stringResource(id = R.string.external_whitelist_mode_desc),
                    icon = Icons.Outlined.Shield,
                    enabled = externalDelegateEnabled,
                    isChecked = externalWhitelistMode && externalDelegateEnabled,
                ) {
                    externalWhitelistMode = !externalWhitelistMode
                    EXTERNAL_WHITELIST_MODE.updateBoolean(externalWhitelistMode)
                }
            }

            if (externalWhitelistMode && externalDelegateEnabled) {
                item {
                    OutlinedTextField(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        value = whitelistText,
                        onValueChange = {
                            whitelistText = it
                            EXTERNAL_CALLER_WHITELIST.updateString(it)
                        },
                        label = {
                            Text(text = stringResource(id = R.string.external_caller_whitelist))
                        },
                        supportingText = {
                            Text(text = stringResource(id = R.string.external_caller_whitelist_desc))
                        },
                        minLines = 3,
                    )
                }
            }
        }
    }

    if (showDownloadTypeDialog) {
        DownloadTypeCustomizationDialog(
            onDismissRequest = { showDownloadTypeDialog = false },
            selectedItem = initialType,
        ) {
            DOWNLOAD_TYPE_INITIALIZATION.updateInt(it)
            showDownloadTypeDialog = false
        }
    }
}
