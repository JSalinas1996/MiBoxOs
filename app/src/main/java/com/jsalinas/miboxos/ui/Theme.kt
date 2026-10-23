package com.jsalinas.miboxos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jsalinas.miboxos.R

val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

/** Colores por modo. `onWallpaper` es el texto sobre el material de la grilla (nombres de apps). */
data class AppColors(
    val dark: Boolean,
    val text: Color,
    val secondary: Color,
    val onWallpaper: Color,
    val panel: Color,
    val control: Color,
    val controlFocused: Color,
    val onControlFocused: Color,
    val tile: Color,
    val accent: Color,
    /** Fondo de Ajustes (pantalla completa, como la app Ajustes de tvOS). */
    val settingsBackground: Color,
    /** Tinte sobre el fondo desenfocado detrás de la grilla. */
    val materialTint: Color,
)

fun appColors(dark: Boolean, photoWallpaper: Boolean) = if (dark) {
    AppColors(
        dark = true,
        text = Color(0xFFF5F5F7),
        secondary = Color(0xFF98989D),
        onWallpaper = Color(0xFFF5F5F7),
        panel = Color(0xFF1C1C1E),
        control = Color.White.copy(alpha = 0.10f),
        controlFocused = Color(0xFFF5F5F7),
        onControlFocused = Color(0xFF1D1D1F),
        tile = Color(0xFF2C2C2E),
        accent = Color(0xFF0A84FF),
        settingsBackground = Color(0xFF151517),
        materialTint = Color.Black.copy(alpha = 0.42f),
    )
} else {
    AppColors(
        dark = false,
        text = Color(0xFF1D1D1F),
        secondary = Color(0xFF6E6E73),
        onWallpaper = Color(0xFF1D1D1F),
        panel = Color(0xFFF5F5F7),
        control = Color.Black.copy(alpha = 0.06f),
        controlFocused = Color.White,
        onControlFocused = Color(0xFF1D1D1F),
        tile = Color(0xFFE5E5EA),
        accent = Color(0xFF007AFF),
        settingsBackground = Color(0xFFEBEBF0),
        materialTint = Color.White.copy(alpha = 0.55f),
    )
}

val LocalColors = staticCompositionLocalOf { appColors(dark = true, photoWallpaper = false) }

val OrbColors = listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFF0A84FF))

object Type {
    private val softShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 12f)

    val tileLabel: TextStyle
        @Composable get() = TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp,
            color = LocalColors.current.onWallpaper, shadow = shadowFor(LocalColors.current.onWallpaper),
        )
    /** Título grande centrado de Ajustes. */
    val largeTitle: TextStyle
        @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.3).sp, color = LocalColors.current.text)
    /** Texto de las filas de Ajustes. */
    val row: TextStyle
        @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = LocalColors.current.text)
    /** Transcripción del asistente, grande como Siri en tvOS. */
    val siri: TextStyle
        @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.2).sp, color = Color.White)
    val siriReply: TextStyle
        @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 19.sp, color = Color.White.copy(alpha = 0.72f))
    val section: TextStyle
        @Composable get() = TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp,
            color = LocalColors.current.secondary,
        )
    val title: TextStyle
        @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = LocalColors.current.text)
    val body: TextStyle
        @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = LocalColors.current.text)
    val caption: TextStyle
        @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = LocalColors.current.secondary)

    // La sombra solo ayuda al texto claro sobre fotos; sobre el fondo claro minimalista ensucia
    private fun shadowFor(color: Color) = if (color.luminanceIsHigh()) softShadow else null

    private fun Color.luminanceIsHigh() = red + green + blue > 1.5f
}
