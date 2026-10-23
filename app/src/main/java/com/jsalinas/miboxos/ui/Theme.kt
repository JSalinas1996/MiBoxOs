package com.jsalinas.miboxos.ui

import androidx.compose.ui.graphics.Color
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

object Palette {
    val text = Color(0xFFF5F5F7)
    val secondary = Color(0xFFA1A1A6)
    val tileFallback = Color(0xFF2C2C2E)
    val glass = Color(0xF21C1C1E)
    val orb = listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFF0A84FF))
}

object Type {
    val hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Palette.text, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Palette.text)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Palette.text)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Palette.secondary)
    val clock = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = Palette.text)
}
