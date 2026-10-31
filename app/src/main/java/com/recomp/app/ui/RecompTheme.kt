package com.recomp.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF9A6AFF)
val Background = Color(0xFF100C1F)
val Surface = Color(0xFF241B3D)
val Secondary = Color(0xFF332749)
val TextMuted = Color(0xFFB9AFD3)
val Success = Color(0xFF4ADE80)

private val scheme = darkColorScheme(
    primary = Purple,
    onPrimary = Color(0xFF170828),
    primaryContainer = Color(0xFF392059),
    onPrimaryContainer = Color(0xFFEEE5FF),
    secondary = Color(0xFFB8A2E7),
    background = Background,
    onBackground = Color(0xFFF5F3FF),
    surface = Surface,
    onSurface = Color(0xFFF5F3FF),
    surfaceVariant = Secondary,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF66597E),
    error = Color(0xFFFF6B81)
)

@Composable
fun RecompTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
