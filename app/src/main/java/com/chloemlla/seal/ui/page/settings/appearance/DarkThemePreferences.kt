package com.chloemlla.seal.ui.page.settings.appearance

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.chloemlla.seal.R
import com.chloemlla.seal.ui.common.LocalDarkTheme
import com.chloemlla.seal.ui.component.BackButton
import com.chloemlla.seal.ui.component.PreferenceSingleChoiceItem
import com.chloemlla.seal.ui.component.PreferenceSubtitle
import com.chloemlla.seal.ui.component.PreferenceSwitchVariant
import com.chloemlla.seal.util.DarkThemePreference.Companion.FOLLOW_SYSTEM
import com.chloemlla.seal.util.DarkThemePreference.Companion.OFF
import com.chloemlla.seal.util.DarkThemePreference.Companion.ON
import com.chloemlla.seal.util.PreferenceUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkThemePreferences(onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    val darkThemePreference = LocalDarkTheme.current
    val isHighContrastModeEnabled = darkThemePreference.isHighContrastModeEnabled
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(modifier = Modifier, text = stringResource(id = R.string.dark_theme))
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(modifier = Modifier, contentPadding = it) {
                if (Build.VERSION.SDK_INT >= 29)
                    item {
                        PreferenceSingleChoiceItem(
                            text = stringResource(R.string.follow_system),
                            selected = darkThemePreference.darkThemeValue == FOLLOW_SYSTEM,
                        ) {
                            PreferenceUtil.modifyDarkThemePreference(FOLLOW_SYSTEM)
                        }
                    }
                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(R.string.on),
                        selected = darkThemePreference.darkThemeValue == ON,
                    ) {
                        PreferenceUtil.modifyDarkThemePreference(ON)
                    }
                }
                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(R.string.off),
                        selected = darkThemePreference.darkThemeValue == OFF,
                    ) {
                        PreferenceUtil.modifyDarkThemePreference(OFF)
                    }
                }
                item { PreferenceSubtitle(text = stringResource(R.string.additional_settings)) }
                item {
                    PreferenceSwitchVariant(
                        title = stringResource(R.string.high_contrast),
                        icon = Icons.Outlined.Contrast,
                        isChecked = isHighContrastModeEnabled,
                        onClick = {
                            PreferenceUtil.modifyDarkThemePreference(
                                isHighContrastModeEnabled = !isHighContrastModeEnabled
                            )
                        },
                    )
                }
            }
        },
    )
}
