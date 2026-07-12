package com.chloemlla.seal.database.backup

import com.chloemlla.seal.database.objects.CommandTemplate
import com.chloemlla.seal.database.objects.DownloadedVideoInfo
import com.chloemlla.seal.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
