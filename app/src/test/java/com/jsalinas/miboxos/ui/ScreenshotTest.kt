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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.jsalinas.miboxos.apps.SystemTiles
import com.jsalinas.miboxos.watchnext.WatchNextItem
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.hue.HueStatus
import com.jsalinas.miboxos.wallpaper.Wallpaper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas para el README con apps ficticias (logos genéricos dibujados acá, no marcas reales).
 * Solo escriben archivos al grabar: ./gradlew recordRoborazziDebug
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

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
    private val earthFrosted by lazy {
        com.jsalinas.miboxos.wallpaper.Blur.frosted(
            BitmapFactory.decodeFile(File("src/main/assets/wallpapers/tierra_thumb.webp").absolutePath)!!
        ).asImageBitmap()
    }

    @Test fun homeDark() = shot("homeDark", dark = true)

    @Test fun homeLight() = shot("homeLight", dark = false)

    @Test fun homeEarth() = shot("homeEarth", dark = true, wallpaper = true)

    /** Al bajar de la primera fila, el Top Shelf se va y todo queda sobre el material. */
    @Test fun homeEarthScrolled() = shot("homeEarthScrolled", dark = true, wallpaper = true, pressDown = true)

    @Test fun settingsRoot() = shot("settingsRoot", dark = true, settings = SettingsPage.ROOT)

    @Test fun settingsWallpaper() = shot("settingsWallpaper", dark = true, wallpaper = true, settings = SettingsPage.WALLPAPER)

    @Test fun settingsLight() = shot("settingsLight", dark = false, settings = SettingsPage.APPEARANCE)

    @Test fun settingsScreensaver() = shot("settingsScreensaver", dark = true, settings = SettingsPage.SCREENSAVER)

    @Test fun screensaver() {
        val images = listOf(Wallpaper("saturno", "Saturno", Wallpaper.Asset("", "")))
        compose.setContent { com.jsalinas.miboxos.screensaver.Screensaver(images, { asset("saturno") }, 20) }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onRoot().captureRoboImage("../docs/preview/screensaver.png")
    }

    @Test fun controlCenter() {
        compose.setContent {
            CompositionLocalProvider(LocalColors provides appColors(true, true)) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        apps = apps.take(3) + SystemTiles.assistant() + SystemTiles.settings() + apps.drop(3),
                        wallpaper = earth, frosted = earthFrosted, interactive = false, onLaunch = {},
                        firstTileFocus = remember { FocusRequester() },
                    )
                    ControlCenter(
                        visible = true,
                        state = ControlCenterState(
                            darkMode = true, huePaired = true,
                            rooms = listOf(RoomState("Living", true), RoomState("Dormitorio", false), RoomState("Cocina", true), RoomState("Planta baja", false)),
                            scenes = listOf("Relajarse · Living", "Cine · Living", "Leer · Dormitorio", "Concentración · Cocina"),
                        ),
                        actions = ControlCenterActions(),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("../docs/preview/controlCenter.png")
    }

    @Test fun homeContinueWatching() {
        val items = listOf(
            WatchNextItem(1, "cine", "Stranger Things", "T2 E5 · Dig Dug", null, 0.55f, asset("nebulosa_carina_thumb"), 3),
            WatchNextItem(2, "videos", "Receta de pizza napolitana", "", null, 0.25f, asset("aurora_thumb"), 2),
            WatchNextItem(3, "series", "The Last of Us", "T1 E3 · Long, Long Time", null, 0.8f, asset("tierra_desde_la_luna_thumb"), 1),
            WatchNextItem(4, "series", "Planeta Tierra III", "T1 E1 · Costas", null, 0.1f, asset("luces_nocturnas_thumb"), 0),
        )
        compose.setContent {
            CompositionLocalProvider(LocalColors provides appColors(true, true)) {
                HomeScreen(
                    apps = apps.take(3) + SystemTiles.assistant() + SystemTiles.settings() + apps.drop(3),
                    wallpaper = asset("saturno"), frosted = earthFrosted, interactive = true, onLaunch = {},
                    firstTileFocus = remember { FocusRequester() },
                    continueWatching = items,
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("../docs/preview/continueWatching.png")
    }

    @Test fun settingsHue() = shot("settingsHue", dark = true, settings = SettingsPage.HUE)

    @Test fun assistantDone() = shot(
        "assistantDone", dark = true, wallpaper = true,
        state = AssistantState.Done("prendé las luces del living en cálido", "Listo, living en luz cálida."),
    )

    private fun shot(
        name: String,
        dark: Boolean,
        wallpaper: Boolean = false,
        settings: SettingsPage? = null,
        state: AssistantState = AssistantState.Idle,
        pressDown: Boolean = false,
    ) {
        val wallpapers = listOf(
            Wallpaper("minimal", "Minimal", null),
            Wallpaper("tierra", "Tierra", Wallpaper.Asset("", "")),
            Wallpaper("tierra_desde_la_luna", "Tierra desde la Luna", Wallpaper.Asset("", "")),
            Wallpaper("luna", "Luna", Wallpaper.Asset("", "")),
            Wallpaper("saturno", "Saturno", Wallpaper.Asset("", "")),
            Wallpaper("nebulosa_carina", "Nebulosa de Carina", Wallpaper.Asset("", "")),
            Wallpaper("aurora", "Aurora", Wallpaper.Asset("", "")),
            Wallpaper("luces_nocturnas", "Luces nocturnas", Wallpaper.Asset("", "")),
        )
        val thumbs = wallpapers.filter { it.source != null }.associate { it.id to asset("${it.id}_thumb") }
        val home = apps.take(3) + SystemTiles.assistant() + SystemTiles.settings() + apps.drop(3)
        compose.setContent {
            CompositionLocalProvider(LocalColors provides appColors(dark, wallpaper)) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        apps = home,
                        wallpaper = if (wallpaper) earth else null,
                        frosted = if (wallpaper) earthFrosted else null,
                        interactive = settings == null,
                        onLaunch = {},
                        firstTileFocus = remember { FocusRequester() },
                    )
                    SettingsScreen(
                        visible = settings != null,
                        page = settings ?: SettingsPage.ROOT,
                        state = SettingsState(
                            darkMode = dark,
                            wallpapers = wallpapers,
                            thumbs = thumbs,
                            selectedWallpaper = if (wallpaper) "tierra" else "minimal",
                            userFolder = "Pictures/MiBoxOs",
                            hue = HueStatus.Paired("12 luces · 4 grupos · 9 escenas"),
                            assistantReady = true,
                            model = "gemini-3.8-flash",
                            version = "0.5.0",
                            screensaverSeconds = 20,
                            screensaverOnlyMine = false,
                            hasOwnPhotos = false,
                        ),
                        actions = SettingsActions({}, {}, {}, {}, {}),
                    )
                    AssistantOverlay(state, 0f)
                }
            }
        }
        compose.waitForIdle()
        if (pressDown) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.mainClock.advanceTimeBy(1_000)
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().captureRoboImage("../docs/preview/$name.png")
    }

    private fun asset(name: String): ImageBitmap =
        BitmapFactory.decodeFile(File("src/main/assets/wallpapers/$name.webp").absolutePath)!!.asImageBitmap()

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
