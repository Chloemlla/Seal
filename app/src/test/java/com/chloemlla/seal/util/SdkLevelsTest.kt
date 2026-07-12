package com.chloemlla.seal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkLevelsTest {
    @Test
    fun targetIsApi37() {
        assertEquals(37, SdkLevels.TARGET)
        assertEquals(37, SdkLevels.API_37)
        assertTrue(SdkLevels.API_37 >= SdkLevels.UPSIDE_DOWN_CAKE)
    }
}
