package com.chloemlla.seal.download

import com.chloemlla.seal.database.objects.CommandTemplate
import com.chloemlla.seal.download.Task.DownloadState.Idle
import com.chloemlla.seal.download.Task.TypeInfo.CustomCommand
import com.chloemlla.seal.util.DownloadUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFactoryCustomCommandTest {
    @Test
    fun createsIdleCustomCommandWithLogState() {
        val template = CommandTemplate(id = 7, name = "Audio", template = "-x")

        val (task, state) =
            TaskFactory.createCustomCommand(
                url = "https://example.com/video",
                template = template,
                preferences = DownloadUtil.DownloadPreferences.EMPTY,
            )

        assertEquals("https://example.com/video", task.url)
        assertEquals(template, (task.type as CustomCommand).template)
        assertTrue(state.downloadState is Idle)
        assertEquals("Audio", state.viewState.title)
        assertEquals("", state.outputLog)
    }
}
