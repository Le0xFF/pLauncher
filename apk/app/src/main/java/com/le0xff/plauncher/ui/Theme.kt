package com.le0xff.plauncher.ui

/**
 * pLauncher Companion App — Theme system with Light, Dark, and Amoled variants. Provides Material3 color schemes and system UI integration.
 *
 * @author Le0xFF
 */

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AmoledSurface = Color(AndroidColor.BLACK)
private val AmoledOnSurface = Color.White
private val AmoledSurfaceContainer = Color(0xFF1A1A1A)
private val AmoledSurfaceContainerHighest = Color(0xFF222222)
private val AmoledSurfaceContainerLow = Color(0xFF0A0A0A)

enum class AppTheme {
    Light,
    Dark,
    Amoled
}

fun getAppThemeColorScheme(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.Light -> lightColorScheme()
    AppTheme.Dark -> darkColorScheme()
    AppTheme.Amoled -> darkColorScheme().copy(
        surface = AmoledSurface,
        onSurface = AmoledOnSurface,
        surfaceVariant = AmoledSurface,
        onSurfaceVariant = AmoledOnSurface,
        surfaceContainer = AmoledSurfaceContainer,
        surfaceContainerHigh = AmoledSurfaceContainer,
        surfaceContainerHighest = AmoledSurfaceContainerHighest,
        surfaceContainerLow = AmoledSurfaceContainerLow,
        surfaceContainerLowest = AmoledSurface,
        surfaceBright = AmoledSurface,
        surfaceDim = AmoledSurface,
        background = AmoledSurface,
        onBackground = AmoledOnSurface,
        inverseSurface = AmoledSurface,
        inverseOnSurface = AmoledOnSurface,
    )
}

@Composable
fun PLauncherTheme(
    theme: AppTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = getAppThemeColorScheme(theme)
    val isDark = theme != AppTheme.Light
    val view = LocalView.current

    val window = (view.context as? android.app.Activity)?.window
    if (window != null) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
        @Suppress("DEPRECATION")
        window.statusBarColor = AndroidColor.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = AndroidColor.TRANSPARENT
    }

    CompositionLocalProvider(
        LocalTonalElevationEnabled provides (theme != AppTheme.Amoled)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
