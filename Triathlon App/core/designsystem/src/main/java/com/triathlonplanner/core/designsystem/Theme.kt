package com.triathlonplanner.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue40 = Color(0xFF1B5FA8)
private val BlueGrey40 = Color(0xFF4C6479)
private val Orange40 = Color(0xFFB8572C)

private val Blue80 = Color(0xFFA8CBFF)
private val BlueGrey80 = Color(0xFFB4CCE4)
private val Orange80 = Color(0xFFFFB68F)

private val LightColors = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Orange40,
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Orange80,
)

@Composable
fun TriathlonPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
