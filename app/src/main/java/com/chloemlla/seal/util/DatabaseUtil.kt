package com.chloemlla.seal.util

import androidx.room.Room
import com.chloemlla.seal.App.Companion.applicationScope
import com.chloemlla.seal.App.Companion.context
import com.chloemlla.seal.database.AppDatabase
import com.chloemlla.seal.database.backup.Backup
import com.chloemlla.seal.database.backup.BackupUtil.BackupType
import com.chloemlla.seal.database.backup.BackupUtil.decodeToBackup
import com.chloemlla.seal.database.objects.CommandTemplate
import com.chloemlla.seal.database.objects.CookieProfile
import com.chloemlla.seal.database.objects.DownloadedVideoInfo
import com.chloemlla.seal.database.objects.OptionShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseUtil {
    private const val DATABASE_NAME = "app_database"
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
    private val dao = db.videoInfoDao()

    fun insertInfo(vararg infoList: DownloadedVideoInfo) {
        applicationScope.launch(Dispatchers.IO) {
            infoList.forEach { dao.insertInfoDistinctByPath(it) }
        }
    }

    init {
        applicationScope.launch {
            getTemplateFlow().collect {
                if (it.isEmpty()) PreferenceUtil.initializeTemplateSample()
            }
        }
    }

    fun getDownloadHistoryFlow() = dao.getDownloadHistoryFlow()

    private suspend fun getDownloadHistory() = dao.getDownloadHistory()

    fun getTemplateFlow() = dao.getTemplateFlow()

    fun getCookiesFlow() = dao.getCookieProfileFlow()

    fun getShortcuts() = dao.getOptionShortcuts()

    suspend fun deleteShortcut(shortcut: OptionShortcut) = dao.deleteShortcut(shortcut)

    suspend fun insertShortcut(shortcut: OptionShortcut) = dao.insertShortcut(shortcut)

    suspend fun getCookieById(id: Int) = dao.getCookieById(id)

    suspend fun deleteCookieProfile(profile: CookieProfile) = dao.deleteCookieProfile(profile)

    suspend fun insertCookieProfile(profile: CookieProfile) = dao.insertCookieProfile(profile)

    suspend fun updateCookieProfile(profile: CookieProfile) = dao.updateCookieProfile(profile)

    suspend fun getTemplateList() = dao.getTemplateList()

    suspend fun getShortcutList() = dao.getShortcutList()

    data class DeleteHistoryResult(
        val removedCount: Int,
        val fileDeleteResults: List<FileUtil.MediaDeleteResult> = emptyList(),
    ) {
        val failedFileDeletes: List<FileUtil.MediaDeleteResult>
            get() = fileDeleteResults.filter { !it.primaryDeletedOrMissing || it.failedPaths.isNotEmpty() }
    }

    suspend fun deleteInfoList(
        infoList: List<DownloadedVideoInfo>,
        deleteFile: Boolean = false,
    ): DeleteHistoryResult {
        if (infoList.isEmpty()) return DeleteHistoryResult(removedCount = 0)

        // Always remove history rows first so UI updates even if storage deletion fails.
        dao.deleteInfoList(infoList)

        val fileResults =
            if (deleteFile) {
                infoList.map { info -> FileUtil.deleteFile(info.videoPath, deleteRelated = true) }
            } else {
                emptyList()
            }

        return DeleteHistoryResult(
            removedCount = infoList.size,
            fileDeleteResults = fileResults,
        )
    }


    suspend fun getInfoById(id: Int): DownloadedVideoInfo = dao.getInfoById(id)

    suspend fun deleteInfoById(id: Int) = dao.deleteInfoById(id)

    suspend fun insertTemplate(commandTemplate: CommandTemplate) =
        dao.insertTemplate(commandTemplate)

    suspend fun updateTemplate(commandTemplate: CommandTemplate) {
        dao.updateTemplate(commandTemplate)
    }

    suspend fun importBackup(backup: Backup, types: Set<BackupType>): Int {
        var cnt = 0
        backup.run {
            if (types.contains(BackupType.DownloadHistory)) {
                val itemList = getDownloadHistory()

                if (!downloadHistory.isNullOrEmpty()) {
                    dao.insertAll(
                        com.chloemlla.seal.database.backup.BackupImportSelector
                            .selectNewHistory(itemList, downloadHistory)
                            .also { cnt += it.size }
                    )
                }
            }
            if (types.contains(BackupType.CommandTemplate)) {
                if (templates != null) {
                    val existingTemplates = getTemplateList()
                    dao.importTemplates(
                        com.chloemlla.seal.database.backup.BackupImportSelector
                            .selectNewTemplates(existingTemplates, templates)
                            .also { cnt += it.size }
                    )
                }
            }
            if (types.contains(BackupType.CommandShortcut)) {
                val existingShortcuts = getShortcutList()
                if (shortcuts != null) {
                    dao.insertAllShortcuts(
                        com.chloemlla.seal.database.backup.BackupImportSelector
                            .selectNewShortcuts(existingShortcuts, shortcuts)
                            .also { cnt += it.size }
                    )
                }
            }
        }
        return cnt
    }

    suspend fun importTemplatesFromJson(json: String): Int {
        json
            .decodeToBackup()
            .onSuccess { backup ->
                return importBackup(
                    backup = backup,
                    types = setOf(BackupType.CommandTemplate, BackupType.CommandShortcut),
                )
            }
            .onFailure { it.printStackTrace() }
        return 0
    }

    suspend fun deleteTemplateById(id: Int) = dao.deleteTemplateById(id)

    suspend fun deleteTemplates(templates: List<CommandTemplate>) = dao.deleteTemplates(templates)

    private const val TAG = "DatabaseUtil"
}
