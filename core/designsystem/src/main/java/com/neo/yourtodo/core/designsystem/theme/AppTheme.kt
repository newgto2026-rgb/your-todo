package com.neo.yourtodo.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()
private val AppTypography = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontFamily = FontFamily.SansSerif),
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.SansSerif),
        headlineSmall = headlineSmall.copy(fontFamily = FontFamily.SansSerif),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.SansSerif),
        bodyLarge = bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = bodySmall.copy(fontFamily = FontFamily.SansSerif),
        labelMedium = labelMedium.copy(fontFamily = FontFamily.SansSerif)
    )
}

@Composable
fun YourTodoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
