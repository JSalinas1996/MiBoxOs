package com.jsalinas.miboxos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.hue.HueStatus
import com.jsalinas.miboxos.wallpaper.Wallpaper
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Capturas con apps ficticias: logos genéricos dibujados acá, no marcas reales. */
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
            fake("Cine", 0xFF000000, 0xFFE50914, Glyph.PLAY),
            fake("Videos", 0xFFFFFFFF, 0xFFFF0033, Glyph.PLAY),
            fake("Series", 0xFF0F171E, 0xFF00A8E1, Glyph.WORD),
            fake("Estrellas", 0xFF0B1240, 0xFFFFFFFF, Glyph.STAR),
            fake("Música", 0xFF121212, 0xFF1ED760, Glyph.WAVES),
            fake("Deportes", 0xFF002BE7, 0xFFFFFFFF, Glyph.WORD),
            fake("Media", 0xFF1F1F1F, 0xFFE5A00D, Glyph.CHEVRON),
            fake("Streams", 0xFF9146FF, 0xFFFFFFFF, Glyph.WORD),
            fake("Kodi", 0xFF17B2E7, 0xFFFFFFFF, Glyph.STAR),
            fake("Fotos", 0xFFFFFFFF, 0xFF4285F4, Glyph.WAVES),
        )
    }

    private val earth by lazy { asset("tierra") }

    @Test
    fun homeDark() = shot(dark = true)

    @Test
    fun homeLight() = shot(dark = false)

    @Test
    fun homeEarth() = shot(dark = true, wallpaper = earth, photo = true)

    @Test
    fun settingsOpen() = shot(dark = true, wallpaper = earth, photo = true, settings = true)

    @Test
    fun settingsLight() = shot(dark = false, settings = true)

    @Test
    fun assistantDone() = shot(
        dark = true, wallpaper = earth, photo = true,
        state = AssistantState.Done("prendé las luces del living en cálido", "Listo, living en luz cálida."),
    )

    private fun shot(
        dark: Boolean,
        wallpaper: ImageBitmap? = null,
        photo: Boolean = false,
        settings: Boolean = false,
        state: AssistantState = AssistantState.Idle,
    ) {
        val wallpapers = listOf(
            Wallpaper("minimal", "Minimal", null),
            Wallpaper("tierra", "Tierra", Wallpaper.Asset("", "")),
            Wallpaper("luna", "Luna", Wallpaper.Asset("", "")),
            Wallpaper("saturno", "Saturno", Wallpaper.Asset("", "")),
        )
        val thumbs = wallpapers.filter { it.source != null }.associate { it.id to asset("${it.id}_thumb") }
        paparazzi.snapshot {
            CompositionLocalProvider(LocalColors provides appColors(dark, photo)) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        apps = apps,
                        wallpaper = wallpaper,
                        interactive = !settings,
                        onLaunch = {},
                        onAssistant = {},
                        onSettings = {},
                        firstTileFocus = remember { FocusRequester() },
                    )
                    SettingsPanel(
                        visible = settings,
                        state = SettingsState(
                            darkMode = dark,
                            wallpapers = wallpapers,
                            thumbs = thumbs,
                            selectedWallpaper = if (photo) "tierra" else "minimal",
                            userFolder = "Pictures/MiBoxOs",
                            hue = HueStatus.Paired("12 luces · 4 habitaciones/zonas · 9 escenas"),
                            assistantReady = true,
                            model = "gemini-3.8-flash",
                        ),
                        actions = SettingsActions({}, {}, {}, {}),
                    )
                    AssistantOverlay(state, 0f)
                }
            }
        }
    }

    private fun asset(name: String): ImageBitmap =
        BitmapFactory.decodeFile(File("src/main/assets/wallpapers/$name.webp").absolutePath).asImageBitmap()

    private enum class Glyph { PLAY, STAR, WAVES, CHEVRON, WORD }

    private fun fake(name: String, bg: Long, fg: Long, glyph: Glyph): TvApp {
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg.toInt() }
        when (glyph) {
            Glyph.PLAY -> canvas.drawPath(Path().apply { moveTo(140f, 60f); lineTo(190f, 90f); lineTo(140f, 120f); close() }, paint)
            Glyph.STAR -> {
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 38.0 else 16.0
                    val a = Math.PI / 5 * i - Math.PI / 2
                    val x = (160 + r * Math.cos(a)).toFloat()
                    val y = (90 + r * Math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path.apply { close() }, paint)
            }
            Glyph.WAVES -> {
                canvas.drawCircle(160f, 90f, 40f, paint)
                paint.color = bg.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f
                for (i in 0..2) canvas.drawArc(128f, 62f + i * 16f, 192f, 98f + i * 16f, 200f, 140f, false, paint)
            }
            Glyph.CHEVRON -> canvas.drawPath(Path().apply {
                moveTo(140f, 55f); lineTo(170f, 55f); lineTo(195f, 90f); lineTo(170f, 125f); lineTo(140f, 125f); lineTo(165f, 90f); close()
            }, paint)
            Glyph.WORD -> canvas.drawText(name.uppercase(), 160f, 104f, paint.apply {
                textSize = 40f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
        }
        return TvApp(name.lowercase(), name, bmp.asImageBitmap())
    }
}
