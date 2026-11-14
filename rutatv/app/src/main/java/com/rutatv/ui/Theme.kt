package com.rutatv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Orange = Color(0xFFFF8A3D)
val Bg = Color(0xFF10141A)
val Panel = Color(0xFF1A2029)
val PanelHi = Color(0xFF242C38)
val Muted = Color(0xFF9AA4B2)

private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A0E05),
    secondary = Color(0xFF4FC3F7),
    background = Bg,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = PanelHi,
    onSurfaceVariant = Muted,
    error = Color(0xFFFF6B6B),
)

// Letra más grande de lo normal: se usa en la pantalla del carro, a distancia.
private val type = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp),
    bodyMedium = TextStyle(fontSize = 18.sp),
    bodySmall = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun RutaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
