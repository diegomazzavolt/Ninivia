package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF285D4A), onPrimary = Color.White, primaryContainer = Color(0xFFDDEDE2), onPrimaryContainer = Color(0xFF183E2E),
    secondary = Color(0xFF526459), secondaryContainer = Color(0xFFE7EEE7),
    tertiary = Color(0xFF9A642C), background = Color(0xFFF6F7F2), surface = Color(0xFFFCFDF8),
    surfaceVariant = Color(0xFFEBEEE7), onSurfaceVariant = Color(0xFF58625B), outlineVariant = Color(0xFFD9DFD6)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA8D4B8), onPrimary = Color(0xFF103827), primaryContainer = Color(0xFF294E3C),
    secondary = Color(0xFFBDCDBF), background = Color(0xFF121A16), surface = Color(0xFF18221C),
    surfaceVariant = Color(0xFF28362D), onSurfaceVariant = Color(0xFFBDC8BD), outlineVariant = Color(0xFF3E4C42)
)
@Composable
fun MyApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
