package com.chloemlla.seal

import com.chloemlla.seal.database.backup.BackupImportSelector
import com.chloemlla.seal.database.objects.CommandTemplate
import com.chloemlla.seal.database.objects.OptionShortcut
import com.chloemlla.seal.util.CommandTemplateSanitizer
import com.chloemlla.seal.util.connectWithDelimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testTextJoin() {
        assertEquals(
            connectWithDelimiter("123", "456", "789", delimiter = ","),
            listOf(123, 456, 789).joinToString(separator = ",") { it.toString() },
        )
        assertEquals(connectWithDelimiter(delimiter = ","), "")
        assertEquals(emptyList<String>().joinToString(separator = ",") { it }, "")
    }
}

class CommandTemplateSanitizerTest {
    @Test
    fun allowsBenignTemplate() {
        val result = CommandTemplateSanitizer.validate("-f bv*+ba/b --embed-metadata")
        assertTrue(result.ok)
        assertTrue(result.blockedOptions.isEmpty())
    }

    @Test
    fun blocksExecAndNestedConfig() {
        val result =
            CommandTemplateSanitizer.validate(
                """
                --exec echo pwned
                --config-locations /sdcard/evil.conf
                """.trimIndent()
            )
        assertFalse(result.ok)
        assertTrue(result.blockedOptions.isNotEmpty())
    }
}

class BackupImportSelectorTest {
    @Test
    fun importsOnlyNewTemplates() {
        val existing = listOf(CommandTemplate(id = 1, name = "default", template = "-f b"))
        val incoming =
            listOf(
                CommandTemplate(id = 9, name = "default", template = "-f b"),
                CommandTemplate(id = 10, name = "audio", template = "-x"),
            )
        val selected = BackupImportSelector.selectNewTemplates(existing, incoming)
        assertEquals(1, selected.size)
        assertEquals("audio", selected.single().name)
        assertEquals(0, selected.single().id)
    }

    @Test
    fun importsOnlyNewShortcuts() {
        val existing = listOf(OptionShortcut(id = 1, option = "--no-mtime"))
        val incoming =
            listOf(
                OptionShortcut(id = 2, option = "--no-mtime"),
                OptionShortcut(id = 3, option = "--embed-thumbnail"),
            )
        val selected = BackupImportSelector.selectNewShortcuts(existing, incoming)
        assertEquals(listOf("--embed-thumbnail"), selected.map { it.option })
    }
}
