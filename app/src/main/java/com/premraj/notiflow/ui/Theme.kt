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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun NotiFlowTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors

    MaterialTheme(colorScheme = scheme, content = content)
}
