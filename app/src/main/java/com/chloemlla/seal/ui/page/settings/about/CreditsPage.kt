package com.chloemlla.seal.ui.page.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.R
import com.chloemlla.seal.ui.component.BackButton
import com.chloemlla.seal.ui.component.CreditItem
import com.chloemlla.seal.ui.svg.DynamicColorImageVectors
import com.chloemlla.seal.ui.svg.drawablevectors.coder

data class Credit(
    val title: String = "",
    val author: String = "",
    val descriptionRes: Int = 0,
    val license: String? = null,
    val url: String = "",
)

private const val GPL_V3 = "GNU General Public License v3.0"
private const val GPL_V2 = "GNU General Public License v2.0"
private const val APACHE_V2 = "Apache License, Version 2.0"
private const val UNLICENSE = "The Unlicense"
private const val BSD = "BSD 3-Clause License"
private const val ICONS8_LICENSE = "Universal Multimedia Licensing Agreement for Icons8"

private const val youtubedlAndroidUrl = "https://github.com/yausername/youtubedl-android"
private const val ytdlpUrl = "https://github.com/yt-dlp/yt-dlp"
private const val readYou = "https://github.com/Ashinch/ReadYou"
private const val dvd = "https://github.com/yausername/dvd"
private const val icons8 = "https://icons8.com/"
private const val materialIcon = "https://fonts.google.com/icons"
private const val materialColor = "https://github.com/material-foundation/material-color-utilities"
private const val monet = "https://github.com/Kyant0/Monet"
private const val jetpack = "https://github.com/androidx/androidx"
private const val coil = "https://github.com/coil-kt/coil"
private const val mmkv = "https://github.com/Tencent/MMKV"
private const val kotlin = "https://kotlinlang.org/"
private const val okhttp = "https://github.com/square/okhttp"
private const val accompanist = "https://github.com/google/accompanist"
private const val aria2 = "https://github.com/aria2/aria2"
private const val material3 = "https://m3.material.io/"
private const val unDraw = "https://undraw.co/"
private const val materialMotionCompose = "https://github.com/fornewid/material-motion-compose"
private const val termux = "https://github.com/termux/termux-app"
private const val FFmpeg = "https://ffmpeg.org/"

fun projectCredits(): List<Credit> =
    listOf(
        Credit(
            title = "yt-dlp",
            author = "yt-dlp contributors",
            descriptionRes = R.string.credit_ytdlp_desc,
            license = UNLICENSE,
            url = ytdlpUrl,
        ),
        Credit(
            title = "Read You",
            author = "Ashinch",
            descriptionRes = R.string.credit_readyou_desc,
            license = GPL_V3,
            url = readYou,
        ),
        Credit(
            title = "youtubedl-android",
            author = "yausername",
            descriptionRes = R.string.credit_youtubedl_android_desc,
            license = GPL_V3,
            url = youtubedlAndroidUrl,
        ),
        Credit(
            title = "Termux",
            author = "Termux developers",
            descriptionRes = R.string.credit_termux_desc,
            license = GPL_V3,
            url = termux,
        ),
        Credit(
            title = "FFmpeg",
            author = "FFmpeg team",
            descriptionRes = R.string.credit_ffmpeg_desc,
            license = GPL_V2,
            url = FFmpeg,
        ),
        Credit(
            title = "Android Jetpack",
            author = "Google / AOSP",
            descriptionRes = R.string.credit_jetpack_desc,
            license = APACHE_V2,
            url = jetpack,
        ),
        Credit(
            title = "Kotlin",
            author = "JetBrains",
            descriptionRes = R.string.credit_kotlin_desc,
            license = APACHE_V2,
            url = kotlin,
        ),
        Credit(
            title = "dvd",
            author = "yausername",
            descriptionRes = R.string.credit_dvd_desc,
            license = GPL_V3,
            url = dvd,
        ),
        Credit(
            title = "Accompanist",
            author = "Google",
            descriptionRes = R.string.credit_accompanist_desc,
            license = APACHE_V2,
            url = accompanist,
        ),
        Credit(
            title = "Material Design 3",
            author = "Google",
            descriptionRes = R.string.credit_material3_desc,
            license = APACHE_V2,
            url = material3,
        ),
        Credit(
            title = "Material Icons",
            author = "Google",
            descriptionRes = R.string.credit_material_icons_desc,
            license = APACHE_V2,
            url = materialIcon,
        ),
        Credit(
            title = "Monet",
            author = "Kyant0",
            descriptionRes = R.string.credit_monet_desc,
            license = APACHE_V2,
            url = monet,
        ),
        Credit(
            title = "Material color utilities",
            author = "Material Foundation",
            descriptionRes = R.string.credit_material_color_desc,
            license = APACHE_V2,
            url = materialColor,
        ),
        Credit(
            title = "MMKV",
            author = "Tencent",
            descriptionRes = R.string.credit_mmkv_desc,
            license = BSD,
            url = mmkv,
        ),
        Credit(
            title = "Coil",
            author = "Coil contributors",
            descriptionRes = R.string.credit_coil_desc,
            license = APACHE_V2,
            url = coil,
        ),
        Credit(
            title = "aria2",
            author = "Tatsuhiro Tsujikawa",
            descriptionRes = R.string.credit_aria2_desc,
            license = GPL_V2,
            url = aria2,
        ),
        Credit(
            title = "OkHttp",
            author = "Square",
            descriptionRes = R.string.credit_okhttp_desc,
            license = APACHE_V2,
            url = okhttp,
        ),
        Credit(
            title = "material-motion-compose",
            author = "fornewid",
            descriptionRes = R.string.credit_material_motion_desc,
            license = APACHE_V2,
            url = materialMotionCompose,
        ),
        Credit(
            title = "unDraw",
            author = "Katerina Limpitsouni",
            descriptionRes = R.string.credit_undraw_desc,
            license = null,
            url = unDraw,
        ),
        Credit(
            title = "App icon by Icons8",
            author = "Icons8",
            descriptionRes = R.string.credit_icons8_desc,
            license = ICONS8_LICENSE,
            url = icons8,
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsPage(onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    val creditsList = projectCredits()
    val uriHandler = LocalUriHandler.current
    fun openUrl(url: String) {
        uriHandler.openUri(url)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.credits)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(modifier = Modifier.padding(it)) {
                item {
                    Surface(
                        modifier =
                            Modifier.fillParentMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .clip(MaterialTheme.shapes.large)
                                .clickable {}
                                .clearAndSetSemantics {},
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        val painter =
                            rememberVectorPainter(image = DynamicColorImageVectors.coder())
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 72.dp, vertical = 48.dp),
                        )
                    }
                }
                items(creditsList) { item ->
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
                            openUrl(item.url)
                        }
                    }
                }
            }
        },
    )
}
