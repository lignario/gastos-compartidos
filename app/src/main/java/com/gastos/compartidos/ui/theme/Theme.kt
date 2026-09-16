package com.gastos.compartidos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E7D5B)
private val GreenLight = Color(0xFF5BA888)
private val GreenDark = Color(0xFF1B5E40)
private val Amber = Color(0xFFF2A93B)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = GreenLight,
    tertiary = Amber,
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    secondary = Green,
    tertiary = Amber,
    surface = Color(0xFF121412),
)

@Composable
fun GastosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
