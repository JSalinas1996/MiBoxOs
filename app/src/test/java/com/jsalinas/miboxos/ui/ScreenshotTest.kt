package com.jsalinas.miboxos.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantState
import org.junit.Rule
import org.junit.Test

/** Capturas de la pantalla de inicio con apps ficticias (banners generados, sin logos reales). */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        // Paparazzi 1.3.x no lee la plataforma 36; la UI no usa APIs nuevas, así que se renderiza con la 34
        environment = detectEnvironment().copy(compileSdkVersion = 34),
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1920,
            screenHeight = 1080,
            xdpi = 320,
            ydpi = 320,
            density = Density.XHIGH,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    // Lazy: los Bitmap solo se pueden crear una vez que Paparazzi inicializó layoutlib
    private val apps by lazy {
        listOf(
        fake("Netflix", 0xFFB20710, 0xFF3A0204),
        fake("YouTube", 0xFFE62117, 0xFF5C0A07),
        fake("Prime Video", 0xFF0F79AF, 0xFF06283D),
        fake("Disney+", 0xFF1A3A8F, 0xFF0B1433),
        fake("Spotify", 0xFF1DB954, 0xFF0B3D1C),
        fake("Max", 0xFF4B2BD6, 0xFF140A45),
        fake("Plex", 0xFFE5A00D, 0xFF3D2A03),
        fake("Twitch", 0xFF9146FF, 0xFF2A1060),
        fake("Kodi", 0xFF17B2E7, 0xFF06303F),
        fake("Ajustes", 0xFF636366, 0xFF1C1C1E),
        )
    }

    @Test
    fun home() = shot(AssistantState.Idle)

    @Test
    fun assistantListening() = shot(AssistantState.Listening, level = 0.6f)

    @Test
    fun assistantDone() = shot(AssistantState.Done("abrí Netflix", "Listo, abriendo Netflix."))

    private fun shot(state: AssistantState, level: Float = 0f) {
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    apps = apps,
                    onLaunch = {},
                    onAssistant = {},
                    onSettings = {},
                    firstTileFocus = FocusRequester(),
                )
                AssistantOverlay(state, level)
            }
        }
    }

    private fun fake(name: String, top: Long, bottom: Long): TvApp {
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 320f, 180f, top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        })
        canvas.drawText(name, 160f, 102f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        val small = Bitmap.createScaledBitmap(bmp, 6, 4, true)
        return TvApp(name.lowercase(), name, bmp.asImageBitmap(), null, small.asImageBitmap())
    }
}
