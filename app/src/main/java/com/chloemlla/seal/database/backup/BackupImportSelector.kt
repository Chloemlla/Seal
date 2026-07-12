package com.chloemlla.seal.database.backup

import com.chloemlla.seal.database.objects.CommandTemplate
import com.chloemlla.seal.database.objects.DownloadedVideoInfo
import com.chloemlla.seal.database.objects.OptionShortcut

object BackupImportSelector {
    fun selectNewHistory(
        existing: List<DownloadedVideoInfo>,
        incoming: List<DownloadedVideoInfo>,
    ): List<DownloadedVideoInfo> =
        incoming
            .filterNot { candidate ->
                existing.any {
                    it.videoUrl == candidate.videoUrl &&
                        it.videoPath == candidate.videoPath &&
                        it.videoTitle == candidate.videoTitle
                }
            }
            .map { it.copy(id = 0) }

    fun selectNewTemplates(
        existing: List<CommandTemplate>,
        incoming: List<CommandTemplate>,
    ): List<CommandTemplate> =
        incoming
            .filterNot { candidate ->
                existing.any { it.name == candidate.name && it.template == candidate.template }
            }
            .map { it.copy(id = 0) }

    fun selectNewShortcuts(
        existing: List<OptionShortcut>,
        incoming: List<OptionShortcut>,
    ): List<OptionShortcut> =
        incoming
            .filterNot { candidate -> existing.any { it.option == candidate.option } }
            .map { it.copy(id = 0) }
}
