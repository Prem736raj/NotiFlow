package com.premraj.notiflow.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.premraj.notiflow.data.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF365E9D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF526070),
    secondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFF705574),
    surface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFFE2E2E8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF16457F),
    secondary = Color(0xFFBAC8DB),
    secondaryContainer = Color(0xFF3B4858),
    tertiary = Color(0xFFDDBBDD),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF44474F)
)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF16457F),
    secondary = Color(0xFFBAC8DB),
    secondaryContainer = Color(0xFF3B4858),
    tertiary = Color(0xFFDDBBDD),
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A1A),
    background = Color.Black
)

object ThemeResolver {
    fun isDark(mode: AppThemeMode, systemDark: Boolean): Boolean = when (mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    fun isAmoled(mode: AppThemeMode): Boolean = mode == AppThemeMode.AMOLED

    fun shouldUseDynamicColor(mode: AppThemeMode, useDynamicColor: Boolean): Boolean =
        useDynamicColor && mode != AppThemeMode.AMOLED
}

val LocalNotiFlowDarkTheme = staticCompositionLocalOf { false }

@Composable
fun NotiFlowTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = ThemeResolver.isDark(themeMode, systemDark)

    val scheme = when {
        ThemeResolver.isAmoled(themeMode) -> AmoledColors
        ThemeResolver.shouldUseDynamicColor(themeMode, useDynamicColor) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }

    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalNotiFlowDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
