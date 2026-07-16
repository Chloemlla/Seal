package com.chloemlla.seal.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSettingsTest {
    @Test
    fun indonesianLocaleUsesIsoLanguageTag() {
        val indonesian = LocaleLanguageCodeMap.keys.first { it.language == "id" }
        assertEquals("id", indonesian.language)
        assertTrue(LocaleLanguageCodeMap.containsKey(Locale.forLanguageTag("id")))
    }

    @Test
    fun canonicalAppLocaleMapsLegacyIndonesianTag() {
        assertEquals("id", Locale.forLanguageTag("in").canonicalAppLocale().language)
        assertEquals("he", Locale.forLanguageTag("iw").canonicalAppLocale().language)
    }
}
