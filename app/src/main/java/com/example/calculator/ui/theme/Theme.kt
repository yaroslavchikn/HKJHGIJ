package com.example.calculator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    secondary = Color(0xFFC5E8FF),
    tertiary = Color(0xFF7DDC8C),
    background = Color(0xFF0E1116),
    surface = Color(0xFF0E1116),
    surfaceVariant = Color(0xFF1D232B),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
    error = Color(0xFFF28B82)
)

@Composable
fun CalculatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
