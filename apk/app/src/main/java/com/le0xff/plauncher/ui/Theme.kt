package com.le0xff.plauncher.ui

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

enum class AppTheme {
    Light,
    Dark,
    Amoled
}

@Composable
fun getAppThemeColorScheme(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.Light -> lightColorScheme()
    AppTheme.Dark -> darkColorScheme()
    AppTheme.Amoled -> darkColorScheme().copy(
        surface = Color(AndroidColor.BLACK),
        onSurface = Color.White,
        surfaceVariant = Color(AndroidColor.BLACK),
        onSurfaceVariant = Color.White,
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = Color(0xFF1A1A1A),
        surfaceContainerHighest = Color(0xFF222222),
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainerLowest = Color(AndroidColor.BLACK),
        surfaceBright = Color(AndroidColor.BLACK),
        surfaceDim = Color(AndroidColor.BLACK),
        background = Color(AndroidColor.BLACK),
        onBackground = Color.White,
        inverseSurface = Color(AndroidColor.BLACK),
        inverseOnSurface = Color.White,
    )
}

@Composable
fun pLauncherTheme(
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
