package com.rutamusica.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.rutamusica.R
import androidx.compose.ui.unit.sp

val Accent = Color(0xFFE94B7A)
val Bg = Color(0xFF10141A)
val Panel = Color(0xFF1A2029)
val PanelHi = Color(0xFF242C38)
val Muted = Color(0xFF9AA4B2)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF4FC3F7),
    background = Bg,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = PanelHi,
    onSurfaceVariant = Muted,
    error = Color(0xFFFF6B6B),
)

// Fuente incluida en la app: la fuente del sistema de algunas pantallas de carro no trae
// tildes ni ñ en negrita/seminegrita (salían cuadros, p. ej. "conexi□n").
val AppFont = FontFamily(
    Font(R.font.app_regular, FontWeight.Normal),
    Font(R.font.app_bold, FontWeight.Bold),
)

private fun TextStyle.f() = copy(fontFamily = AppFont)
private val base = Typography()

// Letra más grande de lo normal: se usa en la pantalla del carro, a distancia.
private val type = Typography(
    displayLarge = base.displayLarge.f(),
    displayMedium = base.displayMedium.f(),
    displaySmall = base.displaySmall.f(),
    headlineLarge = base.headlineLarge.f(),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold).f(),
    headlineSmall = base.headlineSmall.f(),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold).f(),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold).f(),
    titleSmall = base.titleSmall.f(),
    bodyLarge = TextStyle(fontSize = 20.sp).f(),
    bodyMedium = TextStyle(fontSize = 18.sp).f(),
    bodySmall = TextStyle(fontSize = 15.sp).f(),
    labelLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold).f(),
    labelMedium = TextStyle(fontSize = 16.sp).f(),
    labelSmall = base.labelSmall.f(),
)

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
