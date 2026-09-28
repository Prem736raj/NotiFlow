package com.premraj.notiflow.theme

import com.premraj.notiflow.data.AppThemeMode
import com.premraj.notiflow.ui.ThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    @Test
    fun testAllThemeModesExist() {
        val modes = AppThemeMode.entries
        assertEquals(4, modes.size)
        assertTrue(modes.contains(AppThemeMode.SYSTEM))
        assertTrue(modes.contains(AppThemeMode.LIGHT))
        assertTrue(modes.contains(AppThemeMode.DARK))
        assertTrue(modes.contains(AppThemeMode.AMOLED))
    }

    @Test
    fun testSystemThemeResolution() {
        assertTrue(ThemeResolver.isDark(AppThemeMode.SYSTEM, systemDark = true))
        assertFalse(ThemeResolver.isDark(AppThemeMode.SYSTEM, systemDark = false))
        assertFalse(ThemeResolver.isAmoled(AppThemeMode.SYSTEM))
    }

    @Test
    fun testLightThemeResolution() {
        assertFalse(ThemeResolver.isDark(AppThemeMode.LIGHT, systemDark = true))
        assertFalse(ThemeResolver.isDark(AppThemeMode.LIGHT, systemDark = false))
        assertFalse(ThemeResolver.isAmoled(AppThemeMode.LIGHT))
    }

    @Test
    fun testDarkThemeResolution() {
        assertTrue(ThemeResolver.isDark(AppThemeMode.DARK, systemDark = false))
        assertTrue(ThemeResolver.isDark(AppThemeMode.DARK, systemDark = true))
        assertFalse(ThemeResolver.isAmoled(AppThemeMode.DARK))
    }

    @Test
    fun testAmoledThemeResolution() {
        assertTrue(ThemeResolver.isDark(AppThemeMode.AMOLED, systemDark = false))
        assertTrue(ThemeResolver.isDark(AppThemeMode.AMOLED, systemDark = true))
        assertTrue(ThemeResolver.isAmoled(AppThemeMode.AMOLED))
    }
}
